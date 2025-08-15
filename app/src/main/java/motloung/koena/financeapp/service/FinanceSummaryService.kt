/*package motloung.koena.financeapp.service

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import motloung.koena.financeapp.data.AppDb

/**
 * Bound service that returns a summary of FinanceApp events via Messenger IPC.
 * Protected by permission + runtime caller validation.
 */
class FinanceSummaryService : Service() {

    companion object {
        const val ACTION = "motloung.koena.financeapp.ACTION_SUMMARY_SERVICE"
        const val MSG_GET_SUMMARY = 1
        const val KEY_SUMMARY = "summary"

        // Allow-list of trusted caller packages (add more if needed)
        private val TRUSTED_PACKAGES = setOf("motloung.koena.analyticsapp")
        private const val TAG = "FinanceSummaryService"
    }

    // Handler that processes incoming messages
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_GET_SUMMARY -> {
                    val callingUid = Binder.getCallingUid()
                    if (!isTrustedCaller(callingUid)) {
                        Log.w(TAG, "Rejected caller UID=$callingUid")
                        return
                    }

                    val replyTo = msg.replyTo ?: return

                    // Build summary off main thread
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = AppDb.get(applicationContext).eventDao()
                        val list = dao.all()               // Flow, so we need one-shot query:
                        // one-shot fetch (simple way): create a DAO method for count/last
                        // For brevity, do quick queries here:
                        val events = AppDb.get(applicationContext).eventDao().allOnce()
                        val count = events.size
                        val last = events.firstOrNull()?.payload ?: "No last message"

                        val summary = "Finance summary from Service\n" +
                                "Total events: $count\n" +
                                "Last: $last"

                        val data = Bundle().apply { putString(KEY_SUMMARY, summary) }
                        val reply = Message.obtain(null, MSG_GET_SUMMARY).apply { this.data = data }

                        try {
                            replyTo.send(reply)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to reply", e)
                        }
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    // Incoming endpoint
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent): IBinder? {
        // Extra defense: only accept our action (explicit intent)
        if (intent.action != ACTION) return null
        return messenger.binder
    }

    private fun isTrustedCaller(uid: Int): Boolean {
        val pm = packageManager
        val pkgs = pm.getPackagesForUid(uid) ?: return false
        return pkgs.any { it in TRUSTED_PACKAGES }
    }
}
*/

package motloung.koena.financeapp.service

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import motloung.koena.financeapp.data.AppDb

class FinanceSummaryService : Service() {

    companion object {
        const val ACTION = "motloung.koena.financeapp.ACTION_SUMMARY_SERVICE"
        const val MSG_GET_SUMMARY = 1
        const val KEY_SUMMARY = "summary"

        private val TRUSTED_PACKAGES = setOf("motloung.koena.analyticsapp")
        private const val TAG = "FinanceSummaryService"
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_GET_SUMMARY -> {
                    val callingUid = if (Build.VERSION.SDK_INT >= 29) msg.sendingUid else Binder.getCallingUid()
                    Log.d(TAG, "MSG_GET_SUMMARY from uid=$callingUid")

                    if (!isTrustedCaller(callingUid)) {
                        Log.w(TAG, "Rejected caller uid=$callingUid (not trusted)")
                        // Optional: tell the client we denied access
                        msg.replyTo?.let { r ->
                            val denial = Message.obtain(null, MSG_GET_SUMMARY).apply {
                                data = Bundle().apply { putString(KEY_SUMMARY, "Access denied") }
                            }
                            try { r.send(denial) } catch (_: Exception) {}
                        }
                        return
                    }

                    val replyTo = msg.replyTo ?: run {
                        Log.w(TAG, "No replyTo messenger; cannot respond")
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = AppDb.get(applicationContext).eventDao()
                            val events = dao.allOnce() // ensure this DAO method exists (see step 4)
                            val count = events.size
                            val last = events.firstOrNull()?.payload ?: "No last message"

                            val summary = "Finance summary from Service\n" +
                                    "Total events: $count\n" +
                                    "Last: $last"

                            val data = Bundle().apply { putString(KEY_SUMMARY, summary) }
                            val reply = Message.obtain(null, MSG_GET_SUMMARY).apply { this.data = data }

                            try {
                                replyTo.send(reply)
                                Log.d(TAG, "Reply sent")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to reply", e)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "DB error building summary", e)
                            val err = Message.obtain(null, MSG_GET_SUMMARY).apply {
                                data = Bundle().apply { putString(KEY_SUMMARY, "Error building summary") }
                            }
                            try { replyTo.send(err) } catch (_: Exception) {}
                        }
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "onBind action=${intent.action}")
        if (intent.action != ACTION) {
            Log.w(TAG, "Reject onBind: wrong action ${intent.action}")
            return null
        }
        return messenger.binder
    }

    private fun isTrustedCaller(uid: Int): Boolean {
        val pkgs = packageManager.getPackagesForUid(uid) ?: return false
        Log.d(TAG, "Caller packages: ${pkgs.joinToString()}")
        return pkgs.any { it in TRUSTED_PACKAGES }
    }
}
