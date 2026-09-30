package app.unreel

import android.app.Application
import app.unreel.data.Prefs
import app.unreel.data.UsageStore

class UnreelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        UsageStore.init(this)
    }
}
