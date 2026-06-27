package my.silentmode.pentana.core

import android.content.Context
import my.silentmode.pentana.shared.ActivitiesRepository
import my.silentmode.pentana.shared.ApiClient
import my.silentmode.pentana.shared.AuthRepository
import my.silentmode.pentana.shared.BillsRepository
import my.silentmode.pentana.shared.DashboardRepository
import my.silentmode.pentana.shared.LunchRepository
import my.silentmode.pentana.shared.NotificationsRepository

/** Manual DI graph — built once in [my.silentmode.pentana.App]. Holds the six repos the UI uses. */
class AppContainer(context: Context) {
    private val client = ApiClient(AppConfig.BASE_URL, PrefsTokenStore(context.applicationContext), engine = null)

    val auth = AuthRepository(client)
    val dashboard = DashboardRepository(client)
    val bills = BillsRepository(client)
    val lunch = LunchRepository(client)
    val activities = ActivitiesRepository(client)
    val notifications = NotificationsRepository(client)
}
