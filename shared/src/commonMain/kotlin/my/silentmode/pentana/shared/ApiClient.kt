package my.silentmode.pentana.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Thrown when the API returns a non-2xx response. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Holds the configured Ktor client and the API base URL (e.g. "https://host/api/v1").
 * The platform engine (Darwin on iOS, OkHttp on Android) is selected automatically
 * from the classpath. The bearer token is read from [tokenStore] per request.
 */
class ApiClient(
    val baseUrl: String,
    val tokenStore: TokenStore,
    engine: HttpClientEngine? = null, // inject MockEngine in tests; null = platform default
) {
    // Always pass an explicit engine. On Kotlin/Native a no-arg HttpClient {} relies on
    // engine auto-registration, which the static-framework linker can dead-code-eliminate
    // (→ crash on the first request). Tests inject MockEngine; otherwise the platform default.
    // status handled explicitly (expectSuccess = false) so we can surface ApiException.
    val http: HttpClient = HttpClient(engine ?: defaultHttpEngine()) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    fun urlFor(path: String): String = baseUrl.trimEnd('/') + path

    fun ensureSuccess(response: HttpResponse) {
        val code = response.status.value
        if (code !in 200..299) {
            throw ApiException(code, "Request failed with status $code")
        }
    }
}
