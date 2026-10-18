package rsps

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Serves the client bootstrap endpoints the official client fetches before connecting:
 * `jav_config.ws` (world host, world id, world-list URL) and `slr.ws` (binary world list).
 */
class Http(private val config: Config, private val online: () -> Int) {
    private val localAuth = if (config.localAuth) LocalAuth(config) else null

    /**
     * A raw-socket responder rather than the JDK HttpServer: the game's HTTP client is picky,
     * so headers are written exactly (canonical case, explicit length, connection close).
     */
    fun start() {
        val server = ServerSocket(config.httpPort)
        val pool = Executors.newFixedThreadPool(4)
        thread(name = "http-accept", isDaemon = true) {
            while (true) {
                val socket = server.accept()
                pool.execute { socket.use(::handle) }
            }
        }
        log.info { "HTTP listening on :${config.httpPort} (public base ${config.httpBase})" }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 10_000
            val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = reader.readLine() ?: return
            val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
            val method = requestLine.substringBefore(' ')
            val target = requestLine.split(' ').getOrElse(1) { "/" }
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                java.net.URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to
                    java.net.URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
            }
            val length = headers.firstOrNull { it.startsWith("content-length:", true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
            val body = if (length > 0) String(CharArray(length).also { buf -> var n = 0; while (n < length) n += reader.read(buf, n, length - n).takeIf { it > 0 } ?: break }) else ""
            log.info { "HTTP $requestLine from ${socket.inetAddress.hostAddress}" }
            log.debug { "HTTP headers: $headers" }
            val out = socket.getOutputStream()
            localAuth?.handle(method, path, query, body)?.let {
                respond(out, it.contentType, it.body, it.status, it.location)
                return
            }
            when {
                path.endsWith("/jav_config.ws") -> respond(out, "text/plain; charset=ISO-8859-1", javConfig().toByteArray(Charsets.ISO_8859_1))
                path.endsWith("/slr.ws") || path.endsWith("/worldlist.ws") -> respond(out, "application/octet-stream", worldList())
                path == "/rsa" -> respond(out, "text/plain", "${config.rsa.modulus.toString(16)}\n".toByteArray())
                path == "/health" -> respond(out, "text/plain", "ok\n".toByteArray())
                else -> respond(out, "text/plain", "not found\n".toByteArray(), "404 Not Found")
            }
        } catch (e: Exception) {
            log.warn { "HTTP request failed: $e" }
        }
    }

    private fun respond(out: OutputStream, type: String, body: ByteArray, status: String = "200 OK", location: String? = null) {
        val head = "HTTP/1.1 $status\r\n" +
            (if (location != null) "Location: $location\r\n" else "") +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    /** Uses the template mirrored from the live config when present, else a minimal config. */
    fun javConfig(): String {
        val template = config.javConfigTemplate?.takeIf { Files.exists(it) } ?: return minimalJavConfig()
        var text = Files.readString(template)
        // payments.jagex.com (shop catalogue, purchase initiate/completion): never the live host,
        // the same as the minimal config.
        text = text.lines().joinToString("\n") { line ->
            if (line.startsWith("param=$PAYMENTS_PARAM=")) "param=$PAYMENTS_PARAM=${config.httpBase}/" else line
        }
        if (localAuth != null) {
            // Point the sign-in (issuers, game-session API, account/social URLs) at this server.
            text = text.lines().joinToString("\n") { line ->
                if (AUTH_PARAMS.any { line.startsWith("param=$it=") }) line.substringBeforeLast('=') + "=" + localAuth.issuer else line
            }
        }
        return text
            .replace("{{WORLD_HOST}}", config.worldHost)
            .replace("{{WORLD_ID}}", config.worldId.toString())
            .replace("{{WORLD_FLAGS}}", config.worldFlags.toString())
            .replace("{{MEMBERS_WORLD}}", membersWorld().toString())
            .replace("{{HTTP_BASE}}", config.httpBase)
    }

    /**
     * jav_config param 3 is the desktop client's members-world flag (its init compares it with
     * "true"); without it members items show as "X (Members)" with only Use/Destroy/Examine,
     * whatever param 5 (world flags) says. The Android client goes by the world list instead.
     */
    private fun membersWorld(): Boolean = (config.worldFlags and 1) != 0

    private fun minimalJavConfig(): String = buildString {
        fun line(s: String) = append(s).append('\n')
        line("title=Old School RuneScape (test server)")
        line("codebase=http://${config.worldHost}/")
        line("cachedir=oldschool")
        line("storebase=0")
        line("initial_jar=gamepack.jar")
        line("initial_class=client.class")
        line("viewerversion=124")
        line("win_sub_version=1")
        line("mac_sub_version=2")
        line("other_sub_version=2")
        line("window_preferredwidth=800")
        line("window_preferredheight=600")
        line("applet_minwidth=765")
        line("applet_minheight=503")
        line("applet_maxwidth=5760")
        line("applet_maxheight=2160")
        line("msg=lang0=English")
        line("param=2=${config.httpBase}/")
        line("param=3=${membersWorld()}")
        line("param=4=1")
        line("param=5=${config.worldFlags}")
        line("param=7=0")
        line("param=8=true")
        line("param=10=5")
        line("param=12=${config.worldId}")
        line("param=13=.${config.worldHost}")
        line("param=14=0")
        line("param=15=0")
        line("param=16=false")
        line("param=17=${config.httpBase}/slr.ws?order=LPWM")
        line("param=18=")
        line("param=21=0")
        line("param=25=${Config.REVISION}")
        line("param=29=30")
    }

    private fun worldList(): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).apply {
            writeShort(1)
            writeShort(config.worldId)
            writeInt(config.worldFlags)
            writeCString(config.worldHost)
            writeCString("Test server")
            writeByte(0) // location: US
            writeShort(online())
        }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(body.size())
            write(body.toByteArray())
        }
        return out.toByteArray()
    }

    private fun DataOutputStream.writeCString(s: String) {
        write(s.toByteArray(Charsets.ISO_8859_1))
        writeByte(0)
    }

    private companion object {
        /** jav_config params holding auth.jagex.com, social.auth.jagex.com, auth.runescape.com, account.jagex.com. */
        private val AUTH_PARAMS = listOf(11, 20, 22, 28)
        /** jav_config param holding payments.jagex.com. */
        private const val PAYMENTS_PARAM = 2
        private val log = com.github.michaelbull.logging.InlineLogger()
    }
}
