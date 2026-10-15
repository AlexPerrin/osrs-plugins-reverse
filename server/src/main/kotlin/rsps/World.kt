package rsps

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.android.AndroidUnknownPacket
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import net.rsprot.protocol.game.outgoing.info.util.getOrThrow
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.logout.Logout
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.misc.client.ServerTickEnd
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStatV2
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetAnim
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.specific.ObjAddSpecific
import net.rsprot.protocol.game.outgoing.specific.ObjDelSpecific
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.loginprot.incoming.util.AuthenticationType
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.protocol.loginprot.incoming.util.LoginClientType
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import net.rsprot.protocol.loginprot.outgoing.util.AuthenticatorResponse
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sign

class Player(
    val index: Int,
    val username: String,
    var session: Session<Player>,
    /** XTEA seed of the current login; the client presents it to reconnect. */
    var seed: IntArray,
    val infos: Infos,
    val toplevel: Int,
    var coord: Coord,
    /** The official Android app; only the packets in RSProt's osrs-241-android module can be sent. */
    val android: Boolean,
) {
    val disconnected = AtomicBoolean(false)
    /** Tick at which the connection dropped, or -1 while connected. */
    var disconnectedTick = -1L
    /** Set when the player logged out on purpose; such players are not kept for reconnecting. */
    var loggedOut = false
    val path = ArrayDeque<Coord>()
    var running = false
    var dialogueOpen = false
    val experience = IntArray(25).also { it[3] = 1154 }
    var buildCenter = coord

    val extendedInfo get() = infos.playerInfo.avatar.extendedInfo

    /** Ground objs this player can see (owner-only, sent with OBJ_ADD_SPECIFIC). */
    val groundObjs = ArrayList<GroundObj>()
    /** A pending op on an NPC, loc or obj: runs once [Interaction.reached] holds after movement. */
    var interaction: Interaction? = null

    val inventory = Inventory(INV_INVENTORY, 28)
    val worn = Inventory(INV_WORN, 14)

    fun send(message: net.rsprot.protocol.message.OutgoingGameMessage) = session.queue(message)

    fun message(text: String) = send(MessageGame(0, text))
}

class GroundObj(val obj: Int, val count: Int, val coord: Coord)

class Interaction(val reached: (Player) -> Boolean, val repath: (Player) -> Unit, val action: (Player) -> Unit)

/** An inventory without stacking rules: one obj id and count per slot. */
class Inventory(val id: Int, val capacity: Int) {
    val objs = IntArray(capacity) { -1 }
    val counts = IntArray(capacity)

    fun add(obj: Int, count: Int): Boolean {
        val slot = objs.indexOf(-1).takeIf { it >= 0 } ?: return false
        objs[slot] = obj
        counts[slot] = count
        return true
    }

    fun clear() {
        objs.fill(-1)
        counts.fill(0)
    }

    fun toMessage(): UpdateInvFull =
        UpdateInvFull(id, capacity) { slot ->
            if (objs[slot] == -1) InventoryObject.NULL else InventoryObject(objs[slot], counts[slot])
        }
}

/** A wandering, non-interactive NPC; RSProt's avatar carries everything the client sees. */
class Npc(val index: Int, val id: Int, val spawn: Coord, val avatar: NpcAvatar) {
    var x = spawn.x
    var z = spawn.z
}

const val INV_INVENTORY = 93
const val INV_WORN = 94

/**
 * A deliberately tiny game world: players can log in with any credentials, see each other,
 * walk (no collision), chat and run a few `::` commands. Everything runs on the game thread;
 * RSProt's login callbacks only enqueue work.
 */
class World(
    private val config: Config,
    private val gameframes: Map<Int, Map<Int, Int>>,
    private val crcs: IntArray,
    private val objTypes: Map<Int, ObjWear>,
    private val npcTypes: Map<Int, EntityType>,
    private val locTypes: Map<Int, EntityType>,
) : GameConnectionHandler<Player> {
    private val log = InlineLogger()
    lateinit var service: NetworkService<Player>

    private val players = arrayOfNulls<Player>(MAX_PLAYERS)
    private val npcs = ArrayList<Npc>()
    private val random = java.util.Random()
    private val pendingLogins = ConcurrentLinkedQueue<Pair<GameLoginResponseHandler<Player>, LoginBlock<AuthenticationType>>>()
    private val pendingReconnects = ConcurrentLinkedQueue<Pair<GameLoginResponseHandler<Player>, LoginBlock<XteaKey>>>()
    private var tick = 0L

    val online: Int get() = players.count { it != null }

    // -- Login (called on RSProt's login executor) --

    override fun onLogin(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<AuthenticationType>) {
        log.info {
            "Login request: user='${block.username}' client=${block.header.clientType} platform=${block.header.platformType} " +
                "sub=${block.header.subVersion} resizable=${block.resizable} ${block.width}x${block.height} " +
                "auth=${block.authentication::class.simpleName}"
        }
        if (!block.crc.validate(crcs)) {
            log.warn { "Client CRCs differ from the server cache (client is using a different cache build)" }
            if (config.validateCrcs) {
                responseHandler.writeFailedResponse(LoginResponse.OutOfDateReload)
                return
            }
        }
        pendingLogins += responseHandler to block
    }

    override fun onReconnect(responseHandler: GameLoginResponseHandler<Player>, block: LoginBlock<XteaKey>) {
        pendingReconnects += responseHandler to block
    }

    // -- Game thread --

    fun run() {
        service.setCommunicationThread(Thread.currentThread())
        for ((id, coord) in DEFAULT_NPCS) spawnNpc(id, coord)
        log.info { "Game loop started (${TICK_MS}ms ticks)" }
        while (true) {
            val start = System.nanoTime()
            try {
                cycle()
            } catch (e: Exception) {
                log.error(e) { "Tick $tick failed" }
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            Thread.sleep((TICK_MS - elapsedMs).coerceAtLeast(0))
        }
    }

    private fun cycle() {
        tick++
        processLogins()
        processReconnects()
        for (player in players) {
            player ?: continue
            if (player.disconnected.get()) {
                if (player.disconnectedTick < 0) {
                    player.disconnectedTick = tick
                    if (!player.loggedOut) log.info { "${player.username} lost connection; kept for reconnecting" }
                }
                if (player.loggedOut || tick - player.disconnectedTick > RECONNECT_GRACE_TICKS) remove(player)
                continue
            }
            try {
                player.session.processIncomingPackets(player)
            } catch (e: Exception) {
                log.warn(e) { "Packet processing failed for ${player.username}" }
            }
        }
        for (player in players) player?.let { move(it); interact(it) }
        if (!npcsFrozen) for (npc in npcs) wander(npc)
        service.infoProtocols.update()
        for (player in players) player?.let(::flush)
    }

    private fun processLogins() {
        while (true) {
            val (response, block) = pendingLogins.poll() ?: return
            val loginName = block.username.ifBlank { if (config.localAuth) config.localDisplayName else "" }
            val existing = players.firstOrNull { loginName.isNotEmpty() && it?.username.equals(loginName, ignoreCase = true) }
            if (existing != null) {
                // A fresh login takes over: drops the copy kept for reconnecting, or a session whose
                // socket has not noticed the client went away (e.g. after a client restart).
                existing.loggedOut = true
                existing.session.requestClose()
                remove(existing)
            }
            val index = (1 until MAX_PLAYERS).firstOrNull { players[it] == null }
            if (index == null) {
                response.writeFailedResponse(LoginResponse.ServerFull)
                continue
            }
            val id = block.username.lowercase().hashCode().toLong() and 0xFFFFFFFFL
            val ok = LoginResponse.Ok(
                authenticatorResponse = AuthenticatorResponse.NoAuthenticator,
                staffModLevel = 2,
                playerMod = true,
                index = index,
                member = true,
                accountHash = id,
                userId = id,
                userHash = id,
            )
            val session = response.writeSuccessfulResponse(ok, block)
            val clientType = checkNotNull(block.header.clientType.toOldSchoolClientType())
            val infos = service.infoProtocols.alloc(index, clientType)
            val android = clientType == OldSchoolClientType.ANDROID
            val toplevel = when {
                android || block.header.clientType == LoginClientType.ANDROID ||
                    block.header.clientType == LoginClientType.IOS -> Gameframe.TOPLEVEL_MOBILE
                block.resizable -> Gameframe.TOPLEVEL_STRETCH
                else -> Gameframe.TOPLEVEL_FIXED
            }
            // Token (Jagex-account / local) logins carry no username; the local character name is used.
            val name = block.username.ifBlank { if (config.localAuth) config.localDisplayName else "Player$index" }
            val player = Player(index, name, session, block.seed, infos, toplevel, config.spawn, android)
            players[index] = player
            watchDisconnect(player, session)
            onLogin(player)
            log.info { "${player.username} logged in as #$index (toplevel ${player.toplevel}); online=$online" }
        }
    }

    private fun watchDisconnect(player: Player, session: Session<Player>) {
        // Only the current session may mark the player as gone (an old one can close late).
        session.setDisconnectionHook { if (player.session === session) player.disconnected.set(true) }
    }

    private fun processReconnects() {
        while (true) {
            val (response, block) = pendingReconnects.poll() ?: return
            val player = players.firstOrNull { it != null && !it.loggedOut && it.seed.contentEquals(block.authentication.key) }
            if (player == null) {
                // Typically the server restarted since the client's last login.
                log.info { "Reconnect for an unknown session rejected" }
                response.writeFailedResponse(LoginResponse.ConnectFail)
                continue
            }
            player.infos.playerInfo.onReconnect()
            player.infos.npcInfo.onReconnect()
            player.infos.worldEntityInfo.onReconnect()
            val session = response.writeSuccessfulResponse(LoginResponse.ReconnectOk(player.infos.playerInfo), block, player.session)
            player.session = session
            player.seed = block.seed
            player.disconnectedTick = -1
            player.disconnected.set(false)
            watchDisconnect(player, session)
            val c = player.coord
            player.infos.updateRootCoord(c.level, c.x, c.z)
            player.infos.updateRootBuildAreaCenteredOnPlayer(c.x, c.z)
            log.info { "${player.username} reconnected" }
        }
    }

    private fun onLogin(player: Player) {
        val c = player.coord
        player.infos.updateRootCoord(c.level, c.x, c.z)
        player.infos.updateRootBuildAreaCenteredOnPlayer(c.x, c.z)
        player.send(RebuildLoginV2(c.zoneX, c.zoneZ, 0, player.infos.playerInfo))

        player.send(IfOpenTop(player.toplevel))
        val redirect = gameframes[player.toplevel].orEmpty()
        for ((interfaceId, stretchComponent) in Gameframe.overlays) {
            val target = if (player.toplevel == Gameframe.TOPLEVEL_STRETCH) {
                (Gameframe.TOPLEVEL_STRETCH shl 16) or stretchComponent
            } else {
                redirect[(Gameframe.TOPLEVEL_STRETCH shl 16) or stretchComponent] ?: continue
            }
            player.send(IfOpenSub(target ushr 16, target and 0xFFFF, interfaceId, 1))
        }

        for (stat in 0 until 25) {
            val level = if (stat == HITPOINTS) 10 else 1
            player.send(UpdateStatV2(stat, level, level, if (stat == HITPOINTS) 1154 else 0))
        }
        player.send(UpdateRunEnergy(10000))
        // varbit has_displayname_transmitter (varp 1737 bit 31): without it the chatbox script
        // refuses input with "You must set a name before you can chat".
        setVarp(player, VARP_DISPLAYNAME, 1 shl 31)

        for ((obj, count) in STARTER_KIT) player.inventory.add(obj, count)
        player.send(player.inventory.toMessage())
        player.send(player.worn.toMessage())
        // Enable item ops 1-10 on the 28 inventory slots (interface 149, component 0).
        player.send(IfSetEventsV2(INTERFACE_INVENTORY, 0, 0, 27, 0, IF_EVENTS_OPS))
        for (component in WORN_SLOT_COMPONENTS.keys) {
            player.send(IfSetEventsV2(INTERFACE_WORN, component, -1, -1, 0, IF_EVENTS_OPS))
        }

        player.extendedInfo.apply {
            setName(player.username)
            setCombatLevel(3)
            DEFAULT_KITS.forEachIndexed { slot, kit -> setIdentKit(slot, kit) }
            for (slot in 0 until 5) setColour(slot, 0)
            setBaseAnimationSet(808, 823, 819, 820, 821, 822, 824)
        }
        player.message("Welcome to the test server (rev ${Config.REVISION}). Commands: ::tele x z [level], ::pos, ::anim id, ::home, ::players, ::item id [n], ::empty, ::wear id [slot], ::xp skill n, ::talk [npc] text, ::obj id [n], ::npc id, ::npcsay text, ::npcanim id, ::npcform id, ::drop")
    }

    fun spawnNpc(id: Int, coord: Coord): Npc? {
        if (npcs.size >= MAX_NPCS) return null
        val index = npcs.size + 1
        val avatar = service.npcAvatarFactory.alloc(index, id, coord.level, coord.x, coord.z, direction = random.nextInt(8))
        return Npc(index, id, coord, avatar).also { npcs += it }
    }

    /** A random step now and then, staying within 3 tiles of the spawn point. */
    private fun wander(npc: Npc) {
        if (random.nextInt(8) != 0) return
        val dx = random.nextInt(3) - 1
        val dz = random.nextInt(3) - 1
        if (dx == 0 && dz == 0) return
        if (abs(npc.x + dx - npc.spawn.x) > 3 || abs(npc.z + dz - npc.spawn.z) > 3) return
        npc.x += dx
        npc.z += dz
        npc.avatar.walk(dx, dz)
    }

    fun setVarp(player: Player, id: Int, value: Int) {
        player.send(if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) VarpSmall(id, value) else VarpLarge(id, value))
    }

    private fun move(player: Player) {
        val steps = if (player.running) 2 else 1
        var moved = 0
        while (moved < steps) {
            val next = player.path.firstOrNull() ?: break
            val c = player.coord
            if (c.x == next.x && c.z == next.z) {
                player.path.removeFirst()
                continue
            }
            player.coord = Coord(c.x + (next.x - c.x).sign, c.z + (next.z - c.z).sign, c.level)
            moved++
        }
        if (moved == 0) return
        val c = player.coord
        player.infos.updateRootCoord(c.level, c.x, c.z)
        val center = player.buildCenter
        if (abs(c.zoneX - center.zoneX) >= REBUILD_DISTANCE || abs(c.zoneZ - center.zoneZ) >= REBUILD_DISTANCE) {
            rebuild(player)
        }
    }

    private fun rebuild(player: Player) {
        val c = player.coord
        player.buildCenter = c
        player.infos.updateRootBuildAreaCenteredOnPlayer(c.x, c.z)
        player.send(RebuildNormalV2(c.zoneX, c.zoneZ, 0))
    }

    private fun flush(player: Player) {
        if (player.disconnected.get()) {
            discardInfoPackets(player)
            return
        }
        try {
            val packets = player.infos.getPackets()
            val root = packets.rootWorldInfoPackets
            player.send(root.activeWorld)
            player.send(root.npcUpdateOrigin)
            val worldEntityInfo = root.worldEntityInfo.getOrThrow()
            // WORLDENTITY_INFO has not been identified in the Android protocol; without world
            // entities nothing is lost by leaving it out.
            if (player.android) {
                worldEntityInfo.consume()
                worldEntityInfo.release()
            } else {
                player.send(worldEntityInfo)
            }
            player.send(root.playerInfo.getOrThrow())
            if (!root.npcInfo.isEmpty()) {
                player.send(root.npcInfo.getOrThrow())
            } else {
                root.npcInfo.safeReleaseOrThrow()
            }
            player.send(root.activeWorld)
            // Lets the client count server ticks (Android: Client+0x3058).
            player.send(ServerTickEnd)
        } catch (e: Exception) {
            log.error(e) { "Info update failed for ${player.username}; disconnecting" }
            player.session.requestClose()
        }
        player.session.flush()
    }

    /** Info packets are computed for every allocated player; consume them while one is away. */
    private fun discardInfoPackets(player: Player) {
        try {
            val root = player.infos.getPackets().rootWorldInfoPackets
            root.worldEntityInfo.getOrThrow().run { consume(); release() }
            root.playerInfo.getOrThrow().run { consume(); release() }
            root.npcInfo.safeReleaseOrThrow()
        } catch (e: Exception) {
            log.warn(e) { "Could not discard info packets for ${player.username}" }
        }
    }

    private fun remove(player: Player) {
        players[player.index] = null
        service.infoProtocols.dealloc(player.infos)
        log.info { "${player.username} disconnected; online=$online" }
    }

    // -- Incoming messages (dispatched from processIncomingPackets on the game thread) --

    fun walkTo(player: Player, x: Int, z: Int, ctrlRun: Boolean) {
        player.interaction = null
        player.path.clear()
        player.path += Coord(x, z, player.coord.level)
        player.running = ctrlRun
    }

    fun teleport(player: Player, to: Coord) {
        player.interaction = null
        player.path.clear()
        player.coord = to
        player.infos.updateRootCoord(to.level, to.x, to.z)
        player.extendedInfo.setTempMoveSpeed(MOVESPEED_TELEPORT)
        rebuild(player)
    }

    fun command(player: Player, line: String) {
        log.info { "${player.username} command: ::$line" }
        val args = line.trim().split(Regex("\\s+"))
        when (args[0].lowercase()) {
            "tele" -> {
                val x = args.getOrNull(1)?.toIntOrNull()
                val z = args.getOrNull(2)?.toIntOrNull()
                if (x == null || z == null) return player.message("Usage: ::tele x z [level]")
                teleport(player, Coord(x, z, args.getOrNull(3)?.toIntOrNull() ?: player.coord.level))
            }
            "home" -> teleport(player, config.spawn)
            "pos" -> player.message("Position: ${player.coord.x}, ${player.coord.z}, ${player.coord.level}")
            "anim" -> player.extendedInfo.setSequence(args.getOrNull(1)?.toIntOrNull() ?: -1, 0)
            "players" -> player.message("Online: ${players.filterNotNull().joinToString { it.username }}")
            "item" -> {
                val obj = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::item id [count]")
                if (!player.inventory.add(obj, args.getOrNull(2)?.toIntOrNull() ?: 1)) return player.message("Inventory full")
                player.send(player.inventory.toMessage())
            }
            "empty" -> {
                player.inventory.clear()
                player.send(player.inventory.toMessage())
            }
            "wear" -> {
                // The slot defaults to the obj's wearpos (0 head, 1 cape, 2 neck, 3 weapon, 4 body,
                // 5 shield, 7 legs, 9 hands, 10 feet, 12 ring, 13 ammo).
                val obj = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::wear id [slot]")
                val wear = objTypes[obj]?.takeIf { it.wearable }
                val slot = args.getOrNull(2)?.toIntOrNull() ?: wear?.wearpos
                if (slot == null || slot !in 0 until 14) return player.message("Obj $obj has no wear slot; give one")
                setWorn(player, slot, obj, 1)
                player.send(player.worn.toMessage())
            }
            "npc" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::npc id")
                val npc = spawnNpc(id, player.coord) ?: return player.message("Too many NPCs")
                player.message("Spawned npc $id as #${npc.index}")
            }
            "xp" -> {
                // ::xp skill amount - adds experience (skill ids as in the stats tab order: 0 attack ... 3 hitpoints ...).
                val stat = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0 until 25 }
                val amount = args.getOrNull(2)?.toIntOrNull()
                if (stat == null || amount == null) return player.message("Usage: ::xp skill amount")
                player.experience[stat] = (player.experience[stat] + amount).coerceIn(0, 200_000_000)
                val level = levelForXp(player.experience[stat])
                player.send(UpdateStatV2(stat, level, level, player.experience[stat]))
                player.message("Skill $stat: level $level, ${player.experience[stat]} xp")
            }
            "talk" -> {
                // ::talk [npcId] text - opens an NPC chat dialogue (chat_left) in the chatbox.
                val npcId = args.getOrNull(1)?.toIntOrNull()
                val text = line.trim().split(Regex("\\s+"), limit = if (npcId != null) 3 else 2).getOrNull(if (npcId != null) 2 else 1)
                openNpcChat(player, npcId ?: DEFAULT_NPCS.first().first, "Hans", text ?: "Hello there! Welcome to the test server.")
            }
            "obj" -> {
                val obj = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::obj id [count]")
                // ::obj id [count] [dz] - on the player's tile by default (long-press yourself to see Take).
                spawnGroundObj(player, obj, args.getOrNull(2)?.toIntOrNull() ?: 1, dz = args.getOrNull(3)?.toIntOrNull() ?: 0)
            }
            "freeze" -> {
                npcsFrozen = !npcsFrozen
                player.message("NPC wandering ${if (npcsFrozen) "off" else "on"}")
            }
            "npcsay" -> {
                val text = line.trim().substringAfter(' ', "").ifBlank { return player.message("Usage: ::npcsay text") }
                for (npc in npcs) npc.avatar.extendedInfo.setSay(text)
            }
            "npcanim" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::npcanim id (-1 to stop)")
                for (npc in npcs) npc.avatar.extendedInfo.setSequence(id, 0)
            }
            "npcform" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::npcform id")
                for (npc in npcs) npc.avatar.extendedInfo.setTransmogrification(id)
            }
            // Closes the connection without logging out, to exercise the client's reconnect.
            "drop" -> player.session.requestClose()
            else -> player.message("Unknown command: ${args[0]}")
        }
    }

    private val seenUnknownOpcodes = HashSet<Int>()
    /** ::freeze stops NPC wandering (makes NPCs easy to tap while testing). */
    private var npcsFrozen = false

    fun unknownAndroidPacket(player: Player, packet: AndroidUnknownPacket) {
        // 93 (NO_TIMEOUT, every ~600 ms while idle) would flood the log.
        if (seenUnknownOpcodes.add(packet.opcode) || (config.logAndroidPackets && packet.opcode !in QUIET_OPCODES)) {
            log.info { "Unidentified Android packet from ${player.username}: $packet" }
        } else {
            log.debug { "<- ${player.username}: $packet" }
        }
    }

    /** Handles inventory and equipment buttons; returns false for anything else. */
    fun button(player: Player, interfaceId: Int, componentId: Int, sub: Int, obj: Int, op: Int): Boolean {
        when {
            interfaceId == INTERFACE_INVENTORY -> inventoryOp(player, sub, op)
            interfaceId == INTERFACE_WORN && componentId in WORN_SLOT_COMPONENTS -> unequip(player, WORN_SLOT_COMPONENTS.getValue(componentId))
            else -> return false
        }
        return true
    }

    /** Inventory item ops arrive as op = option index + 2 (option 1 "Wield" is op 3). */
    private fun inventoryOp(player: Player, slot: Int, op: Int) {
        val inv = player.inventory
        val obj = inv.objs.getOrNull(slot)?.takeIf { it != -1 } ?: return
        val type = objTypes[obj]
        when (type?.iops?.getOrNull(op - 2)?.lowercase()) {
            "wield", "wear", "equip" -> equip(player, slot)
            "drop" -> {
                spawnGroundObj(player, obj, inv.counts[slot])
                inv.objs[slot] = -1
                inv.counts[slot] = 0
                player.send(inv.toMessage())
            }
            "eat", "drink" -> {
                inv.objs[slot] = -1
                inv.counts[slot] = 0
                player.send(inv.toMessage())
                player.extendedInfo.setSequence(ANIM_EAT, 0)
                player.message("You ${type?.iops?.get(op - 2)?.lowercase()} the ${type?.name}.")
            }
            else -> player.message("Nothing interesting happens.")
        }
    }

    private fun levelForXp(xp: Int): Int {
        var points = 0.0
        for (level in 1 until 99) {
            points += Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0))
            if (Math.floor(points / 4) > xp) return level
        }
        return 99
    }

    fun openNpcChat(player: Player, npcId: Int, name: String, text: String) {
        player.send(RunClientScript(SCRIPT_CHATBOX_RESET_BACKGROUND, emptyList()))
        player.send(IfOpenSub(CHATBOX_INTERFACE, CHATBOX_MODAL, CHAT_LEFT, 0))
        player.send(IfSetNpcHead(CHAT_LEFT, CHAT_LEFT_HEAD, npcId))
        player.send(IfSetAnim(CHAT_LEFT, CHAT_LEFT_HEAD, ANIM_CHAT_NEUTRAL))
        player.send(IfSetText(CHAT_LEFT, CHAT_LEFT_NAME, name))
        player.send(IfSetText(CHAT_LEFT, CHAT_LEFT_TEXT, text))
        player.send(IfSetEventsV2(CHAT_LEFT, CHAT_LEFT_CONTINUE, -1, -1, IF_EVENT_PAUSE, 0))
        player.send(IfSetText(CHAT_LEFT, CHAT_LEFT_CONTINUE, "Click here to continue"))
        player.dialogueOpen = true
    }

    fun closeDialogue(player: Player) {
        if (!player.dialogueOpen) return
        player.dialogueOpen = false
        player.send(IfCloseSub(CHATBOX_INTERFACE, CHATBOX_MODAL))
        player.send(RunClientScript(SCRIPT_CHAT_DEFAULT_RESTORE_INPUT, emptyList()))
    }

    /** Shows an obj on the ground under the player (to that player only; no pickup yet). */
    private fun spawnGroundObj(player: Player, obj: Int, count: Int, dz: Int = 0) {
        val c = player.coord
        player.groundObjs += GroundObj(obj, count, Coord(c.x, c.z + dz, c.level))
        player.send(ObjAddSpecific(obj, count, c.level, c.x, c.z + dz, OBJ_OPS_ALL, 0, OBJ_DESPAWN_TICKS, 0, false))
    }

    // -- Interactions (OPNPC / OPLOC / OPOBJ). No pathfinding: the player walks in a straight line. --

    private fun interact(player: Player) {
        val interaction = player.interaction ?: return
        if (interaction.reached(player)) {
            player.interaction = null
            player.path.clear()
            interaction.action(player)
        } else if (player.path.isEmpty()) {
            interaction.repath(player)
        }
    }

    private fun startInteraction(player: Player, target: Coord, reached: (Player) -> Boolean, action: (Player) -> Unit) {
        player.path.clear()
        player.path += target
        val interaction = Interaction(reached, { it.path += target }, action)
        player.interaction = interaction
        // Already in reach: act this tick instead of waiting for movement.
        interact(player)
    }

    fun opNpc(player: Player, index: Int, op: Int) {
        val npc = npcs.firstOrNull { it.index == index } ?: return log.info { "OPNPC$op on unknown npc #$index" }
        val type = npcTypes[npc.id]
        val opName = type?.op(op)
        log.info { "${player.username} OPNPC$op #$index (${npc.id} ${type?.name}) '$opName'" }
        player.path.clear()
        player.path += Coord(npc.x, npc.z, player.coord.level)
        val interaction = Interaction(
            reached = { abs(it.coord.x - npc.x) <= 1 && abs(it.coord.z - npc.z) <= 1 },
            repath = { it.path += Coord(npc.x, npc.z, it.coord.level) },
            action = { p ->
                when (opName?.lowercase()) {
                    "talk-to" -> openNpcChat(p, npc.id, type?.name ?: "", "Hello, ${p.username}! This is a test server.")
                    else -> p.message("$opName ${type?.name ?: "npc ${npc.id}"}: nothing interesting happens.")
                }
            },
        )
        player.interaction = interaction
        interact(player)
    }

    fun opLoc(player: Player, id: Int, x: Int, z: Int, op: Int) {
        val type = locTypes[id]
        val opName = type?.op(op)
        log.info { "${player.username} OPLOC$op $id (${type?.name}) at $x,$z '$opName'" }
        val sx = type?.sizeX ?: 1
        val sz = type?.sizeZ ?: 1
        startInteraction(
            player,
            Coord(x, z, player.coord.level),
            reached = { it.coord.x in x - 1..x + sx && it.coord.z in z - 1..z + sz },
            action = { it.message("$opName ${type?.name ?: "loc $id"} ($id at $x, $z): nothing interesting happens.") },
        )
    }

    fun opObj(player: Player, id: Int, x: Int, z: Int, op: Int) {
        val type = objTypes[id]
        val opName = type?.ops?.getOrNull(op - 1)
        log.info { "${player.username} OPOBJ$op $id (${type?.name}) at $x,$z '$opName'" }
        startInteraction(
            player,
            Coord(x, z, player.coord.level),
            reached = { it.coord.x == x && it.coord.z == z },
            action = { p ->
                val ground = p.groundObjs.firstOrNull { it.obj == id && it.coord.x == x && it.coord.z == z }
                when {
                    ground == null -> p.message("Too late - it's gone!")
                    opName.equals("take", true) -> {
                        if (!p.inventory.add(id, ground.count)) return@startInteraction p.message("You don't have enough inventory space.")
                        p.groundObjs.remove(ground)
                        p.send(ObjDelSpecific(id, ground.count, ground.coord.level, x, z))
                        p.send(p.inventory.toMessage())
                    }
                    else -> p.message("$opName ${type?.name}: nothing interesting happens.")
                }
            },
        )
    }

    private fun equip(player: Player, slot: Int) {
        val inv = player.inventory
        val obj = inv.objs.getOrNull(slot)?.takeIf { it != -1 } ?: return
        val wear = objTypes[obj]?.takeIf { it.wearable } ?: return player.message("You can't wear that.")
        val count = inv.counts[slot]
        val previous = player.worn.objs[wear.wearpos]
        val previousCount = player.worn.counts[wear.wearpos]
        inv.objs[slot] = previous
        inv.counts[slot] = if (previous == -1) 0 else previousCount
        setWorn(player, wear.wearpos, obj, count)
        player.send(inv.toMessage())
        player.send(player.worn.toMessage())
    }

    private fun unequip(player: Player, wearpos: Int) {
        val obj = player.worn.objs[wearpos]
        if (obj == -1) return
        if (!player.inventory.add(obj, player.worn.counts[wearpos])) return player.message("Your inventory is full.")
        setWorn(player, wearpos, -1, 0)
        player.send(player.inventory.toMessage())
        player.send(player.worn.toMessage())
    }

    private fun setWorn(player: Player, wearpos: Int, obj: Int, count: Int) {
        player.worn.objs[wearpos] = obj
        player.worn.counts[wearpos] = count
        if (wearpos < 12) {
            val wear = objTypes[obj]
            player.extendedInfo.setWornObj(wearpos, obj, wear?.wearpos2 ?: -1, wear?.wearpos3 ?: -1)
        }
    }

    fun logout(player: Player) {
        player.loggedOut = true
        player.send(Logout)
        player.session.flush()
        player.session.requestClose()
    }

    companion object {
        const val MAX_PLAYERS = 2048
        private val QUIET_OPCODES = setOf(93)
        const val MAX_NPCS = 1000

        /** Hans, a man, a woman and a chicken around the castle courtyard. */
        private val DEFAULT_NPCS = listOf(
            3105 to Coord(3221, 3221), 3106 to Coord(3226, 3220), 3111 to Coord(3218, 3216), 1173 to Coord(3228, 3215),
        )
        const val TICK_MS = 600L
        private const val HITPOINTS = 3
        private const val INTERFACE_INVENTORY = 149
        private const val INTERFACE_WORN = 387
        private const val ANIM_EAT = 829
        private const val SCRIPT_CHATBOX_RESET_BACKGROUND = 2379
        private const val SCRIPT_CHAT_DEFAULT_RESTORE_INPUT = 2158
        private const val CHATBOX_INTERFACE = 162
        /** chatbox:chatmodal; 566 in rev 239, 568 in rev 241 (two components were inserted before it). */
        private const val CHATBOX_MODAL = 568
        private const val CHAT_LEFT = 231
        private const val CHAT_LEFT_HEAD = 2
        private const val CHAT_LEFT_NAME = 4
        private const val CHAT_LEFT_CONTINUE = 5
        private const val CHAT_LEFT_TEXT = 6
        private const val ANIM_CHAT_NEUTRAL = 588
        /** IF event bit 0: the component is a pause ("continue") button. */
        private const val IF_EVENT_PAUSE = 1
        /** All five ground ops visible. */
        private const val OBJ_OPS_ALL: Byte = 0x1F
        /** Client-side lifetime of a dropped obj; 0 makes it expire immediately. */
        private const val OBJ_DESPAWN_TICKS = 3000
        /** wornitems slot components -> wearpos. */
        private val WORN_SLOT_COMPONENTS = mapOf(
            15 to 0, 16 to 1, 17 to 2, 18 to 3, 19 to 4, 20 to 5, 21 to 7, 22 to 9, 23 to 10, 24 to 12, 25 to 13,
        )
        /** V2 events2: one bit per op (ops 1-10, plus bit 0 in case numbering is 1-based). */
        private const val IF_EVENTS_OPS = 0x7FF
        private const val VARP_DISPLAYNAME = 1737
        /** How long a dropped player is kept so the client can reconnect (60 seconds). */
        private const val RECONNECT_GRACE_TICKS = 100
        private const val REBUILD_DISTANCE = 4
        private const val MOVESPEED_TELEPORT = 127
        private val DEFAULT_KITS = intArrayOf(0, 10, 18, 26, 33, 36, 42)

        /** The classic new-player inventory. */
        private val STARTER_KIT = listOf(
            1351 to 1, 590 to 1, 303 to 1, 315 to 1, 1925 to 1, 1931 to 1, 2309 to 1, 1265 to 1,
            1205 to 1, 1277 to 1, 1171 to 1, 841 to 1, 882 to 25, 556 to 25, 558 to 15, 555 to 6,
            557 to 4, 559 to 2, 995 to 25,
        )
    }
}
