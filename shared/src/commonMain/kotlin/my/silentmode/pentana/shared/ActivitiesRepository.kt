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
import my.silentmode.pentana.shared.model.ActivityDto
import my.silentmode.pentana.shared.model.DataEnvelope
import my.silentmode.pentana.shared.model.RegisterActivityRequest

class ActivitiesRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun activities(): List<ActivityDto> = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.get(client.urlFor("/activities")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<List<ActivityDto>>>().data
    }

    /** Register the signed-in member, answering the activity's questions. Returns the refreshed activity. */
    @Throws(Exception::class)
    suspend fun register(activityId: Long, answers: Map<String, String>): ActivityDto =
        withContext(Dispatchers.Main) {
            val token = client.tokenStore.get()
            val response = client.http.post(client.urlFor("/activities/$activityId/register")) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, "application/json")
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(RegisterActivityRequest(answers))
            }
            client.ensureSuccess(response)
            response.body<DataEnvelope<ActivityDto>>().data
        }

    @Throws(Exception::class)
    suspend fun cancel(activityId: Long): ActivityDto = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.post(client.urlFor("/activities/$activityId/cancel")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<ActivityDto>>().data
    }
}
