package motloung.koena.financeapp

import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openAnalyticsBtn = findViewById<Button>(R.id.btnOpenAnalytics)

        openAnalyticsBtn.setOnClickListener {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.example.analyticsapp",
                    "com.example.analyticsapp.AnalyticsActivity"
                )
                putExtra("summary", "Monthly Report: R2,000 saved")
            }
            startActivity(intent);
        }
    }
}