package my.silentmode.pentana.shared

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.silentmode.pentana.shared.model.NotificationsPageDto

class NotificationsRepository(private val client: ApiClient) {

    @Throws(Exception::class)
    suspend fun notifications(): NotificationsPageDto = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.get(client.urlFor("/notifications")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        response.body<NotificationsPageDto>()
    }

    /** Marks all the member's notifications read; returns the remaining unread count (0). */
    @Throws(Exception::class)
    suspend fun markAllRead(): Int = withContext(Dispatchers.Main) {
        val token = client.tokenStore.get()
        val response = client.http.post(client.urlFor("/notifications/read")) {
            header(HttpHeaders.Accept, "application/json")
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }
        client.ensureSuccess(response)
        // The response is {"unread_count":0}; NotificationsPageDto.data defaults to empty.
        response.body<NotificationsPageDto>().unreadCount
    }
}
