package rsps

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.android.AndroidUnknownPacket
import net.rsprot.protocol.android.AndroidUpdateZonePartialEnclosed
import net.rsprot.protocol.android.AndroidZoneProts
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.game.outgoing.zone.payload.LocDel
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import net.rsprot.protocol.game.outgoing.zone.payload.ObjDel
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim
import net.rsprot.protocol.game.outgoing.zone.payload.MapAnim
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import net.rsprot.protocol.game.outgoing.zone.payload.SoundArea
import net.rsprot.protocol.message.ZoneProt
import net.rsprot.protocol.message.IncomingGameMessage
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
import net.rsprot.protocol.game.outgoing.map.RebuildRegionV2
import net.rsprot.protocol.game.outgoing.map.util.RebuildRegionZone
import net.rsprot.protocol.game.outgoing.misc.client.HideLocOps
import net.rsprot.protocol.game.outgoing.misc.client.HideNpcOps
import net.rsprot.protocol.game.outgoing.misc.client.HideObjOps
import net.rsprot.protocol.game.outgoing.misc.client.ServerTickEnd
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.game.outgoing.specific.ProjAnimSpecificV4
import net.rsprot.protocol.internal.game.outgoing.info.CoordGrid
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStatV2
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetAnim
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetNpcHead
import net.rsprot.protocol.game.outgoing.interfaces.IfSetPlayerHead
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
    /** What happens when the open dialogue is continued (sub = chosen option for menus). */
    var onResume: ((Int) -> Unit)? = null
    val experience = IntArray(25).also { it[3] = 1154 }
    var hitpoints = 10
    var buildCenter = coord
    /** NPC the player is fighting (melee); attacks every [World.ATTACK_SPEED] ticks while in reach. */
    var combatTarget: Npc? = null
    var attackDelay = 0
    var lastAttackTick = -1L
    /** Ticks left of the death animation, or -1 while alive. */
    var deathTicks = -1
    /** Interface open in the journal tab's container, -1 for none. */
    var journalPanel = -1
    /** `::trace`: log every incoming message from this player, Event* ones included. */
    var trace = false

    val extendedInfo get() = infos.playerInfo.avatar.extendedInfo

    /** A pending op on an NPC, loc or obj: runs once [Interaction.reached] holds after movement. */
    var interaction: Interaction? = null

    val inventory = Inventory(INV_INVENTORY, 28)
    val worn = Inventory(INV_WORN, 14)
    val bank = Inventory(INV_BANK, 800)
    /** Server-side varp values (only the ones set by the server). */
    val varps = HashMap<Int, Int>()

    fun send(message: net.rsprot.protocol.message.OutgoingGameMessage) = session.queue(message)

    fun message(text: String) = send(MessageGame(0, text))
}

/** A ground obj visible to everyone (zone OBJ_ADD / OBJ_DEL). */
class GroundObj(val obj: Int, val count: Int, val coord: Coord, val spawnTick: Long)

class Interaction(val reached: (Player) -> Boolean, val repath: (Player) -> Unit, val action: (Player) -> Unit)

/** An inventory without stacking rules: one obj id and count per slot. */
class Inventory(val id: Int, val capacity: Int) {
    val objs = IntArray(capacity) { -1 }
    val counts = IntArray(capacity)

    /** Adds to an existing stack of [obj] if there is one (bank behaviour). */
    fun addStacked(obj: Int, count: Int): Boolean {
        val slot = objs.indexOf(obj)
        if (slot >= 0) {
            counts[slot] += count
            return true
        }
        return add(obj, count)
    }

    /** Adds [count] of [obj]: one stack if [stackable], otherwise one slot per item (all or nothing). */
    fun add(obj: Int, count: Int, stackable: Boolean): Boolean {
        if (stackable) return addStacked(obj, count)
        if (objs.count { it == -1 } < count) return false
        repeat(count) { add(obj, 1) }
        return true
    }

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
class Npc(val index: Int, val id: Int, val spawn: Coord, val avatar: NpcAvatar, val type: EntityType?) {
    var x = spawn.x
    var z = spawn.z
    val maxHitpoints = (type?.stats?.get(3) ?: 1).coerceAtLeast(1)
    var hitpoints = maxHitpoints
    /** Player this NPC fights back against. */
    var target: Player? = null
    var attackDelay = 0
    var lastAttackTick = -1L
    /** Ticks left of the death animation (then hidden), -1 while alive. */
    var deathTicks = -1
    /** Ticks until respawn while hidden, -1 otherwise. */
    var respawnTicks = -1
    val alive get() = deathTicks < 0 && respawnTicks < 0
    fun stat(i: Int) = type?.stats?.get(i) ?: 1
}

const val INV_INVENTORY = 93
const val INV_WORN = 94
const val INV_BANK = 95

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
    private val varbitTypes: Map<Int, VarbitType>,
    private val quests: List<QuestType>,
    private val questTotals: QuestTotals,
    /** Openable wall locs (doors, gates) from the map, by [Maps.key]. */
    private val mapDoors: Map<Long, MapLoc>,
    private val collision: Collision,
) : GameConnectionHandler<Player> {
    private val log = InlineLogger()
    private val saves = Saves(config.saveDir)

    /** Writes every player's save (autosave, shutdown). */
    fun saveAll() {
        for (player in players) player?.let(::savePlayer)
    }

    private fun savePlayer(player: Player) {
        try {
            saves.save(player)
        } catch (e: Exception) {
            log.warn(e) { "Could not save ${player.username}" }
        }
    }
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
        for (player in players) player?.let { move(it); interact(it); playerCombat(it) }
        for (npc in npcs) npcCombat(npc)
        if (!npcsFrozen) for (npc in npcs) if (npc.alive && npc.target == null) wander(npc)
        // Ground objs vanish when the client's despawn timer runs out; forget them here too.
        if (tick % AUTOSAVE_TICKS == 0L) saveAll()
        if (tick % 100 == 0L) groundObjs.filter { tick - it.spawnTick >= OBJ_DESPAWN_TICKS }.forEach(::removeGroundObj)
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
            val save = runCatching { saves.load(name) }.onFailure { log.warn(it) { "Could not load the save of $name" } }.getOrNull()
            val player = Player(index, name, session, block.seed, infos, toplevel, save?.coord ?: config.spawn, android)
            save?.let { saves.apply(it, player) }
            players[index] = player
            watchDisconnect(player, session)
            onLogin(player, restored = save != null)
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

    private fun onLogin(player: Player, restored: Boolean) {
        val c = player.coord
        player.infos.updateRootCoord(c.level, c.x, c.z)
        player.infos.updateRootBuildAreaCenteredOnPlayer(c.x, c.z)
        player.send(RebuildLoginV2(c.zoneX, c.zoneZ, 0, player.infos.playerInfo))
        sendGroundObjs(player)
        sendWallChanges(player)

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

        // Saved varps first (quest progress etc.); the fixed ones below override them.
        for ((id, value) in player.varps.toList()) setVarp(player, id, value)
        updateQuestPoints(player)

        for (stat in 0 until 25) {
            val level = levelForXp(player.experience[stat])
            val current = if (stat == HITPOINTS) player.hitpoints else level
            player.send(UpdateStatV2(stat, current, level, player.experience[stat]))
        }
        openJournalTab(player, JOURNAL_SUMMARY)
        player.send(UpdateRunEnergy(10000))
        // varbit has_displayname_transmitter (varp 1737 bit 31): without it the chatbox script
        // refuses input with "You must set a name before you can chat".
        setVarp(player, VARP_DISPLAYNAME, 1 shl 31)
        // NPC "Attack" option: 1 = always right-click. The client hides Attack with the unset value 0.
        setVarp(player, VARP_NPC_ATTACK_OPTION, 1)

        if (!restored) for ((obj, count) in STARTER_KIT) give(player, obj, count)
        player.send(player.inventory.toMessage())
        player.send(player.worn.toMessage())
        // Enable item ops 1-10 on the 28 inventory slots (interface 149, component 0), plus "Use"
        // (targets: ground obj, NPC, loc, player, inv item, component) and being a use target.
        player.send(IfSetEventsV2(INTERFACE_INVENTORY, 0, 0, 27, IF_EVENTS_INV_TARGET, IF_EVENTS_OPS))
        for (component in WORN_SLOT_COMPONENTS.keys) {
            player.send(IfSetEventsV2(INTERFACE_WORN, component, -1, -1, 0, IF_EVENTS_OPS))
        }

        player.extendedInfo.apply {
            setName(player.username)
            setCombatLevel(combatLevel(player))
            DEFAULT_KITS.forEachIndexed { slot, kit -> setIdentKit(slot, kit) }
            for (slot in 0 until 5) setColour(slot, 0)
            setBaseAnimationSet(808, 823, 819, 820, 821, 822, 824)
        }
        for (wearpos in 0 until 12) {
            val obj = player.worn.objs[wearpos]
            if (obj != -1) setWorn(player, wearpos, obj, player.worn.counts[wearpos])
        }
        // MESSAGE_GAME holds at most 255 bytes, so the command list is split over several lines.
        player.message("Welcome to the test server (rev ${Config.REVISION}).")
        player.message("Commands: ::tele x z [level], ::pos, ::home, ::players, ::anim id, ::item|::give id [n], ::empty, ::wear id [slot], ::xp skill n, ::openbank")
        player.message("::talk [npc] text, ::npc id, ::npcsay text, ::npcanim id, ::npcform id, ::freeze, ::drop")
        player.message("::obj id [n] [dz], ::zobj id [n], ::locadd id [shape] [rot], ::locdel [shape] [dx] [dz]")
        player.message("::quest name [start|done|reset|n], ::varp id [v], ::varbit id [v], ::events [e1] [e2], ::hit [n] [type], ::npchit [n]")
        player.message("Protocol tests: ::hideops npc|loc|obj [1|0], ::flag [x z], ::proj id [dx] [mode], ::region [dzx] [dzz] [rot], ::minimap n, ::pmfilter n, ::chatfilter p t, ::trace, ::jingle id")
    }

    fun spawnNpc(id: Int, coord: Coord): Npc? {
        if (npcs.size >= MAX_NPCS) return null
        val index = npcs.size + 1
        val avatar = service.npcAvatarFactory.alloc(index, id, coord.level, coord.x, coord.z, direction = random.nextInt(8))
        return Npc(index, id, coord, avatar, npcTypes[id]).also { npcs += it }
    }

    /** A random step now and then, staying within 3 tiles of the spawn point. */
    private fun wander(npc: Npc) {
        if (random.nextInt(8) != 0) return
        val dx = random.nextInt(3) - 1
        val dz = random.nextInt(3) - 1
        if (dx == 0 && dz == 0) return
        if (abs(npc.x + dx - npc.spawn.x) > 3 || abs(npc.z + dz - npc.spawn.z) > 3) return
        if (!collision.canStep(npc.x, npc.z, npc.spawn.level, dx, dz)) return
        npc.x += dx
        npc.z += dz
        npc.avatar.walk(dx, dz)
    }

    fun setVarp(player: Player, id: Int, value: Int) {
        player.varps[id] = value
        player.send(if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) VarpSmall(id, value) else VarpLarge(id, value))
    }

    fun getVarbit(player: Player, id: Int): Int {
        val type = varbitTypes[id] ?: return 0
        return ((player.varps[type.varp] ?: 0) ushr type.lsb) and type.mask
    }

    fun setVarbit(player: Player, id: Int, value: Int) {
        val type = varbitTypes[id] ?: return player.message("No varbit $id")
        val mask = type.mask shl type.lsb
        val old = player.varps[type.varp] ?: 0
        setVarp(player, type.varp, (old and mask.inv()) or ((value shl type.lsb) and mask))
    }

    /**
     * Quest points (varp 101) and the journal summary counters: quests_completed_count /
     * quests_total_count ("Quests Completed: x/y"), qp_max ("Quest Points: x/y"),
     * subquests_completed_count and miniquests_completed_count.
     */
    private fun updateQuestPoints(player: Player) {
        val done = quests.filter { questProgress(player, it) >= it.end }
        setVarp(player, VARP_QUEST_POINTS, done.sumOf { it.questPoints })
        setVarbit(player, VARBIT_QP_MAX, questTotals.questPoints)
        setVarbit(player, VARBIT_QUESTS_TOTAL, questTotals.quests)
        setVarbit(player, VARBIT_QUESTS_COMPLETED, done.count { it.kind == QuestType.Kind.QUEST })
        setVarbit(player, VARBIT_SUBQUESTS_COMPLETED, done.count { it.kind == QuestType.Kind.SUBQUEST })
        setVarbit(player, VARBIT_MINIQUESTS_COMPLETED, done.count { it.kind == QuestType.Kind.MINIQUEST })
    }

    /**
     * Journal side tab (side_journal 629): the server opens the selected panel into 629:43 -
     * 0 character summary (712), 1 quest list (399), 2 achievement diaries (259) - and sets
     * varbit side_journal_tab. Without it the tab is empty.
     */
    private fun openJournalTab(player: Player, tab: Int) {
        val panel = JOURNAL_PANELS.getOrNull(tab) ?: return
        if (player.journalPanel == panel) return
        if (player.journalPanel != -1) player.send(IfCloseSub(INTERFACE_SIDE_JOURNAL, SIDE_JOURNAL_CONTAINER))
        player.send(IfOpenSub(INTERFACE_SIDE_JOURNAL, SIDE_JOURNAL_CONTAINER, panel, 1))
        player.journalPanel = panel
        setVarbit(player, VARBIT_SIDE_JOURNAL_TAB, tab)
        if (panel == 712) sendSummaryCombatLevel(player)
    }

    /**
     * The character summary (712) shows varc 52 (combat level), which only the server sets: script
     * 3954(712:2, 712:3, level) stores it and redraws via 3310 (args from 712:0's onload, 3174).
     * Without it the panel shows -1.
     */
    private fun combatLevel(player: Player): Int {
        val l = IntArray(7) { levelForXp(player.experience[it]) }
        val base = 0.25 * (l[1] + l[3] + l[5] / 2)
        val melee = 0.325 * (l[0] + l[2])
        val range = 0.325 * (l[4] * 3 / 2)
        val mage = 0.325 * (l[6] * 3 / 2)
        return (base + maxOf(melee, range, mage)).toInt()
    }

    fun sendSummaryCombatLevel(player: Player) {
        if (player.journalPanel != 712) return
        player.send(RunClientScript(3954, listOf((712 shl 16) or 2, (712 shl 16) or 3, combatLevel(player))))
    }

    private fun questProgress(player: Player, quest: QuestType) =
        if (quest.varbit) getVarbit(player, quest.varId) else player.varps[quest.varId] ?: 0

    /** `::quest name [start|done|reset|value]`: shows or sets a quest's progress var. */
    private fun questCommand(player: Player, args: List<String>) {
        val state = args.lastOrNull()?.takeIf { it in QUEST_STATES || it.toIntOrNull() != null }
        val query = (if (state != null) args.dropLast(1) else args).joinToString(" ").lowercase()
        if (query.isBlank()) return player.message("::quest name|index [start|done|reset|value] (${quests.size} quests)")
        // Exact name, then index in the quest list (::quest 12), then a unique prefix
        // (ignoring "the "), then a unique substring.
        val matches = quests.filter { query in it.name.lowercase() }
        val prefix = matches.filter { it.name.lowercase().removePrefix("the ").startsWith(query) || it.name.lowercase().startsWith(query) }
        val quest = quests.firstOrNull { it.name.equals(query, true) }
            ?: query.toIntOrNull()?.let { quests.getOrNull(it) }
            ?: prefix.singleOrNull() ?: matches.singleOrNull()
            ?: return player.message(if (matches.isEmpty()) "No quest matches '$query'" else "Matches (::quest <index> ...): ${matches.take(6).joinToString { "${quests.indexOf(it)} ${it.name}" }}")
        if (state != null) {
            val value = when (state) {
                "start" -> quest.unstarted + 1
                "done" -> quest.end
                "reset" -> quest.unstarted
                else -> state.toInt()
            }
            if (quest.varbit) setVarbit(player, quest.varId, value) else setVarp(player, quest.varId, value)
            updateQuestPoints(player)
        }
        val progress = questProgress(player, quest)
        val status = when {
            progress >= quest.end -> "done"
            progress > quest.unstarted -> "started"
            else -> "not started"
        }
        player.message("${quest.name}: ${if (quest.varbit) "varbit" else "varp"} ${quest.varId} = $progress ($status, end ${quest.end})")
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
        sendGroundObjs(player)
        sendWallChanges(player)
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
        savePlayer(player)
        players[player.index] = null
        service.infoProtocols.dealloc(player.infos)
        log.info { "${player.username} disconnected; online=$online" }
    }

    // -- Incoming messages (dispatched from processIncomingPackets on the game thread) --

    fun walkTo(player: Player, x: Int, z: Int, ctrlRun: Boolean) {
        if (player.deathTicks >= 0) return
        player.interaction = null
        player.combatTarget = null
        pathTo(player, Coord(x, z, player.coord.level)) { tx, tz -> tx == x && tz == z }
        player.running = ctrlRun
    }

    fun teleport(player: Player, to: Coord) {
        player.interaction = null
        player.combatTarget = null
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
            "walk" -> {
                // ::walk x z - walks to an absolute tile with the pathfinder (as a minimap click would).
                val x = args.getOrNull(1)?.toIntOrNull()
                val z = args.getOrNull(2)?.toIntOrNull()
                if (x == null || z == null) return player.message("Usage: ::walk x z")
                walkTo(player, x, z, ctrlRun = false)
                player.message("Path: ${player.path.size} steps, ends at ${player.path.lastOrNull()?.let { "${it.x}, ${it.z}" } ?: "here"}")
            }
            "pos" -> player.message("Position: ${player.coord.x}, ${player.coord.z}, ${player.coord.level}")
            "anim" -> player.extendedInfo.setSequence(args.getOrNull(1)?.toIntOrNull() ?: -1, 0)
            "players" -> player.message("Online: ${players.filterNotNull().joinToString { it.username }}")
            // ::give is an alias (client-dev request); non-stackables go one per slot, stackables into one stack.
            "item", "give" -> {
                val obj = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::item|::give id [count]")
                if (!give(player, obj, args.getOrNull(2)?.toIntOrNull() ?: 1)) return player.message("Inventory full")
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
                sendSummaryCombatLevel(player)
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
            "locadd" -> {
                // ::locadd id [shape] [rotation] - adds a loc on the player's tile (shape 10 = centrepiece).
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::locadd id [shape] [rot]")
                val c = player.coord
                val shape = args.getOrNull(2)?.toIntOrNull() ?: 10
                val rot = args.getOrNull(3)?.toIntOrNull() ?: 0
                sendZone(player, c, listOf(LocAddChangeV2(id, c.x and 7, c.z and 7, shape, rot, 0x1F)))
            }
            "locdel" -> {
                // ::locdel [shape] [dx] [dz] - removes the loc of that shape's layer at an offset from the player.
                val shape = args.getOrNull(1)?.toIntOrNull() ?: 10
                val c = player.coord.let { Coord(it.x + (args.getOrNull(2)?.toIntOrNull() ?: 0), it.z + (args.getOrNull(3)?.toIntOrNull() ?: 0), it.level) }
                sendZone(player, c, listOf(LocDel(c.x and 7, c.z and 7, shape, 0)))
            }
            "zfx" -> {
                // ::zfx mapanim id [height] | locanim anim [shape] [dx] [dz] | sound id [loops] | proj id [dx]
                val n = args.getOrNull(2)?.toIntOrNull() ?: return player.message("::zfx mapanim|locanim|sound|proj id ...")
                val c = player.coord
                val a3 = args.getOrNull(3)?.toIntOrNull()
                val msg: ZoneProt = when (args.getOrNull(1)) {
                    "mapanim" -> MapAnim(n, 0, a3 ?: 0, c.x and 7, c.z and 7)
                    "locanim" -> {
                        val t = Coord(c.x + (args.getOrNull(4)?.toIntOrNull() ?: 0), c.z + (args.getOrNull(5)?.toIntOrNull() ?: 0), c.level)
                        return sendZone(player, t, listOf(LocAnim(n, t.x and 7, t.z and 7, a3 ?: 10, 0)))
                    }
                    "sound" -> SoundArea(n, 0, a3 ?: 1, 10, 0, c.x and 7, c.z and 7)
                    "proj" -> MapProjAnimV2(n, 40, 30, 0, 60, 16, 64, 0, 0, c.x and 7, c.z and 7,
                        c.x + (a3 ?: 5), c.z, c.level)
                    else -> return player.message("::zfx mapanim|locanim|sound|proj id ...")
                }
                sendZone(player, c, listOf(msg))
            }
            "zobj" -> {
                // ::zobj id [count] - same as ::obj (both use shared zone ground objs now).
                val obj = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::zobj id [count]")
                val count = args.getOrNull(2)?.toIntOrNull() ?: 1
                val c = player.coord
                addGroundObj(obj, count, c)
            }
            // "::bank" itself never reaches the server: the client turns it into a joke public chat line.
            "openbank" -> openBank(player)
            "varp" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("::varp id [value]")
                args.getOrNull(2)?.toIntOrNull()?.let { setVarp(player, id, it) }
                player.message("varp $id = ${player.varps[id] ?: 0}")
            }
            "varbit" -> {
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("::varbit id [value]")
                args.getOrNull(2)?.toIntOrNull()?.let { setVarbit(player, id, it) }
                player.message("varbit $id = ${getVarbit(player, id)} (varp ${varbitTypes[id]?.varp})")
            }
            "quest" -> questCommand(player, args.drop(1))
            "hit" -> {
                // ::hit [damage] [hitmark type] - hitsplat + headbar on yourself (type 1 = damage, 0 = block).
                val damage = args.getOrNull(1)?.toIntOrNull() ?: 1
                val type = args.getOrNull(2)?.toIntOrNull() ?: if (damage == 0) 0 else 1
                val max = levelForXp(player.experience[HITPOINTS])
                player.hitpoints = (player.hitpoints - damage).coerceIn(0, max)
                player.extendedInfo.addHitMark(-1, type, type, type, damage)
                player.extendedInfo.addHeadBar(-1, 0, startFill = HEADBAR_WIDTH * player.hitpoints / max)
                player.send(UpdateStatV2(HITPOINTS, player.hitpoints, player.hitpoints, player.experience[HITPOINTS]))
                if (player.hitpoints == 0) player.hitpoints = max
            }
            "events" -> {
                // Debug: resend the inventory's IF_SETEVENTS with other events1/events2 values (hex ok).
                val e1 = args.getOrNull(1)?.let { it.removePrefix("0x").toLongOrNull(if (it.startsWith("0x")) 16 else 10)?.toInt() } ?: IF_EVENTS_INV_TARGET
                val e2 = args.getOrNull(2)?.let { it.removePrefix("0x").toLongOrNull(if (it.startsWith("0x")) 16 else 10)?.toInt() } ?: IF_EVENTS_OPS
                player.send(IfSetEventsV2(INTERFACE_INVENTORY, 0, 0, 27, e1, e2))
                player.message("inventory events1=0x${e1.toString(16)} events2=0x${e2.toString(16)}")
            }
            "ptalk" -> openPlayerChat(player, line.trim().substringAfter(' ', "").ifBlank { "Hello! I am talking to myself." })
            "options" -> openOptions(player, "Select an Option", args.drop(1).ifEmpty { listOf("Yes", "No", "Maybe") })
            "heal" -> {
                player.hitpoints = levelForXp(player.experience[HITPOINTS])
                sendStat(player, HITPOINTS)
            }
            "combat" -> {
                // ::combat [level] - sets attack, strength, defence and hitpoints (default 99) for quick kills.
                val level = (args.getOrNull(1)?.toIntOrNull() ?: 99).coerceIn(1, 99)
                for (stat in intArrayOf(ATTACK, DEFENCE, STRENGTH, HITPOINTS)) {
                    player.experience[stat] = maxOf(XP_TABLE[level - 1], if (stat == HITPOINTS) 1154 else 0)
                    sendStat(player, stat)
                }
                player.hitpoints = levelForXp(player.experience[HITPOINTS])
                sendStat(player, HITPOINTS)
            }
            "minimap" -> {
                // MINIMAP_TOGGLE (Android 31): 0 normal, 1 no clicks, 2 hidden, 3 no clicks + hidden (desktop meaning).
                val st = args.getOrNull(1)?.toIntOrNull() ?: 0
                player.send(net.rsprot.protocol.game.outgoing.misc.client.MinimapToggle(st))
            }
            "trace" -> {
                // Logs every incoming message (camera, keyboard and mouse events too) for protocol checks.
                player.trace = !player.trace
                player.message("trace ${if (player.trace) "on" else "off"}")
            }
            "jingle" -> {
                // MIDI_JINGLE test: ::jingle id; when it ends the client should send SOUND_JINGLEEND (Android 46).
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::jingle id")
                player.send(net.rsprot.protocol.game.outgoing.sound.MidiJingle(id))
            }
            "chatfilter" -> {
                // CHAT_FILTER_SETTINGS (Android 78) control for ::pmfilter: ::chatfilter public trade (0 on, 1 friends, 2 off, ...).
                val pub = args.getOrNull(1)?.toIntOrNull() ?: 0
                val trade = args.getOrNull(2)?.toIntOrNull() ?: 0
                player.send(net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettings(pub, trade))
            }
            "pmfilter" -> {
                // CHAT_FILTER_SETTINGS_PRIVATECHAT (Android 11): 0 on, 1 friends, 2 off; watch the Private chat tab label.
                val st = args.getOrNull(1)?.toIntOrNull() ?: 0
                player.send(net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettingsPrivateChat(st))
            }
            "region" -> {
                // REBUILD_REGION_V2 (Android 41) test: ::region [dzx] [dzz] [rot] rebuilds the area around you from
                // zones offset by (dzx, dzz) zones (8 tiles each), rotated by rot; ::home or walking away restores it.
                val dzx = args.getOrNull(1)?.toIntOrNull() ?: 0
                val dzz = args.getOrNull(2)?.toIntOrNull() ?: 0
                val rot = args.getOrNull(3)?.toIntOrNull() ?: 0
                val c = player.coord
                player.send(RebuildRegionV2(c.zoneX, c.zoneZ, true) { x, z, level -> RebuildRegionZone(x + dzx, z + dzz, level, rot) })
                player.message("Region rebuilt from zones offset $dzx,$dzz rot $rot")
            }
            "proj" -> {
                // PROJANIM_SPECIFIC_V4 (Android 76) test: ::proj id [dx] [mode]. Nominal: starts at height 40 (x4 in the
                // client, about a player's height) at your tile, lands at 0 dx tiles east, flying from cycle +30 to +300
                // (about 6 s). mode bits swap the values given to suspect fields: 1 = start/end time, 2 = start/end
                // height, 4 = progress/startHeight. Struct slots are confirmed by the zone variant (see android-protocol.md).
                val id = args.getOrNull(1)?.toIntOrNull() ?: return player.message("Usage: ::proj spotanim [dx] [mode]")
                val dx = args.getOrNull(2)?.toIntOrNull() ?: 5
                val mode = args.getOrNull(3)?.toIntOrNull() ?: 0
                var t1 = 30; var t2 = 300; var h1 = 40; var h2 = 0; var prog = 0
                if (mode and 1 != 0) { t1 = 300; t2 = 30 }
                if (mode and 2 != 0) { h1 = 0; h2 = 40 }
                if (mode and 4 != 0) { prog = h1; h1 = 0 }
                val c = player.coord
                player.send(ProjAnimSpecificV4(id, h1, h2, t1, t2, 16, prog, CoordGrid(c.level, c.x, c.z), 0,
                    CoordGrid(c.level, c.x + dx, c.z), 0))
                player.message("proj $id mode $mode: times $t1/$t2 heights $h1/$h2 progress $prog")
            }
            "flag" -> {
                // SET_MAP_FLAG_V2 (Android 143): ::flag x z shows the minimap flag there, ::flag clears it.
                val x = args.getOrNull(1)?.toIntOrNull()
                val z = args.getOrNull(2)?.toIntOrNull()
                player.send(if (x != null && z != null) SetMapFlagV2(x, z) else SetMapFlagV2())
            }
            "hideops" -> {
                // Android opcodes 113 (npc) / 20 (loc) / 115 (obj); npc and loc verified live.
                val on = args.getOrNull(2) != "0"
                when (args.getOrNull(1)) {
                    "npc" -> player.send(HideNpcOps(on))
                    "loc" -> player.send(HideLocOps(on))
                    "obj" -> player.send(HideObjOps(on))
                    else -> return player.message("Usage: ::hideops npc|loc|obj [1|0]  (opcodes 113|20|115)")
                }
                player.message("Sent hideops ${args[1]} = $on")
            }
            "freeze" -> {
                npcsFrozen = !npcsFrozen
                player.message("NPC wandering ${if (npcsFrozen) "off" else "on"}")
            }
            "npchit" -> {
                // ::npchit [damage] - hitsplat + headbar on every NPC (tinted as dealt by you).
                val damage = args.getOrNull(1)?.toIntOrNull() ?: 1
                for (npc in npcs) {
                    npc.avatar.extendedInfo.addHitMark(0x10000 + player.index, if (damage == 0) 0 else 1, value = damage)
                    npc.avatar.extendedInfo.addHeadBar(0x10000 + player.index, 0, startFill = HEADBAR_WIDTH * 2 / 3)
                }
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
    /** Ground objs, shared by all players. */
    private val groundObjs = ArrayList<GroundObj>()

    private fun stackable(obj: Int) = objTypes[obj]?.stackable == true

    /** Puts [count] of [obj] in the player's inventory with the obj's stacking rule. */
    fun give(player: Player, obj: Int, count: Int): Boolean = player.inventory.add(obj, count, stackable(obj))

    private fun nearby(coord: Coord): List<Player> =
        players.filterNotNull().filter { it.android && !it.disconnected.get() && it.coord.level == coord.level && inBuildArea(it, coord) }

    fun addGroundObj(obj: Int, count: Int, coord: Coord) {
        groundObjs += GroundObj(obj, count, coord, tick)
        for (p in nearby(coord)) sendZone(p, coord, listOf(objAdd(obj, count, coord)))
    }

    private fun removeGroundObj(ground: GroundObj) {
        groundObjs.remove(ground)
        val c = ground.coord
        for (p in nearby(c)) sendZone(p, c, listOf(ObjDel(ground.obj, ground.count, c.x and 7, c.z and 7)))
    }

    private fun objAdd(obj: Int, count: Int, c: Coord) =
        ObjAdd(obj, count, c.x and 7, c.z and 7, OBJ_OPS_ALL, 0, OBJ_DESPAWN_TICKS, 0, false)

    /** After a (re)build the client has an empty scene: resend the ground objs of the new area, per zone. */
    private fun sendGroundObjs(player: Player) {
        groundObjs.filter { it.coord.level == player.coord.level && inBuildArea(player, it.coord) }
            .groupBy { Coord(it.coord.zoneX shl 3, it.coord.zoneZ shl 3, it.coord.level) }
            .forEach { (zone, objs) -> sendZone(player, zone, objs.map { objAdd(it.obj, it.count, it.coord) }) }
    }

    /** Wall-layer locs changed from the map (opened/closed doors): tile -> current loc, null = removed. */
    private val wallChanges = HashMap<Long, MapLoc?>()

    private fun wallAt(c: Coord): MapLoc? {
        val key = Maps.key(c.x, c.z, c.level)
        return if (key in wallChanges) wallChanges[key] else mapDoors[key]
    }

    private fun setWall(c: Coord, loc: MapLoc?, previous: MapLoc?) {
        wallChanges[Maps.key(c.x, c.z, c.level)] = loc
        val prot = wallProt(c, loc, previous) ?: return
        for (p in nearby(c)) sendZone(p, c, listOf(prot))
    }

    private fun wallProt(c: Coord, loc: MapLoc?, previous: MapLoc?): ZoneProt? = when {
        loc != null -> LocAddChangeV2(loc.id, c.x and 7, c.z and 7, loc.shape, loc.rot, 0x1F)
        previous != null -> LocDel(c.x and 7, c.z and 7, previous.shape, previous.rot)
        else -> null
    }

    /** The other state of a door: the nearest loc id with the same name and the opposite op. */
    private fun doorPartner(id: Int, wantOp: String): Int? {
        val name = locTypes[id]?.name ?: return null
        return (1..4).flatMap { listOf(id + it, id - it) }.firstOrNull { other ->
            val t = locTypes[other]
            t != null && t.name == name && t.ops.any { it.equals(wantOp, true) }
        }
    }

    /**
     * Opens or closes a door from the map: the closed door (rotation r) moves one tile towards its
     * wall side and turns to r + 1; closing reverses it. Unknown doors are just removed.
     */
    private fun toggleDoor(player: Player, id: Int, c: Coord, open: Boolean) {
        val current = wallAt(c)?.takeIf { it.id == id }
        if (current == null) {
            log.info { "Door $id at $c is not in the map data; removing it" }
            return setWall(c, null, MapLoc(id, 0, 0))
        }
        val partner = doorPartner(id, if (open) "Close" else "Open")
        if (partner == null) {
            log.info { "No ${if (open) "open" else "closed"} variant for door $id; removing it" }
            return setWall(c, null, current)
        }
        val (target, rot) = if (open) {
            val (dx, dz) = Maps.openOffset(current.rot)
            Coord(c.x + dx, c.z + dz, c.level) to ((current.rot + 1) and 3)
        } else {
            val rot = (current.rot - 1) and 3
            val (dx, dz) = Maps.openOffset(rot)
            Coord(c.x - dx, c.z - dz, c.level) to rot
        }
        log.info { "${player.username} ${if (open) "opens" else "closes"} door $id at $c rot ${current.rot} -> $partner at $target rot $rot" }
        setWall(c, null, current)
        setWall(target, MapLoc(partner, current.shape, rot), wallAt(target))
        collision.addLoc(current.id, c.x, c.z, c.level, current.shape, current.rot, add = false)
        collision.addLoc(partner, target.x, target.z, target.level, current.shape, rot, add = true)
    }

    /** Resends changed wall locs in the player's build area after a (re)build. */
    private fun sendWallChanges(player: Player) {
        wallChanges.entries.mapNotNull { (key, loc) ->
            val c = Coord(((key shr 16) and 0xFFFF).toInt(), (key and 0xFFFF).toInt(), (key shr 32).toInt())
            if (c.level != player.coord.level || !inBuildArea(player, c)) return@mapNotNull null
            wallProt(c, loc, mapDoors[key])?.let { c to it }
        }.groupBy { (c, _) -> Coord(c.zoneX shl 3, c.zoneZ shl 3, c.level) }
            .forEach { (zone, prots) -> sendZone(player, zone, prots.map { it.second }) }
    }

    private fun inBuildArea(player: Player, coord: Coord): Boolean {
        val baseX = (player.buildCenter.zoneX - 6) shl 3
        val baseZ = (player.buildCenter.zoneZ - 6) shl 3
        return coord.x - baseX in 0 until 104 && coord.z - baseZ in 0 until 104
    }
    /** ::freeze stops NPC wandering (makes NPCs easy to tap while testing). */
    private var npcsFrozen = false

    private val seenIncomingClasses = HashSet<Class<*>>()

    /**
     * Client packets that are decoded but not acted on yet (newly identified Android prots): logged
     * once per class at info (to confirm the decoder on live traffic), then at debug.
     */
    fun incoming(player: Player, message: IncomingGameMessage) {
        val first = seenIncomingClasses.add(message.javaClass)
        val quiet = message.javaClass.simpleName.startsWith("Event")
        if (first || player.trace || (config.logAndroidPackets && !quiet)) log.info { "<- ${player.username}: $message" }
        else log.debug { "<- ${player.username}: $message" }
    }

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
            interfaceId == INTERFACE_BANK && componentId == BANK_ITEMS -> withdraw(player, sub, op)
            interfaceId == INTERFACE_BANKSIDE && componentId == BANKSIDE_ITEMS -> deposit(player, sub)
            interfaceId == INTERFACE_WORN && componentId in WORN_SLOT_COMPONENTS -> unequip(player, WORN_SLOT_COMPONENTS.getValue(componentId))
            interfaceId == INTERFACE_SIDE_JOURNAL && componentId in 2..25 -> {
                log.info { "${player.username} journal button 629:$componentId op=$op" }
                openJournalTab(player, (componentId - 2) / 8)
            }
            else -> return false
        }
        return true
    }

    /**
     * Inventory item ops: op 2/3/4 = iop1-3, op 5 = Use, op 6 = iop4, op 7 = iop5 (Drop), op 10 =
     * Examine. (Bucket of milk has iop4 "Empty", which made Drop arrive as op 7.)
     */
    private fun inventoryOp(player: Player, slot: Int, op: Int) {
        val inv = player.inventory
        val obj = inv.objs.getOrNull(slot)?.takeIf { it != -1 } ?: return
        val type = objTypes[obj]
        val iop = when (op) { 2, 3, 4 -> op - 2; 6 -> 3; 7 -> 4; else -> -1 }
        val opName = type?.iops?.getOrNull(iop)
        log.info { "${player.username} inv op $op (${opName ?: "?"}) on $obj slot $slot" }
        when (opName?.lowercase()) {
            "wield", "wear", "equip" -> equip(player, slot)
            "drop" -> {
                spawnGroundObj(player, obj, inv.counts[slot])
                inv.objs[slot] = -1
                inv.counts[slot] = 0
                player.send(inv.toMessage())
            }
            "empty" -> {
                // bucket of milk/water -> bucket, pot of flour -> pot, jug of water -> jug
                val empty = mapOf(1927 to 1925, 1929 to 1925, 1933 to 1931, 1937 to 1935)[obj]
                    ?: return player.message("Nothing interesting happens.")
                inv.objs[slot] = empty
                player.send(inv.toMessage())
                player.message("You empty the ${type?.name?.lowercase()}.")
            }
            "eat", "drink" -> {
                inv.objs[slot] = -1
                inv.counts[slot] = 0
                player.send(inv.toMessage())
                player.extendedInfo.setSequence(ANIM_EAT, 0)
                player.message("You ${opName?.lowercase()} the ${type?.name}.")
            }
            else -> player.message("Nothing interesting happens.")
        }
    }

    /** The component of the player's toplevel that a toplevel_osrs_stretch component redirects to. */
    private fun paneTarget(player: Player, stretchComponent: Int): Int? {
        val stretch = (Gameframe.TOPLEVEL_STRETCH shl 16) or stretchComponent
        if (player.toplevel == Gameframe.TOPLEVEL_STRETCH) return stretch
        return gameframes[player.toplevel].orEmpty()[stretch]
    }

    /** Opens bankmain (12) in the main modal and bankside (15) in the side modal. */
    fun openBank(player: Player) {
        val main = paneTarget(player, Gameframe.STRETCH_MAINMODAL) ?: return player.message("No main modal in this toplevel")
        val side = paneTarget(player, Gameframe.STRETCH_SIDEMODAL) ?: return player.message("No side modal in this toplevel")
        player.send(IfOpenSub(main ushr 16, main and 0xFFFF, INTERFACE_BANK, 0))
        player.send(IfOpenSub(side ushr 16, side and 0xFFFF, INTERFACE_BANKSIDE, 0))
        player.send(player.bank.toMessage())
        player.send(player.inventory.toMessage())
        player.send(IfSetEventsV2(INTERFACE_BANK, BANK_ITEMS, 0, player.bank.capacity - 1, 0, IF_EVENTS_OPS))
        player.send(IfSetEventsV2(INTERFACE_BANKSIDE, BANKSIDE_ITEMS, 0, 27, 0, IF_EVENTS_OPS))
        log.info { "${player.username} opened the bank (main modal ${main ushr 16}:${main and 0xFFFF}, side ${side ushr 16}:${side and 0xFFFF})" }
    }

    /** Bank op 1 withdraws one, any other op the whole stack. */
    private fun withdraw(player: Player, slot: Int, op: Int) {
        val bank = player.bank
        val obj = bank.objs.getOrNull(slot)?.takeIf { it != -1 } ?: return
        val amount = if (op == 1) 1 else bank.counts[slot]
        var moved = 0
        val stack = stackable(obj)
        if (stack) {
            if (player.inventory.add(obj, amount, true)) moved = amount
        } else {
            while (moved < amount && player.inventory.add(obj, 1)) moved++
        }
        if (moved == 0) return player.message("You don't have enough inventory space.")
        bank.counts[slot] -= moved
        if (bank.counts[slot] <= 0) {
            // Shift later items left so the bank has no gaps, as in the real game.
            for (i in slot until bank.capacity - 1) {
                bank.objs[i] = bank.objs[i + 1]
                bank.counts[i] = bank.counts[i + 1]
            }
            bank.objs[bank.capacity - 1] = -1
            bank.counts[bank.capacity - 1] = 0
        }
        player.send(bank.toMessage())
        player.send(player.inventory.toMessage())
    }

    /** Deposits every inventory item with the same id as the one in [slot]. */
    private fun deposit(player: Player, slot: Int) {
        val inv = player.inventory
        val obj = inv.objs.getOrNull(slot)?.takeIf { it != -1 } ?: return
        var total = 0
        for (i in 0 until inv.capacity) {
            if (inv.objs[i] != obj) continue
            total += inv.counts[i]
            inv.objs[i] = -1
            inv.counts[i] = 0
        }
        if (!player.bank.addStacked(obj, total)) return player.message("Your bank is full.")
        player.send(player.bank.toMessage())
        player.send(inv.toMessage())
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
        setChatmodalUnclamp(player, false)
        player.send(IfSetNpcHead(CHAT_LEFT, CHAT_LEFT_HEAD, npcId))
        player.send(IfSetAnim(CHAT_LEFT, CHAT_LEFT_HEAD, ANIM_CHAT_NEUTRAL))
        player.send(IfSetText(CHAT_LEFT, CHAT_LEFT_NAME, name))
        player.send(IfSetText(CHAT_LEFT, CHAT_LEFT_TEXT, text))
        player.send(IfSetEventsV2(CHAT_LEFT, CHAT_LEFT_CONTINUE, -1, -1, IF_EVENT_PAUSE, 0))
        player.send(IfSetText(CHAT_LEFT, CHAT_LEFT_CONTINUE, "Click here to continue"))
        player.dialogueOpen = true
    }

    /** chat_right (217): the player's own chathead and name. */
    fun openPlayerChat(player: Player, text: String) {
        player.send(RunClientScript(SCRIPT_CHATBOX_RESET_BACKGROUND, emptyList()))
        player.send(IfOpenSub(CHATBOX_INTERFACE, CHATBOX_MODAL, CHAT_RIGHT, 0))
        setChatmodalUnclamp(player, false)
        player.send(IfSetPlayerHead(CHAT_RIGHT, CHAT_LEFT_HEAD))
        player.send(IfSetAnim(CHAT_RIGHT, CHAT_LEFT_HEAD, ANIM_CHAT_NEUTRAL))
        player.send(IfSetText(CHAT_RIGHT, CHAT_LEFT_NAME, player.username))
        player.send(IfSetText(CHAT_RIGHT, CHAT_LEFT_TEXT, text))
        player.send(IfSetEventsV2(CHAT_RIGHT, CHAT_LEFT_CONTINUE, -1, -1, IF_EVENT_PAUSE, 0))
        player.send(IfSetText(CHAT_RIGHT, CHAT_LEFT_CONTINUE, "Click here to continue"))
        player.dialogueOpen = true
    }

    /** chatmenu (219): script 58 builds the option list from "title|opt1|opt2..."; options resume with sub = index. */
    fun openOptions(player: Player, title: String, options: List<String>) {
        player.send(RunClientScript(SCRIPT_CHATBOX_RESET_BACKGROUND, emptyList()))
        player.send(IfOpenSub(CHATBOX_INTERFACE, CHATBOX_MODAL, CHATMENU, 0))
        setChatmodalUnclamp(player, true)
        player.send(RunClientScript(SCRIPT_CHATBOX_MULTI, listOf(title, options.joinToString("|"))))
        player.send(IfSetEventsV2(CHATMENU, CHATMENU_OPTIONS, 1, options.size, IF_EVENT_PAUSE, 0))
        player.dialogueOpen = true
    }

    fun resumePause(player: Player, interfaceId: Int, componentId: Int, sub: Int) {
        log.info { "${player.username} RESUME_PAUSEBUTTON $interfaceId:$componentId sub=$sub" }
        val next = player.onResume
        player.onResume = null
        if (next == null) {
            closeDialogue(player)
            if (interfaceId == CHATMENU) player.message("You chose option $sub.")
            return
        }
        // The next step opens its own dialogue over this one; close only if it does not.
        player.dialogueOpen = false
        next(sub)
        if (!player.dialogueOpen) {
            player.dialogueOpen = true
            closeDialogue(player)
        }
    }

    // -- Scripted dialogues: each step opens a chat and says what continuing does. --

    private fun npcSay(player: Player, npc: Int, text: String, then: ((Int) -> Unit)? = null) {
        openNpcChat(player, npc, npcTypes[npc]?.name ?: "", text)
        player.onResume = then
    }

    private fun playerSay(player: Player, text: String, then: ((Int) -> Unit)? = null) {
        openPlayerChat(player, text)
        player.onResume = then
    }

    /** Options menu; [choices] are (text, action) pairs, the chosen one runs on continue. */
    private fun choose(player: Player, vararg choices: Pair<String, () -> Unit>) {
        openOptions(player, "Select an Option", choices.map { it.first })
        player.onResume = { sub -> choices.getOrNull(sub - 1)?.second?.invoke() }
    }

    /** Cook's Assistant (varp 29: 0 not started, 1 started, 2 done), following the real dialogue. */
    private fun countObj(p: Player, obj: Int) = p.inventory.objs.indices.sumOf { if (p.inventory.objs[it] == obj) maxOf(1, p.inventory.counts[it]) else 0 }

    /** Removes up to [n] of [obj] (unstacked slots); returns how many were removed. */
    private fun takeObj(p: Player, obj: Int, n: Int): Int {
        var left = n
        for (i in p.inventory.objs.indices) {
            if (left == 0) break
            if (p.inventory.objs[i] != obj) continue
            p.inventory.objs[i] = -1
            p.inventory.counts[i] = 0
            left--
        }
        p.send(p.inventory.toMessage())
        return n - left
    }

    /** Shear on a sheep: needs shears; gives one wool. */
    private fun shear(p: Player, npc: Npc) {
        if (countObj(p, OBJ_SHEARS) == 0) return p.message("You need a set of shears to do this.")
        if (!give(p, OBJ_WOOL, 1)) return p.message("You don't have enough inventory space.")
        p.extendedInfo.setSequence(ANIM_SHEAR, 0)
        p.send(p.inventory.toMessage())
        p.message("You get some wool.")
        npc.avatar.extendedInfo.setSay("Baa!")
    }

    /** Spin on a spinning wheel: all wool into balls of wool (2.5 Crafting XP each). */
    private fun spin(p: Player) {
        val n = takeObj(p, OBJ_WOOL, countObj(p, OBJ_WOOL))
        if (n == 0) return p.message("You need some wool to spin.")
        repeat(n) { give(p, OBJ_BALL_OF_WOOL, 1) }
        p.send(p.inventory.toMessage())
        p.extendedInfo.setSequence(ANIM_SPIN, 0)
        addXp(p, CRAFTING, n * 5 / 2)
        p.message("You spin the wool into ${if (n == 1) "a ball" else "$n balls"} of wool.")
    }

    /**
     * Sheep Shearer (varp 179: 0 not started, 1 started, 1 + balls handed in, 21 done). Fred takes
     * any balls of wool you carry; 20 finish the quest (1 QP, 150 Crafting XP, 60 coins).
     */
    private fun fredDialogue(p: Player) {
        val fred = NPC_FRED
        val v = p.varps[VARP_SHEEPSHEARER] ?: 0
        when {
            v == 0 -> npcSay(p, fred, "What are you doing on my land? You're not the one who keeps leaving all my gates open and letting out all my chickens are you?") {
                choose(
                    p,
                    "I'm looking for a quest." to {
                        playerSay(p, "I'm looking for a quest.") {
                            npcSay(p, fred, "You're after a quest, you say? Actually I could do with a bit of help. My sheep are getting mighty woolly. I'd be much obliged if you could shear them. And while you're at it spin the wool for me too.") {
                                npcSay(p, fred, "Yes, that's it. Bring me 20 balls of wool. And I'm sure I could sort out some sort of payment. Of course, there's the small matter of The Thing.") {
                                    choose(
                                        p,
                                        "Yes, okay. I can do that." to {
                                            playerSay(p, "Yes, okay. I can do that.") {
                                                setVarp(p, VARP_SHEEPSHEARER, 1)
                                                if (countObj(p, OBJ_SHEARS) == 0) give(p, OBJ_SHEARS, 1).also { p.send(p.inventory.toMessage()) }
                                                npcSay(p, fred, "Good! Now one more thing, do you actually know how to shear a sheep? Here, take these shears. The spinning wheel is in Lumbridge Castle, upstairs.")
                                            }
                                        },
                                        "That doesn't sound a very exciting quest." to {
                                            playerSay(p, "That doesn't sound a very exciting quest.") {
                                                npcSay(p, fred, "Well what do you expect if you ask a farmer for a quest?")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                    "I'm looking for something to kill." to {
                        playerSay(p, "I'm looking for something to kill.") {
                            npcSay(p, fred, "What, on my land? Leave my livestock alone you scoundrel!")
                        }
                    },
                    "I'm lost." to {
                        playerSay(p, "I'm lost.") {
                            npcSay(p, fred, "How can you be lost? Just follow the road east and south. You'll end up in Lumbridge fairly quickly.")
                        }
                    },
                )
            }
            v < 21 -> npcSay(p, fred, "How are you doing getting those balls of wool?") {
                val have = countObj(p, OBJ_BALL_OF_WOOL)
                if (have == 0) {
                    playerSay(p, "I haven't got any at the moment.") {
                        npcSay(p, fred, "Ah well at least you haven't been eaten. You still need to bring me ${21 - v} more.")
                    }
                } else {
                    val given = takeObj(p, OBJ_BALL_OF_WOOL, minOf(have, 21 - v))
                    val now = v + given
                    setVarp(p, VARP_SHEEPSHEARER, now)
                    playerSay(p, "I have some.") {
                        if (now < 21) {
                            npcSay(p, fred, "Give 'em here then. You need to bring me ${21 - now} more.")
                        } else {
                            updateQuestPoints(p)
                            addXp(p, CRAFTING, 150)
                            give(p, OBJ_COINS, 60)
                            p.send(p.inventory.toMessage())
                            npcSay(p, fred, "I guess I'd better pay you then.") {
                                p.message("Congratulations! Quest complete: Sheep Shearer.")
                            }
                        }
                    }
                }
            }
            else -> npcSay(p, fred, "What are you doing on my land?")
        }
    }

    /**
     * Romeo & Juliet (varp 144): 0 talk to Romeo, 10 Juliet gives a message, 20 message to Romeo,
     * 30 Father Lawrence, 40 Apothecary makes a cadava potion from cadava berries, 50 potion to
     * Juliet, 60 back to Romeo, 100 done (5 QP). Abridged dialogue.
     */
    private fun romeoJulietDialogue(p: Player, npc: Int) {
        val v = p.varps[VARP_ROMEOJULIET] ?: 0
        fun set(value: Int) = setVarp(p, VARP_ROMEOJULIET, value)
        when (npc) {
            NPC_ROMEO -> when (v) {
                0 -> npcSay(p, npc, "Juliet, Juliet, wherefore art thou? Kind friend, have you seen Juliet? She's disappeared and I can't find her anywhere.") {
                    choose(
                        p,
                        "Yes, I have seen her actually!" to { playerSay(p, "Perhaps I could help to find her for you?") { set(10); npcSay(p, npc, "Oh would you? That would be wonderful! Please tell her I long to be with her. She lives in the house west of the square, upstairs.") } },
                        "No, sorry. I haven't seen her." to { playerSay(p, "No, sorry. I haven't seen her.") },
                    )
                }
                10 -> npcSay(p, npc, "Please find my Juliet. I'm so lonely without her.")
                20 -> if (countObj(p, OBJ_MESSAGE) > 0) {
                    playerSay(p, "Romeo, I have a message from Juliet.") {
                        takeObj(p, OBJ_MESSAGE, 1); set(30)
                        npcSay(p, npc, "She wants us to meet... but her father will never allow it. Go and see Father Lawrence at the church, he'll know what to do!")
                    }
                } else npcSay(p, npc, "Did Juliet give you a message for me? Please bring it to me!")
                30 -> npcSay(p, npc, "Please speak to Father Lawrence, at the church in the north-east of Varrock.")
                40, 50 -> npcSay(p, npc, "Have you got the potion for Juliet yet?")
                60 -> npcSay(p, npc, "Juliet's... dead? Oh no! ...Wait, she's only sleeping? Thank you, friend, I'll go to her at once!") {
                    set(100); updateQuestPoints(p)
                    p.message("Congratulations! Quest complete: Romeo & Juliet.")
                }
                else -> npcSay(p, npc, "Thank you for all your help with Juliet.")
            }
            NPC_JULIET -> when (v) {
                10 -> npcSay(p, npc, "Romeo! Is that you? Oh, a messenger from Romeo! Please take this message to him, quickly.") {
                    if (give(p, OBJ_MESSAGE, 1)) { p.send(p.inventory.toMessage()); set(20) } else p.message("You don't have enough inventory space.")
                }
                20 -> npcSay(p, npc, "Please take the message to Romeo, he's in Varrock square.") {
                    if (countObj(p, OBJ_MESSAGE) == 0 && give(p, OBJ_MESSAGE, 1)) p.send(p.inventory.toMessage())
                }
                50 -> if (countObj(p, OBJ_CADAVA_POTION) > 0) {
                    playerSay(p, "I have a cadava potion from Father Lawrence's plan.") {
                        takeObj(p, OBJ_CADAVA_POTION, 1); set(60)
                        npcSay(p, npc, "Wonderful! I'll drink it now. Go and tell Romeo the plan!")
                    }
                } else npcSay(p, npc, "Have you got the potion yet?")
                else -> npcSay(p, npc, if (v >= 60) "Please tell Romeo the plan." else "Who are you? Oh, I'm so lonely up here...")
            }
            NPC_LAWRENCE -> when (v) {
                30 -> npcSay(p, npc, "Romeo sent you? I have a plan: Juliet must drink a cadava potion so she seems dead. Go to the Apothecary in the south-west of Varrock; he'll make it from cadava berries.") { set(40) }
                else -> npcSay(p, npc, "Hello, my child. Bless you.")
            }
            NPC_APOTHECARY -> when (v) {
                40 -> if (countObj(p, OBJ_CADAVA_BERRIES) > 0) {
                    playerSay(p, "Father Lawrence sent me. I need a cadava potion.") {
                        takeObj(p, OBJ_CADAVA_BERRIES, 1)
                        give(p, OBJ_CADAVA_POTION, 1); p.send(p.inventory.toMessage()); set(50)
                        npcSay(p, npc, "Here you are. Be careful with it.")
                    }
                } else npcSay(p, npc, "A cadava potion? I'll need some cadava berries. They grow south-east of Varrock.")
                50 -> if (countObj(p, OBJ_CADAVA_POTION) == 0 && countObj(p, OBJ_CADAVA_BERRIES) > 0) {
                    takeObj(p, OBJ_CADAVA_BERRIES, 1); give(p, OBJ_CADAVA_POTION, 1); p.send(p.inventory.toMessage())
                    npcSay(p, npc, "Lost it? Here's another one.")
                } else npcSay(p, npc, "Take the potion to Juliet.")
                else -> npcSay(p, npc, "I am the Apothecary. I brew potions.")
            }
        }
    }

    private fun cookDialogue(p: Player) {
        val cook = NPC_COOK
        when (p.varps[VARP_COOKQUEST] ?: 0) {
            0 -> npcSay(p, cook, "What am I to do?") {
                choose(
                    p,
                    "What's wrong?" to {
                        playerSay(p, "What's wrong?") {
                            npcSay(p, cook, "Oh dear, oh dear, oh dear, I'm in a terrible terrible mess! It's the Duke's birthday today, and I should be making him a lovely big birthday cake.") {
                                npcSay(p, cook, "I've forgotten to buy the ingredients. I'll never get them in time now. He'll sack me! What will I do? I have four children and a goat to look after. Would you help me? Please?") {
                                    choose(
                                        p,
                                        "I'm always happy to help a cook in distress." to {
                                            playerSay(p, "Yes, I'll help you.") {
                                                setVarp(p, VARP_COOKQUEST, 1)
                                                npcSay(p, cook, "Oh thank you, thank you. I need milk, an egg and flour. I'd be very grateful if you can get them for me.")
                                            }
                                        },
                                        "I can't right now, Maybe later." to {
                                            playerSay(p, "No, I don't feel like it. Maybe later.") {
                                                npcSay(p, cook, "Fine. I always knew you Adventurer types were callous beasts. Go on your merry way!")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                    "Can you make me a cake?" to {
                        playerSay(p, "You're a cook, why don't you bake me a cake?") {
                            npcSay(p, cook, "*sniff* Don't talk to me about cakes...")
                        }
                    },
                    "You don't look very happy." to {
                        playerSay(p, "You don't look very happy.") {
                            npcSay(p, cook, "No, I'm not. The world is caving in around me - I am overcome by dark feelings of impending doom.")
                        }
                    },
                    "Nice hat!" to {
                        playerSay(p, "Nice hat!") {
                            npcSay(p, cook, "Er, thank you. It's a pretty ordinary cooks hat really.")
                        }
                    },
                )
            }
            1 -> npcSay(p, cook, "How are you getting on with finding the ingredients?") {
                val missing = COOK_INGREDIENTS.filter { (obj, _) -> obj !in p.inventory.objs }
                if (missing.isNotEmpty()) {
                    playerSay(p, "I haven't got all of them yet.") {
                        npcSay(p, cook, "I still need ${missing.joinToString(", ") { it.second }}.")
                    }
                } else {
                    playerSay(p, "Here's a bucket of milk, a pot of flour and an egg.") {
                        for ((obj, _) in COOK_INGREDIENTS) {
                            val slot = p.inventory.objs.indexOf(obj)
                            if (slot >= 0) {
                                p.inventory.objs[slot] = -1
                                p.inventory.counts[slot] = 0
                            }
                        }
                        p.send(p.inventory.toMessage())
                        setVarp(p, VARP_COOKQUEST, 2)
                        updateQuestPoints(p)
                        addXp(p, COOKING, 300)
                        npcSay(p, cook, "You've brought me everything I need! I am saved! Thank you!") {
                            p.message("Congratulations! Quest complete: Cook's Assistant.")
                        }
                    }
                }
            }
            else -> npcSay(p, cook, "Hello friend, how is the adventuring going?")
        }
    }

    /**
     * chatbox:chatmodal (162:568) is 479x96 by default; chatmenu (219, root 479x122 at y=12) does not
     * fit, so its lower rows were clipped on mobile. Varbit 10670 (chatmodal_unclamp) makes script
     * 113 (chat layout) size 162:568 to fill its parent instead; 113 runs here to apply it.
     */
    private fun setChatmodalUnclamp(player: Player, on: Boolean) {
        val value = if (on) 1 else 0
        if (getVarbit(player, VARBIT_CHATMODAL_UNCLAMP) == value) return
        setVarbit(player, VARBIT_CHATMODAL_UNCLAMP, value)
        player.send(RunClientScript(SCRIPT_CHAT_LAYOUT, emptyList()))
    }

    fun closeDialogue(player: Player) {
        if (!player.dialogueOpen) return
        player.dialogueOpen = false
        player.send(IfCloseSub(CHATBOX_INTERFACE, CHATBOX_MODAL))
        setChatmodalUnclamp(player, false)
        player.send(RunClientScript(SCRIPT_CHAT_DEFAULT_RESTORE_INPUT, emptyList()))
        // Rebuilds the chat history, which stays hidden after a dialogue otherwise.
        player.send(RunClientScript(SCRIPT_BUILD_CHATBOX, emptyList()))
    }

    /** Drops an obj on the ground at the player's tile (+dz), visible to everyone nearby. */
    private fun spawnGroundObj(player: Player, obj: Int, count: Int, dz: Int = 0) {
        val c = player.coord
        addGroundObj(obj, count, Coord(c.x, c.z + dz, c.level))
    }

    /**
     * Sends zone prots for the zone containing [coord] (UPDATE_ZONE_PARTIAL_ENCLOSED). The zone base
     * is in build-area tiles: the build area starts 6 zones south-west of the zone it was built
     * around ([Player.buildCenter]).
     */
    fun sendZone(player: Player, coord: Coord, messages: List<ZoneProt>) {
        if (!player.android) return log.info { "Zone prots are only implemented for Android" }
        val baseX = (player.buildCenter.zoneX - 6) shl 3
        val baseZ = (player.buildCenter.zoneZ - 6) shl 3
        val zoneX = (coord.zoneX shl 3) - baseX
        val zoneZ = (coord.zoneZ shl 3) - baseZ
        if (zoneX !in 0 until 104 || zoneZ !in 0 until 104) return log.info { "$coord is outside ${player.username}'s build area" }
        player.send(AndroidUpdateZonePartialEnclosed(zoneX, zoneZ, coord.level, AndroidZoneProts.encode(messages)))
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
            if (player.path.isEmpty()) {
                player.interaction = null
                player.message("I can't reach that!")
            }
        }
    }

    /** Replaces the player's path with a collision-aware one towards [target], ending where [goal] holds. */
    private fun pathTo(player: Player, target: Coord, goal: (Int, Int) -> Boolean) {
        player.path.clear()
        player.path += collision.findPath(player.coord, target, goal)
    }

    /** Goal test for a [reached] predicate written against the player's position. */
    private fun reachGoal(player: Player, reached: (Player) -> Boolean): (Int, Int) -> Boolean = { x, z ->
        val old = player.coord
        player.coord = Coord(x, z, old.level)
        val result = reached(player)
        player.coord = old
        result
    }

    private fun pathToNpc(player: Player, npc: Npc) =
        pathTo(player, Coord(npc.x, npc.z, player.coord.level)) { x, z ->
            abs(x - npc.x) <= 1 && abs(z - npc.z) <= 1 && !(x == npc.x && z == npc.z)
        }

    private fun startInteraction(player: Player, target: Coord, reached: (Player) -> Boolean, action: (Player) -> Unit) {
        val goal = reachGoal(player, reached)
        pathTo(player, target, goal)
        val interaction = Interaction(reached, { pathTo(it, target, goal) }, action)
        player.interaction = interaction
        // Already in reach: act this tick instead of waiting for movement.
        interact(player)
    }

    fun opNpc(player: Player, index: Int, op: Int) {
        val npc = npcs.firstOrNull { it.index == index } ?: return log.info { "OPNPC$op on unknown npc #$index" }
        val type = npcTypes[npc.id]
        val opName = type?.op(op)
        log.info { "${player.username} OPNPC$op #$index (${npc.id} ${type?.name}) '$opName'" }
        if (!npc.alive || player.deathTicks >= 0) return
        player.combatTarget = null
        if (opName.equals("attack", true)) return attack(player, npc)
        faceNpc(player, npc)
        pathToNpc(player, npc)
        val interaction = Interaction(
            reached = { abs(it.coord.x - npc.x) <= 1 && abs(it.coord.z - npc.z) <= 1 },
            repath = { pathToNpc(it, npc) },
            action = { p ->
                when (opName?.lowercase()) {
                    "bank" -> openBank(p)
                    "shear" -> shear(p, npc)
                    "talk-to" -> if (npc.id == NPC_COOK) cookDialogue(p) else if (npc.id == NPC_FRED) fredDialogue(p) else if (npc.id in ROMEO_JULIET_NPCS) romeoJulietDialogue(p, npc.id) else openNpcChat(p, npc.id, type?.name ?: "", "Hello, ${p.username}! This is a test server.")
                    else -> p.message("$opName ${type?.name ?: "npc ${npc.id}"}: nothing interesting happens.")
                }
            },
        )
        player.interaction = interaction
        interact(player)
    }

    private fun climb(p: Player, level: Int) {
        val c = p.coord
        teleport(p, collision.nearestOpen(c.x, c.z, level) ?: Coord(c.x, c.z, level))
    }

    /** OPLOCT / OPOBJT: "Use <selected obj> -> <loc or ground obj>"; walks there and reports it. */
    fun useOn(player: Player, kind: String, id: Int, x: Int, z: Int, selectedInterface: Int, slot: Int, selectedObj: Int) {
        val targetName = if (kind == "loc") locTypes[id]?.name else objTypes[id]?.name
        val usedName = objTypes[selectedObj]?.name ?: "obj $selectedObj"
        log.info { "${player.username} OP${kind.uppercase()}T $id ($targetName) at $x,$z with $usedName (if $selectedInterface slot $slot)" }
        startInteraction(
            player,
            Coord(x, z, player.coord.level),
            reached = { abs(it.coord.x - x) <= 1 && abs(it.coord.z - z) <= 1 },
            action = { it.message("You use the $usedName on the ${targetName ?: "$kind $id"}. Nothing interesting happens.") },
        )
    }

    /** OPNPCT: "Use <selected obj> -> NPC". */
    fun useOnNpc(player: Player, index: Int, selectedInterface: Int, slot: Int, selectedObj: Int) {
        val npc = npcs.firstOrNull { it.index == index } ?: return
        val name = npcTypes[npc.id]?.name
        val usedName = objTypes[selectedObj]?.name ?: "obj $selectedObj"
        log.info { "${player.username} OPNPCT #$index ($name) with $usedName (if $selectedInterface slot $slot)" }
        pathToNpc(player, npc)
        player.interaction = Interaction(
            reached = { abs(it.coord.x - npc.x) <= 1 && abs(it.coord.z - npc.z) <= 1 },
            repath = { pathToNpc(it, npc) },
            action = { it.message("You use the $usedName on $name. Nothing interesting happens.") },
        )
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
            // Solid locs (stairs, wheels, booths): a side tile with no wall between. Walls (doors): any adjacent tile.
            reached = if (collision.flags(x, z, player.coord.level) and Collision.LOC != 0) {
                { collision.reachesRect(it.coord.x, it.coord.z, it.coord.level, x, z, sx, sz) }
            } else {
                { it.coord.x in x - 1..x + sx && it.coord.z in z - 1..z + sz }
            },
            action = { p ->
                when (opName?.lowercase()) {
                    "bank" -> openBank(p)
                    "open", "close" -> {
                        val open = opName.lowercase() == "open"
                        val c = Coord(x, z, p.coord.level)
                        // Double gates (e.g. Fred's sheep pen 12986/12987): the other half next to it swings too.
                        val other = if (type?.name == "Gate") listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0).map { (dx, dz) -> Coord(x + dx, z + dz, c.level) }
                            .firstOrNull { n -> wallAt(n)?.let { w -> locTypes[w.id]?.let { t -> t.name == "Gate" && t.ops.any { it.equals(opName, true) } } } == true } else null
                        val otherId = other?.let { wallAt(it)?.id }
                        toggleDoor(p, id, c, open)
                        if (other != null && otherId != null) toggleDoor(p, otherId, other, open)
                    }
                    "spin" -> spin(p)
                    "pick-from" -> if (type?.name.equals("Cadava bush", true)) {
                        if (give(p, OBJ_CADAVA_BERRIES, 1)) { p.send(p.inventory.toMessage()); p.message("You pick some cadava berries.") }
                    } else p.message("You find nothing of interest.")
                    // Stairs/ladders: no destination table; move one level up/down and land on the nearest open
                    // tile (staircases usually block their own tiles on the other level).
                    "climb-up" -> if (p.coord.level < 3) climb(p, p.coord.level + 1)
                    "climb-down" -> if (p.coord.level > 0) climb(p, p.coord.level - 1)
                    // Middle-floor staircases (Lumbridge castle 16672): "Climb" asks for the direction.
                    "climb" -> choose(
                        p,
                        "Climb up." to { if (p.coord.level < 3) climb(p, p.coord.level + 1) },
                        "Climb down." to { if (p.coord.level > 0) climb(p, p.coord.level - 1) },
                    )
                    // Lumbridge castle 56230 / 56231 shortcuts between the ground and top (level 2) floors.
                    "top-floor" -> climb(p, minOf(p.coord.level + 2, 3))
                    "bottom-floor" -> climb(p, 0)
                    else -> p.message("$opName ${type?.name ?: "loc $id"} ($id at $x, $z): nothing interesting happens.")
                }
            },
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
                val ground = groundObjs.firstOrNull { it.obj == id && it.coord.x == x && it.coord.z == z && it.coord.level == p.coord.level }
                when {
                    ground == null -> p.message("Too late - it's gone!")
                    opName.equals("take", true) -> {
                        if (!give(p, id, ground.count)) return@startInteraction p.message("You don't have enough inventory space.")
                        removeGroundObj(ground)
                        p.send(p.inventory.toMessage())
                    }
                    else -> p.message("$opName ${type?.name}: nothing interesting happens.")
                }
            },
        )
    }

    // -- Combat (melee only): attack NPCs, NPCs fight back, death and respawn --

    private fun sendStat(player: Player, stat: Int) {
        val level = levelForXp(player.experience[stat])
        val current = if (stat == HITPOINTS) player.hitpoints else level
        player.send(UpdateStatV2(stat, current, level, player.experience[stat]))
    }

    private fun addXp(player: Player, stat: Int, xp: Int) {
        val before = levelForXp(player.experience[stat])
        player.experience[stat] = (player.experience[stat] + xp).coerceAtMost(200_000_000)
        val after = levelForXp(player.experience[stat])
        if (stat == HITPOINTS) player.hitpoints += after - before
        if (after > before) player.message("Congratulations, your ${SKILL_NAMES[stat]} level is now $after.")
        sendStat(player, stat)
    }

    /** Melee reach: orthogonally next to the NPC with no wall in between. */
    private fun inMeleeReach(x: Int, z: Int, level: Int, npc: Npc): Boolean {
        val dx = npc.x - x
        val dz = npc.z - z
        return abs(dx) + abs(dz) == 1 && collision.canStep(x, z, level, dx, dz)
    }

    private fun pathToMelee(player: Player, npc: Npc) =
        pathTo(player, Coord(npc.x, npc.z, player.coord.level)) { x, z -> inMeleeReach(x, z, player.coord.level, npc) }

    private fun attack(player: Player, npc: Npc) {
        player.interaction = null
        player.combatTarget = npc
        faceNpc(player, npc)
        if (!inMeleeReach(player.coord.x, player.coord.z, player.coord.level, npc)) pathToMelee(player, npc)
        playerCombat(player)
    }

    /** OSRS hit chance from the attack and defence rolls. */
    private fun hits(attackRoll: Int, defenceRoll: Int): Boolean {
        val chance = if (attackRoll > defenceRoll) {
            1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1))
        } else {
            attackRoll / (2.0 * (defenceRoll + 1))
        }
        return random.nextDouble() < chance
    }

    /** Melee max hit without equipment bonuses (strength level + 8, plus the weapon's rough bonus). */
    private fun maxHit(strength: Int, bonus: Int) = (0.5 + (strength + 8) * (bonus + 64) / 640.0).toInt()

    private fun weaponName(player: Player) = player.worn.objs[3].takeIf { it != -1 }?.let { objTypes[it]?.name?.lowercase() }

    private fun attackAnim(player: Player): Int {
        val weapon = weaponName(player) ?: return ANIM_PUNCH
        return when {
            "dagger" in weapon -> ANIM_STAB
            "axe" in weapon -> ANIM_AXE
            "sword" in weapon || "scimitar" in weapon -> ANIM_SLASH
            else -> ANIM_PUNCH
        }
    }

    private fun playerCombat(player: Player) {
        if (player.deathTicks >= 0) return playerDeath(player)
        if (player.attackDelay > 0) player.attackDelay--
        val npc = player.combatTarget ?: return
        val c = player.coord
        if (!npc.alive || npc.spawn.level != c.level) {
            player.combatTarget = null
            return
        }
        if (!inMeleeReach(c.x, c.z, c.level, npc)) {
            // The NPC may have moved: path again towards it every tick until in reach.
            pathToMelee(player, npc)
            if (player.path.isEmpty()) {
                player.combatTarget = null
                player.message("I can't reach that!")
            }
            return
        }
        player.path.clear()
        if (player.attackDelay > 0) return
        player.attackDelay = ATTACK_SPEED
        player.lastAttackTick = tick
        player.extendedInfo.setSequence(attackAnim(player), 0)
        val attack = levelForXp(player.experience[ATTACK])
        val strength = levelForXp(player.experience[STRENGTH])
        val bonus = if (weaponName(player) != null) WEAPON_BONUS else 0
        val damage = if (hits((attack + 8 + 3) * (bonus + 64), (npc.stat(1) + 9) * 64)) {
            random.nextInt(maxHit(strength, bonus) + 1)
        } else {
            0
        }
        damageNpc(npc, player, damage)
        if (damage > 0) {
            addXp(player, ATTACK, damage * 4)
            addXp(player, HITPOINTS, damage * 4 / 3)
        }
    }

    private fun damageNpc(npc: Npc, attacker: Player, damage: Int) {
        val dealt = minOf(damage, npc.hitpoints)
        npc.hitpoints -= dealt
        val info = npc.avatar.extendedInfo
        info.addHitMark(0x10000 + attacker.index, if (dealt == 0) HITMARK_BLOCK else HITMARK_DAMAGE, value = dealt)
        info.addHeadBar(0x10000 + attacker.index, 0, startFill = HEADBAR_WIDTH * npc.hitpoints / npc.maxHitpoints)
        if (npc.hitpoints <= 0) {
            npc.deathTicks = DEATH_TICKS
            npc.target = null
            npcFacePlayer(npc, null)
            info.setSequence(npcAnims(npc).death, 0)
            for (p in players) if (p?.combatTarget === npc) p.combatTarget = null
            return
        }
        if (npc.lastAttackTick != tick) info.setSequence(npcAnims(npc).block, 0)
        if (npc.target == null) {
            npc.target = attacker
            npc.attackDelay = 1
            npcFacePlayer(npc, attacker)
        }
    }

    // Facing (extended info: player 0x40 / NPC 0x40 on Android, desktop payload).
    private fun faceNpc(player: Player, npc: Npc) =
        player.extendedInfo.setFaceNpc(npc.index, false, 1, 0)

    @Suppress("DEPRECATION")
    private fun npcFacePlayer(npc: Npc, player: Player?) {
        if (player == null) npc.avatar.extendedInfo.setFacePathingEntity(-1)
        else npc.avatar.extendedInfo.setFacePlayer(player.index, false, 1, 0)
    }

    private class NpcAnims(val attack: Int, val block: Int, val death: Int)

    private fun npcAnims(npc: Npc) = NPC_ANIMS[npc.id] ?: HUMAN_ANIMS

    private fun npcCombat(npc: Npc) {
        if (npc.deathTicks >= 0) {
            if (npc.deathTicks-- > 0) return
            npc.deathTicks = -1
            npc.respawnTicks = RESPAWN_TICKS
            npc.avatar.setInaccessible(true)
            for ((obj, count) in NPC_DROPS[npc.id] ?: listOf(OBJ_BONES to 1)) addGroundObj(obj, count, Coord(npc.x, npc.z, npc.spawn.level))
            return
        }
        if (npc.respawnTicks >= 0) {
            if (npc.respawnTicks-- > 0) return
            npc.respawnTicks = -1
            npc.hitpoints = npc.maxHitpoints
            npc.x = npc.spawn.x
            npc.z = npc.spawn.z
            npc.avatar.teleport(npc.spawn.level, npc.x, npc.z, jump = true)
            npc.avatar.setInaccessible(false)
            return
        }
        if (npc.attackDelay > 0) npc.attackDelay--
        val target = npc.target ?: return
        val c = target.coord
        if (players[target.index] !== target || target.disconnected.get() || target.deathTicks >= 0 || c.level != npc.spawn.level ||
            abs(c.x - npc.spawn.x) > NPC_CHASE_RANGE || abs(c.z - npc.spawn.z) > NPC_CHASE_RANGE
        ) {
            npc.target = null
            npcFacePlayer(npc, null)
            return
        }
        if (!inMeleeReach(c.x, c.z, c.level, npc)) {
            stepTowards(npc, c)
            return
        }
        if (npc.attackDelay > 0) return
        npc.attackDelay = ATTACK_SPEED
        npc.lastAttackTick = tick
        npc.avatar.extendedInfo.setSequence(npcAnims(npc).attack, 0)
        val defence = levelForXp(target.experience[DEFENCE])
        val damage = if (hits((npc.stat(0) + 8) * 64, (defence + 9) * 64)) random.nextInt(maxHit(npc.stat(2), 0) + 1) else 0
        damagePlayer(target, npc, damage)
    }

    /** One step towards [to] (x first, then z), not onto its tile; the NPC stays near its spawn. */
    private fun stepTowards(npc: Npc, to: Coord) {
        val dx = (to.x - npc.x).sign
        val dz = (to.z - npc.z).sign
        for ((sx, sz) in listOf(dx to dz, dx to 0, 0 to dz)) {
            if (sx == 0 && sz == 0) continue
            val nx = npc.x + sx
            val nz = npc.z + sz
            if (nx == to.x && nz == to.z) continue
            if (abs(nx - npc.spawn.x) > NPC_CHASE_RANGE || abs(nz - npc.spawn.z) > NPC_CHASE_RANGE) continue
            if (!collision.canStep(npc.x, npc.z, npc.spawn.level, sx, sz)) continue
            npc.x = nx
            npc.z = nz
            npc.avatar.walk(sx, sz)
            return
        }
    }

    private fun damagePlayer(player: Player, npc: Npc, damage: Int) {
        val dealt = minOf(damage, player.hitpoints)
        player.hitpoints -= dealt
        val max = levelForXp(player.experience[HITPOINTS])
        val type = if (dealt == 0) HITMARK_BLOCK else HITMARK_DAMAGE
        player.extendedInfo.addHitMark(npc.index, type, type, type, dealt)
        player.extendedInfo.addHeadBar(npc.index, 0, startFill = HEADBAR_WIDTH * player.hitpoints / max)
        sendStat(player, HITPOINTS)
        if (player.hitpoints <= 0) {
            player.deathTicks = DEATH_TICKS
            player.combatTarget = null
            player.interaction = null
            player.path.clear()
            player.extendedInfo.setSequence(ANIM_PLAYER_DEATH, 0)
            return
        }
        if (player.lastAttackTick != tick) player.extendedInfo.setSequence(ANIM_BLOCK, 0)
        // Auto retaliate when idle.
        if (player.combatTarget == null && player.interaction == null && player.path.isEmpty()) {
            player.combatTarget = npc
            faceNpc(player, npc)
        }
    }

    private fun playerDeath(player: Player) {
        if (player.deathTicks-- > 0) return
        player.deathTicks = -1
        player.hitpoints = levelForXp(player.experience[HITPOINTS])
        sendStat(player, HITPOINTS)
        player.extendedInfo.setSequence(-1, 0)
        teleport(player, config.spawn)
        player.message("Oh dear, you are dead!")
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
        if (!give(player, obj, player.worn.counts[wearpos])) return player.message("Your inventory is full.")
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
        /** Every 30 s, so a crash or `docker kill` loses little. */
        private const val AUTOSAVE_TICKS = 50L

        /** Hans, a man, a woman and a chicken around the castle courtyard. */
        private val DEFAULT_NPCS = listOf(
            3105 to Coord(3221, 3221), 3106 to Coord(3226, 3220), 3111 to Coord(3218, 3216), 1173 to Coord(3228, 3215),
            4626 to Coord(3209, 3215),
            // Sheep Shearer: Fred the Farmer in his farmhouse, sheep in the pen east of it.
            NPC_FRED to Coord(3190, 3273), 2786 to Coord(3202, 3266), 2787 to Coord(3206, 3262), 2788 to Coord(3199, 3270),
            // Romeo & Juliet (Varrock): Romeo in the square, Juliet upstairs in her house to the west,
            // Father Lawrence in the north-east church, the Apothecary south-west.
            NPC_ROMEO to Coord(3211, 3422), NPC_JULIET to Coord(3158, 3427, 1), NPC_LAWRENCE to Coord(3254, 3483),
            NPC_APOTHECARY to Coord(3195, 3403),
        )
        private const val NPC_ROMEO = 5037
        private val ROMEO_JULIET_NPCS = setOf(5037, 5035, 5038, 5036)
        private const val NPC_JULIET = 5035
        private const val NPC_LAWRENCE = 5038
        private const val NPC_APOTHECARY = 5036
        private const val VARP_ROMEOJULIET = 144
        private const val OBJ_MESSAGE = 755
        private const val OBJ_CADAVA_POTION = 756
        private const val OBJ_CADAVA_BERRIES = 753
        private const val NPC_FRED = 732
        private const val VARP_SHEEPSHEARER = 179
        private const val CRAFTING = 12
        private const val OBJ_SHEARS = 1735
        private const val OBJ_WOOL = 1737
        private const val OBJ_BALL_OF_WOOL = 1759
        private const val OBJ_COINS = 995
        private const val ANIM_SHEAR = 893
        private const val ANIM_SPIN = 894
        private const val NPC_COOK = 4626
        private const val VARP_COOKQUEST = 29
        private const val COOKING = 7
        private val COOK_INGREDIENTS = listOf(1927 to "a bucket of milk", 1944 to "an egg", 1933 to "a pot of flour")
        private const val INTERFACE_SIDE_JOURNAL = 629
        /** side_journal:tab_container (gameval InterfaceID.SideJournal.TAB_CONTAINER). */
        private const val SIDE_JOURNAL_CONTAINER = 43
        private const val JOURNAL_SUMMARY = 0
        /** account_summary_sidepanel, questlist, area_task (achievement diaries). */
        private val JOURNAL_PANELS = listOf(712, 399, 259)
        private const val VARBIT_SIDE_JOURNAL_TAB = 8168
        const val TICK_MS = 600L
        const val ATTACK_SPEED = 4
        private const val ATTACK = 0
        private const val DEFENCE = 1
        private const val STRENGTH = 2
        private val SKILL_NAMES = listOf(
            "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
            "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving",
            "Slayer", "Farming", "Runecraft", "Hunter", "Construction", "Sailing", "?",
        )
        /** XP_TABLE[l - 1] = experience for level l. */
        private val XP_TABLE = IntArray(99).also { table ->
            var points = 0.0
            for (level in 1 until 99) {
                points += Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0))
                table[level] = Math.floor(points / 4).toInt()
            }
        }
        private const val HITMARK_BLOCK = 0
        private const val HITMARK_DAMAGE = 1
        /** Rough strength/accuracy bonus of a wielded weapon (bronze sword is +5). */
        private const val WEAPON_BONUS = 5
        private const val DEATH_TICKS = 4
        private const val RESPAWN_TICKS = 25
        private const val NPC_CHASE_RANGE = 8
        private const val OBJ_BONES = 526
        private const val ANIM_PUNCH = 422
        private const val ANIM_STAB = 376
        private const val ANIM_SLASH = 390
        private const val ANIM_AXE = 395
        private const val ANIM_BLOCK = 424
        private const val ANIM_PLAYER_DEATH = 836
        private val HUMAN_ANIMS = NpcAnims(422, 424, 836)
        /** Attack, block and death animations by NPC id (others use the human ones). */
        private val NPC_ANIMS = mapOf(1173 to NpcAnims(5387, 5388, 5389))
        private val NPC_DROPS = mapOf(1173 to listOf(OBJ_BONES to 1, 2138 to 1, 314 to 5))
        private const val HITPOINTS = 3
        private const val INTERFACE_INVENTORY = 149
        private const val INTERFACE_WORN = 387
        private const val INTERFACE_BANK = 12
        /** bankmain:items (rev 241, from RuneLite's gameval InterfaceID.Bankmain.ITEMS). */
        private const val BANK_ITEMS = 12
        private const val INTERFACE_BANKSIDE = 15
        private const val BANKSIDE_ITEMS = 3
        private const val ANIM_EAT = 829
        private const val SCRIPT_CHATBOX_RESET_BACKGROUND = 2379
        private const val SCRIPT_CHAT_DEFAULT_RESTORE_INPUT = 2158
        /** chat_build (RuneLite ScriptID.BUILD_CHATBOX): redraws the chat lines and the input line. */
        private const val SCRIPT_BUILD_CHATBOX = 216
        private const val CHATBOX_INTERFACE = 162
        /** chatbox:chatmodal; 566 in rev 239, 568 in rev 241 (two components were inserted before it). */
        private const val CHATBOX_MODAL = 568
        private const val CHAT_LEFT = 231
        private const val CHAT_RIGHT = 217
        private const val CHATMENU = 219
        private const val CHATMENU_OPTIONS = 1
        private const val SCRIPT_CHATBOX_MULTI = 58
        private const val SCRIPT_CHAT_LAYOUT = 113
        private const val VARBIT_CHATMODAL_UNCLAMP = 10670
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
        /** V2 events1 (old layout): target mask bits 11-16, drag target bit 20, use target bit 21. */
        private const val IF_EVENTS_INV_TARGET = 0x1F800 or (1 shl 20) or (1 shl 21)
        private const val VARP_DISPLAYNAME = 1737
        private const val VARP_NPC_ATTACK_OPTION = 1306
        private const val VARP_QUEST_POINTS = 101
        private const val VARBIT_QP_MAX = 1782
        private const val VARBIT_QUESTS_COMPLETED = 6347
        private const val VARBIT_QUESTS_TOTAL = 11877
        private const val VARBIT_SUBQUESTS_COMPLETED = 13987
        private const val VARBIT_MINIQUESTS_COMPLETED = 14162
        /** Headbar config 0 (30 pixels wide); fill is in the bar's width units. */
        private const val HEADBAR_WIDTH = 30
        private val QUEST_STATES = setOf("start", "done", "reset")
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
