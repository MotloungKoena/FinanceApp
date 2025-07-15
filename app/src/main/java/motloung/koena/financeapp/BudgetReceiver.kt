package motloung.koena.financeapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class BudgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message")
        Toast.makeText(context, "FinanceApp Received: $message", Toast.LENGTH_LONG).show()
        Log.d("FinanceAppReceiver", "Received: $message")
    }
}
