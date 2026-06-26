package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.DashboardDto
import my.silentmode.pentana.shared.model.DataEnvelope

class DashboardRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun dashboard(): DashboardDto = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.get(client.urlFor("/dashboard")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<DataEnvelope<DashboardDto>>().data
    }
}
