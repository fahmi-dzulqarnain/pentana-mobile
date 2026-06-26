package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.LunchDto
import my.silentmode.pentana.shared.model.RespondRequest

class LunchRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun lunches(): List<LunchDto> = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.get(client.urlFor("/lunches")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<List<LunchDto>>>().data
    }

    /** Vote for an option, or pass null to mark "not attending". Returns the refreshed lunch. */
    @Throws(Exception::class)
    suspend fun respond(lunchId: Long, mealOptionId: Long?): LunchDto = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.post(client.urlFor("/lunches/$lunchId/respond")) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(RespondRequest(mealOptionId))
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<LunchDto>>().data
    }

    /** Swift-friendly wrappers that avoid constructing a nullable Long (KotlinLong?) on the Swift side. */
    @Throws(Exception::class)
    suspend fun chooseOption(lunchId: Long, mealOptionId: Long): LunchDto = respond(lunchId, mealOptionId)

    @Throws(Exception::class)
    suspend fun markNotAttending(lunchId: Long): LunchDto = respond(lunchId, null)
}
