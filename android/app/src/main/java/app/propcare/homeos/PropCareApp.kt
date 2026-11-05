package app.propcare.homeos

import android.app.Application
import app.propcare.homeos.work.ReminderWorker

class PropCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderWorker.ensureChannel(this)
        ReminderWorker.schedule(this)
    }
}
