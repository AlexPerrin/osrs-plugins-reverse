package rsps

import com.github.michaelbull.logging.InlineLogger
import io.netty.buffer.Unpooled
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.compression.provider.HuffmanCodecProvider
import net.rsprot.crypto.rsa.RsaKeyPair
import net.rsprot.protocol.android.AndroidUnknownPacket
import net.rsprot.protocol.api.AbstractNetworkServiceFactory
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.handlers.LoginHandlers
import net.rsprot.protocol.api.js5.Js5GroupProvider
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import net.rsprot.protocol.game.incoming.misc.user.ClientCheat
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import net.rsprot.protocol.game.incoming.locs.OpLocT
import net.rsprot.protocol.game.incoming.objs.OpObjT
import net.rsprot.protocol.game.incoming.npcs.OpNpcT
import net.rsprot.protocol.game.incoming.npcs.OpNpcV2
import net.rsprot.protocol.game.incoming.objs.OpObjV2
import net.rsprot.protocol.loginprot.incoming.pow.ProofOfWorkProvider
import net.rsprot.protocol.loginprot.incoming.pow.challenges.sha256.Sha256Challenge
import net.rsprot.protocol.loginprot.incoming.pow.challenges.sha256.Sha256MetaData
import net.rsprot.protocol.message.IncomingGameMessage
import net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepositoryBuilder
import net.rsprot.protocol.message.codec.incoming.provider.DefaultGameMessageConsumerRepositoryProvider
import net.rsprot.protocol.message.codec.incoming.provider.GameMessageConsumerRepositoryProvider
import kotlin.time.measureTimedValue

private val log = InlineLogger("rsps.Main")

fun main() {
    val config = Config.fromEnv()
    log.info { "Starting test server for revision ${Config.REVISION}" }
    log.info { "Client RSA modulus (patch this into the client):\n${config.rsa.modulus.toString(16)}" }

    val (cache, cacheLoad) = measureTimedValue { Cache(config.cacheDir) }
    val (js5, js5Build) = measureTimedValue { Js5Store.build(cache) }
    log.info { "Cache ${config.cacheDir}: ${cache.archives.size} archives, opened in $cacheLoad, JS5 responses built in $js5Build" }

    val huffman = HuffmanCodec.create(Unpooled.wrappedBuffer(checkNotNull(cache.fileByName(10, "huffman")) { "huffman missing from cache" }))
    val gameframes = loadGameframes(cache)
    val objTypes = cache.files(2, 10).mapValues { (_, data) -> ObjWear.decode(data) }
    log.info {
        "Obj configs: ${objTypes.size} objs, ${objTypes.values.count { it.wearable }} wearable " +
            "(bronze sword -> ${objTypes[1277]?.wearpos}, shrimps -> ${objTypes[315]?.iops?.toList()})"
    }
    val npcTypes = cache.files(2, 9).mapValues { (_, data) -> EntityType.decodeNpc(data) }
    val locTypes = cache.files(2, 6).mapValues { (_, data) -> EntityType.decodeLoc(data) }
    log.info {
        "NPC configs: ${npcTypes.size} (3105 -> ${npcTypes[3105]?.name} ${npcTypes[3105]?.ops?.toList()}), " +
            "loc configs: ${locTypes.size} (1535 -> ${locTypes[1535]?.name} ${locTypes[1535]?.ops?.toList()})"
    }
    val varbitTypes = cache.files(2, 14).mapNotNull { (id, data) -> VarbitType.decode(data)?.let { id to it } }.toMap()
    val quests = Quests.load(cache)
    val questTotals = Quests.totals(cache)
    log.info {
        "Varbits: ${varbitTypes.size}, quests: ${quests.size} (totals: ${questTotals.quests} quests, " +
            "${questTotals.miniquests} miniquests, ${questTotals.questPoints} qp) " +
            "(${quests.firstOrNull { it.name == "Cook's Assistant" }?.let { "${it.name} -> varp ${it.varId}, end ${it.end}" }})"
    }
    val (doors, doorLoad) = measureTimedValue { Maps.loadDoors(cache, locTypes) }
    log.info { "Map doors: ${doors.size} openable wall locs, loaded in $doorLoad (3226,3223 -> ${doors[Maps.key(3226, 3223, 0)]})" }
    cache.close()

    val world = World(config, gameframes, js5.crcs, objTypes, npcTypes, locTypes, varbitTypes, quests, questTotals, doors, Collision(Cache(config.cacheDir), locTypes))
    val service = NetworkFactory(config, world, js5, DefaultHuffmanCodecProvider(huffman)).build()
    world.service = service

    Http(config) { world.online }.start()
    service.start()
    log.info { "Game/JS5 listening on :${config.gamePort}, world host ${config.worldHost}" }
    world.run()
}

/** Pane-redirect enums: (toplevel_osrs_stretch component) -> (component in the given toplevel). */
private fun loadGameframes(cache: Cache): Map<Int, Map<Int, Int>> {
    val enums = cache.files(2, 8)
    return Gameframe.redirectEnums.mapValues { (toplevel, enumId) ->
        val data = enums[enumId]
        if (data == null) {
            log.warn { "Enum $enumId (pane redirect for toplevel $toplevel) missing" }
            return@mapValues emptyMap()
        }
        val type = EnumType.decode(enumId, data)
        val bad = type.ints.values.count { it ushr 16 != toplevel }
        if (bad > 0) log.warn { "Enum $enumId has $bad targets outside toplevel $toplevel; ids may have shifted" }
        type.ints
    }
}

private class NetworkFactory(
    private val config: Config,
    private val world: World,
    private val js5: Js5Store,
    private val huffman: HuffmanCodecProvider,
) : AbstractNetworkServiceFactory<Player>() {
    override val ports: List<Int> = listOf(config.gamePort)

    override val supportedClientTypes: List<OldSchoolClientType> =
        listOf(OldSchoolClientType.DESKTOP, OldSchoolClientType.ANDROID)

    override fun getRsaKeyPair(): RsaKeyPair = config.rsa

    override fun getHuffmanCodecProvider(): HuffmanCodecProvider = huffman

    override fun getJs5GroupProvider(): Js5GroupProvider = Js5GroupProvider { archive, group ->
        js5.response(archive, group).also {
            if (it == null) log.warn { "JS5 request for missing group $archive/$group" }
        }
    }

    override fun getGameConnectionHandler(): GameConnectionHandler<Player> = world

    override fun getLoginHandlers(): LoginHandlers =
        if (config.proofOfWork) LoginHandlers() else LoginHandlers(proofOfWorkProvider = NoProofOfWork)

    override fun getGameMessageConsumerRepositoryProvider(): GameMessageConsumerRepositoryProvider<Player> {
        val builder = GameMessageConsumerRepositoryBuilder<Player>()
            .addListener<MoveGameClick> { world.walkTo(this, it.x, it.z, it.keyCombination == 1) }
            .addListener<MoveMinimapClick> { world.walkTo(this, it.x, it.z, it.keyCombination == 1) }
            .addListener<ClientCheat> { world.command(this, it.command) }
            .addListener<MessagePublic> {
                extendedInfo.setChat(it.colour, it.effect, 2, false, it.message, it.pattern?.toByteArray() ?: ByteArray(0))
            }
            .addListener<If3Button> {
                if (it.interfaceId == Gameframe.LOGOUT_INTERFACE) world.logout(this)
                else if (world.button(this, it.interfaceId, it.componentId, it.sub, it.obj, it.op)) Unit
                else log.info { "If3Button ${it.interfaceId}:${it.componentId} op=${it.op} sub=${it.sub} obj=${it.obj}" }
            }
            .addListener<ResumePauseButton> { world.resumePause(this, it.interfaceId, it.componentId, it.sub) }
            .addListener<OpNpcV2> { world.opNpc(this, it.index, it.op) }
            .addListener<OpLocV2> { world.opLoc(this, it.id, it.x, it.z, it.op) }
            .addListener<OpObjV2> { world.opObj(this, it.id, it.x, it.z, it.op) }
            .addListener<OpLocT> { world.useOn(this, "loc", it.id, it.x, it.z, it.selectedInterfaceId, it.selectedSub, it.selectedObj) }
            .addListener<OpObjT> { world.useOn(this, "obj", it.id, it.x, it.z, it.selectedInterfaceId, it.selectedSub, it.selectedObj) }
            .addListener<OpNpcT> { world.useOnNpc(this, it.index, it.selectedInterfaceId, it.selectedSub, it.selectedObj) }
            .addListener<AndroidUnknownPacket> { world.unknownAndroidPacket(this, it) }
            .addGlobalListener { player, message: IncomingGameMessage ->
                log.debug { "<- ${player.username}: $message" }
            }
        return DefaultGameMessageConsumerRepositoryProvider(builder.build())
    }
}

private val NoProofOfWork = ProofOfWorkProvider<Sha256Challenge, Sha256MetaData> { _, _ -> null }
