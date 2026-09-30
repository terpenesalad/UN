package app.unreel.filter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.unreel.data.Prefs

/** Restarts the adult content filter after a reboot if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(context)
        if (Prefs.dnsFilter && VpnService.prepare(context) == null) {
            try {
                DnsFilterService.start(context)
            } catch (e: Exception) {
                // Background start refused; the user can re-enable it from the app.
            }
        }
    }
}
