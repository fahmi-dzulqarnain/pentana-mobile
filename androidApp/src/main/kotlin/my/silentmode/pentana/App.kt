package my.silentmode.pentana

import android.app.Application
import my.silentmode.pentana.core.AppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
