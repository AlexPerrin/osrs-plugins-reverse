package rsps

import com.github.michaelbull.logging.InlineLogger
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * A stand-in for Jagex's sign-in, so the Android client can be used without a Jagex account
 * (enabled with LOCAL_AUTH=true and a client built with LOCAL_AUTH=1 tools/repack.sh).
 *
 * The client's Java SDK runs a standard OpenID Connect authorization-code flow (AppAuth) against
 * the issuers from jav_config params 11 and 22, then its native code calls Jagex's game-session API
 * at the param 11 base: POST sessions {idToken} -> {sessionId}, GET accounts -> characters,
 * POST tokens -> {token}. The token ends up in the game login (auth type 7), which this server
 * accepts regardless. Tokens here are opaque and unsigned; nothing is validated.
 */
class LocalAuth(private val config: Config) {
    private val log = InlineLogger()
    private val random = SecureRandom()
    private val nonces = ConcurrentHashMap<String, String?>()

    val issuer get() = "${config.httpBase}/"

    class Response(val status: String, val contentType: String, val body: ByteArray, val location: String? = null)

    fun handle(method: String, path: String, query: Map<String, String>, body: String): Response? {
        log.info { "auth: $method $path ${if (body.isNotEmpty()) "(${body.length} bytes)" else ""}" }
        return when {
            path.endsWith("/.well-known/openid-configuration") -> json(discovery())
            path.endsWith("/oauth2/auth") -> authorize(query)
            path.endsWith("/oauth2/token") -> json(token(form(body)))
            path.endsWith("/userinfo") -> json("""{"sub":"local-player","nickname":"Player"}""")
            path.endsWith("/jwks") -> json("""{"keys":[]}""")
            path.endsWith("game-session/v1/sessions") -> json("""{"sessionId":"${randomId()}"}""")
            path.endsWith("game-session/v1/accounts") ->
                json("""[{"accountId":"$ACCOUNT_ID","displayName":"${config.localDisplayName}","userHash":"$ACCOUNT_ID"}]""")
            path.endsWith("game-session/v1/tokens") -> json("""{"token":"${randomId()}"}""")
            else -> null
        }
    }

    private fun discovery(): String =
        """{"issuer":"$issuer","authorization_endpoint":"${issuer}oauth2/auth","token_endpoint":"${issuer}oauth2/token",""" +
            """"userinfo_endpoint":"${issuer}userinfo","jwks_uri":"${issuer}jwks","response_types_supported":["code"],""" +
            """"subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"],""" +
            """"scopes_supported":["openid","offline","gamesso.token.create"],"code_challenge_methods_supported":["S256"]}"""

    /** Signs in immediately: redirects straight back to the app with a code. */
    private fun authorize(query: Map<String, String>): Response {
        val redirect = query["redirect_uri"] ?: return Response("400 Bad Request", "text/plain", "missing redirect_uri".toByteArray())
        val code = randomId()
        nonces[code] = query["nonce"]
        val state = query["state"]?.let { "&state=" + URLEncoder.encode(it, Charsets.UTF_8) } ?: ""
        return Response("302 Found", "text/plain", ByteArray(0), "$redirect?code=$code$state")
    }

    private fun token(form: Map<String, String>): String {
        val clientId = form["client_id"] ?: "com_jagex_auth_mobile_android_osrs"
        val nonce = form["code"]?.let { nonces.remove(it) }
        val now = System.currentTimeMillis() / 1000
        val claims = buildString {
            append("""{"iss":"$issuer","sub":"$ACCOUNT_ID","aud":"$clientId","exp":${now + 86400},"iat":$now""")
            if (nonce != null) append(""","nonce":"$nonce"""")
            append(""","login_provider":"jagex","nickname":"${config.localDisplayName}"}""")
        }
        val idToken = listOf("""{"alg":"RS256","typ":"JWT"}""", claims)
            .joinToString(".") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) } + ".local"
        return """{"access_token":"${randomId()}","token_type":"Bearer","expires_in":86400,""" +
            """"refresh_token":"${randomId()}","id_token":"$idToken","scope":"openid offline gamesso.token.create"}"""
    }

    private fun json(body: String) = Response("200 OK", "application/json", body.toByteArray())

    private fun randomId(): String = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private companion object {
        private const val ACCOUNT_ID = "100000001"

        fun form(body: String): Map<String, String> =
            body.split('&').filter { '=' in it }.associate {
                URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
            }
    }
}
