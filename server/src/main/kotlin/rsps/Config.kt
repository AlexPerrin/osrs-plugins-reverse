package rsps

import net.rsprot.crypto.rsa.RsaKeyPair
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Properties
import kotlin.io.path.Path

/** Server settings, all overridable through environment variables (see docker-compose.yml). */
class Config(
    val dataDir: Path,
    val cacheDir: Path,
    val gamePort: Int,
    val httpPort: Int,
    val worldHost: String,
    val httpBase: String,
    val worldId: Int,
    val worldFlags: Int,
    val spawn: Coord,
    val proofOfWork: Boolean,
    val validateCrcs: Boolean,
    val javConfigTemplate: Path?,
    /** Log every unidentified Android client packet, not just the first per opcode. */
    val logAndroidPackets: Boolean,
    /** Serve the sign-in locally instead of Jagex (needs a client built with LOCAL_AUTH=1). */
    val localAuth: Boolean,
    val localDisplayName: String,
    /** Per-account save files (see [Saves]); a named Docker volume in docker-compose.yml. */
    val saveDir: Path,
    val rsa: RsaKeyPair,
) {
    companion object {
        const val REVISION = 241

        fun fromEnv(): Config {
            fun env(name: String, default: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
            val dataDir = Path(env("DATA_DIR", "data"))
            Files.createDirectories(dataDir)
            val worldHost = env("WORLD_HOST", "127.0.0.1")
            val httpPort = env("HTTP_PORT", "8080").toInt()
            val (x, z, level) = env("SPAWN", "3222,3218,0").split(',').map { it.trim().toInt() }
            return Config(
                dataDir = dataDir,
                cacheDir = Path(env("CACHE_DIR", dataDir.resolve("cache").toString())),
                gamePort = env("GAME_PORT", "43594").toInt(),
                httpPort = httpPort,
                worldHost = worldHost,
                httpBase = env("HTTP_BASE", "http://$worldHost:${env("HTTP_PUBLIC_PORT", httpPort.toString())}"),
                worldId = env("WORLD_ID", "1").toInt(),
                worldFlags = env("WORLD_FLAGS", "1").toInt(),
                spawn = Coord(x, z, level),
                proofOfWork = env("PROOF_OF_WORK", "false").toBoolean(),
                validateCrcs = env("VALIDATE_CRCS", "false").toBoolean(),
                javConfigTemplate = env("JAV_CONFIG_TEMPLATE", "").takeIf { it.isNotEmpty() }?.let { Path(it) },
                logAndroidPackets = env("LOG_ANDROID_PACKETS", "false").toBoolean(),
                localAuth = env("LOCAL_AUTH", "false").toBoolean(),
                localDisplayName = env("LOCAL_DISPLAY_NAME", "Tester"),
                saveDir = Path(env("SAVE_DIR", dataDir.resolve("saves").toString())),
                rsa = env("RSA_FILE", "").takeIf { it.isNotEmpty() }?.let { loadRsa(Path(it)) }
                    ?: loadOrCreateRsa(dataDir.resolve("rsa.properties")),
            )
        }

        /**
         * 1024-bit key, matching the size of the modulus embedded in the client, so the client's
         * 256-hex-digit modulus string can be swapped in place.
         */
        /** A fixed key (RSA_FILE, baked into the image) so restarts never invalidate patched clients. */
        private fun loadRsa(file: Path): RsaKeyPair {
            check(Files.exists(file)) { "RSA_FILE $file does not exist" }
            return loadOrCreateRsa(file)
        }

        private fun loadOrCreateRsa(file: Path): RsaKeyPair {
            val props = Properties()
            if (Files.exists(file)) {
                Files.newBufferedReader(file).use(props::load)
            } else {
                val key = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
                val priv = key.private as RSAPrivateCrtKey
                props["modulus"] = priv.modulus.toString(16)
                props["private_exponent"] = priv.privateExponent.toString(16)
                props["public_exponent"] = priv.publicExponent.toString(16)
                Files.newBufferedWriter(file).use { props.store(it, "Login RSA key. Patch 'modulus' into the client.") }
            }
            return RsaKeyPair(
                BigInteger(props.getProperty("private_exponent"), 16),
                BigInteger(props.getProperty("modulus"), 16),
            )
        }
    }
}

data class Coord(val x: Int, val z: Int, val level: Int = 0) {
    val zoneX get() = x shr 3
    val zoneZ get() = z shr 3
}
