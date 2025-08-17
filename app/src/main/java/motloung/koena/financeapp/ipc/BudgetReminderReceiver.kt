package motloung.koena.financeapp.ipc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import motloung.koena.financeapp.data.AppDb
import motloung.koena.financeapp.data.Event
import motloung.koena.financeapp.util.Notifications

class BudgetReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.ACTION_BUDGET_REMINDER") return
        val msg = intent.getStringExtra("message") ?: return

        CoroutineScope(Dispatchers.IO).launch {
            AppDb.get(context).eventDao().insert(Event(type = "BUDGET_REMINDER", payload = msg))
        }

        val canNotify = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (canNotify) {
            Notifications.notify(context, "Budget Reminder", msg)
        }
    }
}
