package my.silentmode.pentana.shared

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppUpdateRepositoryTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val releaseJson =
        """{"version_name":"1.0.2","version_code":3,"size":12993559,"sha256":"abc","notes":"Faster login","download_url":"https://example.test/android/download","published_at":"2026-10-19T14:25:46+08:00"}"""

    private fun repository(body: String, status: HttpStatusCode = HttpStatusCode.OK): AppUpdateRepository {
        val engine = MockEngine { request ->
            assertEquals("https://example.test/api/v1/android/latest", request.url.toString())
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return AppUpdateRepository(ApiClient("https://example.test/api/v1", InMemoryTokenStore(), engine))
    }

    @Test
    fun latestReleaseIsParsed() = runTest {
        val release = repository(releaseJson).latestAndroidRelease()!!

        assertEquals("1.0.2", release.versionName)
        assertEquals(3, release.versionCode)
        assertEquals(12993559L, release.size)
        assertEquals("Faster login", release.notes)
        assertEquals("https://example.test/android/download", release.downloadUrl)
    }

    @Test
    fun noPublishedReleaseMeansNoUpdate() = runTest {
        val updateRepository = repository("""{"message":"Not Found"}""", HttpStatusCode.NotFound)

        assertNull(updateRepository.latestAndroidRelease())
        assertNull(updateRepository.availableUpdate(installedVersionCode = 1))
    }

    @Test
    fun aHigherVersionCodeIsOffered() = runTest {
        assertEquals(3, repository(releaseJson).availableUpdate(installedVersionCode = 2)?.versionCode)
    }

    @Test
    fun theSameOrAnOlderVersionCodeIsNotOffered() = runTest {
        assertNull(repository(releaseJson).availableUpdate(installedVersionCode = 3))
        assertNull(repository(releaseJson).availableUpdate(installedVersionCode = 4))
    }

    @Test
    fun serverErrorsSurfaceAsApiException() = runTest {
        val failure = assertFailsWith<ApiException> {
            repository("""{"message":"Server Error"}""", HttpStatusCode.InternalServerError).latestAndroidRelease()
        }
        assertEquals(500, failure.status)
    }
}
