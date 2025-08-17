package motloung.koena.financeapp.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import motloung.koena.financeapp.data.Event
import motloung.koena.financeapp.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()
    private val adapter = EventAdapter()

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        run {
            val allowedCaller = "motloung.koena.analyticsapp"
            val expectedAction = "motloung.koena.financeapp.VIEW_FINANCE"

            val fromExplicit = intent.component != null && intent.`package` == "motloung.koena.financeapp"
            val actionOk = intent.action == null || intent.action == expectedAction

            val ref = intent.`package` ?: callingActivity?.packageName ?: intent.getStringExtra("caller_pkg")
            val callerOk = (ref == null) || (ref == allowedCaller)

            if (!fromExplicit || !actionOk || !callerOk) {
                android.util.Log.w("FinanceMain", "Rejected launch: fromExplicit=$fromExplicit actionOk=$actionOk caller=$ref")
            }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = adapter

        lifecycleScope.launchWhenStarted {
            vm.events.collect { events ->
                adapter.submitList(events)
                if (events.isEmpty()) {
                    binding.txtEmpty.visibility = View.VISIBLE
                    binding.rv.visibility = View.GONE
                } else {
                    binding.txtEmpty.visibility = View.GONE
                    binding.rv.visibility = View.VISIBLE
                }
            }
        }

        binding.btnClear.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all finance events? This cannot be undone.")
                .setPositiveButton("Yes") { _, _ ->
                    lifecycleScope.launch { vm.clear() }
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnSendSummary.setOnClickListener {
            val count = adapter.currentList.size
            val last = adapter.currentList.firstOrNull()?.payload ?: "No last message"
            val summary = "FinanceApp summary\n\nTotal reminders: $count\nLast reminder: $last"

            val i = Intent().apply {
                setClassName(
                    "motloung.koena.analyticsapp",
                    "motloung.koena.analyticsapp.AnalyticsActivity"
                )
                action = "motloung.koena.analyticsapp.VIEW_ANALYTICS"
                putExtra("summary", summary)
                setPackage("motloung.koena.analyticsapp")
            }

            try {
                startActivity(i)
            } catch (_: Exception) {
                Toast.makeText(this, "AnalyticsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShare.setOnClickListener {
            val items = adapter.currentList
            if (items.isEmpty()) {
                Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val csv = toCsv(items)

            MaterialAlertDialogBuilder(this)
                .setTitle("Export as")
                .setItems(arrayOf("CSV", "PDF")) { _, which ->
                    when (which) {
                        0 -> {
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "Finance Export")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            startActivity(Intent.createChooser(i, "Share CSV"))
                        }
                        1 -> {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val file = generatePdf(this@MainActivity, csv)
                                withContext(Dispatchers.Main) {
                                    sharePdf(this@MainActivity, file)
                                }
                            }
                        }
                    }
                }
                .show()
        }
    }

    private fun toCsv(items: List<Event>): String {
        val sb = StringBuilder("id,type,payload,receivedAt\n")
        items.forEach { e ->
            val payloadEsc = e.payload.replace("\"", "\"\"")
            sb.append("${e.id},${e.type},\"$payloadEsc\",${e.receivedAt}\n")
        }
        return sb.toString()
    }

    private fun generatePdf(context: Context, csvText: String): File {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val headerGap = 12f
        val bodyTop = 120f
        val footerBottom = pageHeight - 24f

        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
        val metaPaint = Paint().apply {
            isAntiAlias = true
            textSize = 12f
        }
        val linePaint = Paint().apply { strokeWidth = 1f }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val footerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 10f
        }

        val lines = csvText.split("\n")
        val totalRows = (lines.size - 1).coerceAtLeast(0)
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

        val pdf = PdfDocument()

        var pageNum = 1
        var y = bodyTop
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        val canvas = page.canvas

        fun drawHeaderAndRules() {
            canvas.drawText("Finance Events Export", margin, 50f, titlePaint)
            canvas.drawText("Generated: $now", margin, 50f + headerGap + 12f, metaPaint)
            canvas.drawText("Total rows: $totalRows", margin, 50f + 2 * (headerGap + 12f), metaPaint)
            canvas.drawLine(margin, bodyTop - 10f, pageWidth - margin, bodyTop - 10f, linePaint)
        }

        fun drawFooter() {
            val text = "Page $pageNum"
            val textWidth = footerPaint.measureText(text)
            canvas.drawText(text, pageWidth - margin - textWidth, footerBottom, footerPaint)
        }

        drawHeaderAndRules()

        val lineHeight = bodyPaint.descent() - bodyPaint.ascent()
        lines.forEachIndexed { idx, raw ->
            val line = raw.trimEnd()
            if (line.isEmpty()) return@forEachIndexed

            if (y + lineHeight > footerBottom - 10f) {
                drawFooter()
                pdf.finishPage(page)
                pageNum++
                y = bodyTop
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                val c = page.canvas
                c.drawText("Finance Events Export", margin, 50f, titlePaint)
                c.drawText("Generated: $now", margin, 50f + headerGap + 12f, metaPaint)
                c.drawText("Total rows: $totalRows", margin, 50f + 2 * (headerGap + 12f), metaPaint)
                c.drawLine(margin, bodyTop - 10f, pageWidth - margin, bodyTop - 10f, linePaint)
            }

            page.canvas.drawText(line, margin, y, bodyPaint)
            y += lineHeight
        }

        drawFooter()
        pdf.finishPage(page)

        val outFile = File(context.getExternalFilesDir(null), "finance_events_${now.replace(" ", "_").replace(":", "")}.pdf")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()

        return outFile
    }


    private fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Share PDF"))
    }
}


