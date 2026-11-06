package app.shomar

import android.app.Application

class ShomarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.syncScheduler.schedulePeriodic()
        container.syncScheduler.requestSync()
    }
}