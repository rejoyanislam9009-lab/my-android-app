package com.guide.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PdfReportsActivity : AppCompatActivity() {
    private lateinit var store: GuideStore
    private var pendingType = "attendance"

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching { GuidePdfReport.write(this, store, pendingType, uri) }
            .onSuccess { Toast.makeText(this, "PDF সেভ হয়েছে", Toast.LENGTH_LONG).show() }
            .onFailure { Toast.makeText(this, "PDF তৈরি করা যায়নি", Toast.LENGTH_LONG).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuideStore(this)
        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(34))
            background = gradient("#08101F", "#111A35")
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(button("‹", "#1B294A") { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        title.addView(text("PDF রিপোর্ট", 25f, Color.WHITE, true))
        title.addView(text("NEW • সুন্দর রিপোর্ট ডাউনলোড করুন", 12f, Color.parseColor("#8D9BC1")))
        top.addView(title)
        root.addView(top)
        root.addView(space(22))

        root.addView(reportCard("হাজিরা রিপোর্ট", "তারিখ, সময়, উপস্থিত/অনুপস্থিত/ছুটি এবং মাসিক সারাংশ।", "#267B68") { export("attendance", "Guide-Hajira") })
        root.addView(space(12))
        root.addView(reportCard("হিসাব রিপোর্ট", "আয়, ব্যয়, ক্যাটাগরি, নোট এবং মাসিক ব্যালেন্স।", "#5368D8") { export("accounts", "Guide-Hisab") })
        root.addView(space(12))
        root.addView(reportCard("রুটিন রিপোর্ট", "দৈনিক রুটিন, সময়, ক্যাটাগরি এবং alarm status।", "#7B55D7") { export("routines", "Guide-Routine") })
        root.addView(space(12))
        root.addView(reportCard("সম্পূর্ণ Guide রিপোর্ট", "হাজিরা + হিসাব + রুটিন একসাথে একটি PDF-এ।", "#A16B32") { export("all", "Guide-Full-Report") })

        root.addView(space(18))
        val note = card()
        note.addView(text("রিপোর্টে PIN বা কোনো password রাখা হয় না।", 13f, Color.parseColor("#9AA9CF")))
        note.addView(text("PDF তৈরি হওয়ার সময় আপনার ফোনেই তৈরি হয়; কোনো server-এ পাঠানো হয় না।", 13f, Color.parseColor("#7485AE")).apply { setPadding(0, dp(5), 0, 0) })
        root.addView(note)
        return ScrollView(this).apply { isFillViewport = true; addView(root) }
    }

    private fun export(type: String, prefix: String) {
        pendingType = type
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        createPdf.launch("$prefix-$stamp.pdf")
    }

    private fun reportCard(title: String, subtitle: String, accent: String, action: () -> Unit): LinearLayout {
        val c = card()
        c.addView(text(title, 18f, Color.WHITE, true))
        c.addView(text(subtitle, 13f, Color.parseColor("#94A2C7")).apply { setPadding(0, dp(6), 0, dp(13)) })
        c.addView(button("PDF ডাউনলোড", accent, action))
        return c
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded("#17213E", 20)
    }

    private fun button(label: String, bg: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        background = rounded(bg, 14); setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(hex: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex)); cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun space(height: Int) = android.widget.Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

object GuidePdfReport {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val LEFT = 42f
    private const val RIGHT = 553f
    private const val TOP = 58f
    private const val BOTTOM = 790f

    fun write(context: android.content.Context, store: GuideStore, type: String, uri: Uri) {
        val doc = PdfDocument()
        val writer = PdfWriter(doc, store.profileName())
        when (type) {
            "attendance" -> writeAttendance(writer, store)
            "accounts" -> writeAccounts(writer, store, context)
            "routines" -> writeRoutines(writer, store)
            else -> {
                writeAttendance(writer, store)
                writer.sectionBreak()
                writeAccounts(writer, store, context)
                writer.sectionBreak()
                writeRoutines(writer, store)
            }
        }
        writer.finish()
        context.contentResolver.openOutputStream(uri, "w")?.use { out -> doc.writeTo(out) }
            ?: error("Unable to open PDF destination")
        doc.close()
    }

    private fun writeAttendance(w: PdfWriter, store: GuideStore) {
        val summary = store.attendanceSummaryForCurrentMonth()
        w.heading("হাজিরা রিপোর্ট")
        w.info("এই মাস: উপস্থিত ${summary["Present"] ?: 0} • অনুপস্থিত ${summary["Absent"] ?: 0} • ছুটি ${summary["Leave"] ?: 0}")
        w.rule()
        val records = store.markedAttendanceHistory(365)
        if (records.isEmpty()) w.info("কোনো হাজিরা রেকর্ড নেই।")
        records.forEach { r ->
            val status = when (r.status) { "Present" -> "উপস্থিত"; "Absent" -> "অনুপস্থিত"; "Leave" -> "ছুটি"; else -> r.status }
            w.row(r.date, "$status${if (r.time.isNotBlank()) " • ${r.time}" else ""}")
        }
    }

    private fun writeAccounts(w: PdfWriter, store: GuideStore, context: android.content.Context) {
        val currency = context.getSharedPreferences("guide_ui", android.content.Context.MODE_PRIVATE).getString("currency", "SAR") ?: "SAR"
        val summary = store.currentMonthMoneySummary()
        w.heading("হিসাব রিপোর্ট")
        w.info("আয় $currency ${money(summary.first)} • ব্যয় $currency ${money(summary.second)} • ব্যালেন্স $currency ${money(summary.third)}")
        w.rule()
        val records = store.moneyRecords()
        if (records.isEmpty()) w.info("কোনো হিসাব রেকর্ড নেই।")
        records.forEach { r ->
            val type = if (r.type == "Income") "আয়" else "ব্যয়"
            val details = "$type • ${r.category} • $currency ${money(r.amount)}${if (r.note.isNotBlank()) " • ${r.note}" else ""}"
            w.row(r.date, details)
        }
    }

    private fun writeRoutines(w: PdfWriter, store: GuideStore) {
        w.heading("রুটিন রিপোর্ট")
        w.info("মোট রুটিন: ${store.routines().size}")
        w.rule()
        val items = store.routines().sortedWith(compareBy<RoutineItem> { it.hour }.thenBy { it.minute })
        if (items.isEmpty()) w.info("কোনো রুটিন নেই।")
        items.forEach { r ->
            val time = LocalTime.of(r.hour, r.minute).format(DateTimeFormatter.ofPattern("hh:mm a"))
            w.row(time, "${r.title} • ${r.category} • ${if (r.alarmEnabled) "অ্যালার্ম চালু" else "অ্যালার্ম বন্ধ"}")
        }
    }

    private fun money(value: Double) = String.format(Locale.US, "%.2f", value)

    private class PdfWriter(private val doc: PdfDocument, private val profileName: String) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = TOP
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 49, 69); textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(105, 116, 145); textSize = 9f; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 27, 49); textSize = 22f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val section = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 39, 70); textSize = 16f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 66, 98); textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        init { newPage() }

        fun heading(text: String) {
            ensure(52f)
            canvas?.drawText(text, LEFT, y, section)
            y += 25f
        }

        fun info(text: String) {
            ensure(24f)
            drawWrapped(text, body, RIGHT - LEFT)
            y += 6f
        }

        fun rule() {
            ensure(20f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(224, 228, 238); strokeWidth = 1f }
            canvas?.drawLine(LEFT, y, RIGHT, y, p)
            y += 16f
        }

        fun row(left: String, right: String) {
            ensure(38f)
            val c = canvas ?: return
            c.drawText(left, LEFT, y, label)
            drawWrapped(right, body, 390f, LEFT + 115f)
            y += 11f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(239, 241, 247); strokeWidth = 1f }
            c.drawLine(LEFT, y, RIGHT, y, p)
            y += 13f
        }

        fun sectionBreak() {
            ensure(34f)
            y += 20f
            rule()
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create()
            page = doc.startPage(info)
            canvas = page?.canvas
            y = TOP
            drawHeader()
        }

        private fun drawHeader() {
            val c = canvas ?: return
            c.drawText("GUIDE", LEFT, 34f, title)
            c.drawText("$profileName • ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy • hh:mm a"))}", LEFT + 96f, 31f, small)
            c.drawText("পৃষ্ঠা $pageNumber", RIGHT - 42f, 31f, small)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(105, 84, 245); strokeWidth = 3f }
            c.drawLine(LEFT, 43f, RIGHT, 43f, p)
        }

        private fun ensure(height: Float) {
            if (y + height > BOTTOM) newPage()
        }

        private fun drawWrapped(text: String, paint: Paint, maxWidth: Float, x: Float = LEFT) {
            val c = canvas ?: return
            val words = text.split(" ")
            var line = ""
            for (word in words) {
                val test = if (line.isBlank()) word else "$line $word"
                if (paint.measureText(test) > maxWidth && line.isNotBlank()) {
                    c.drawText(line, x, y, paint)
                    y += paint.textSize + 4f
                    line = word
                    ensure(paint.textSize + 8f)
                } else line = test
            }
            if (line.isNotBlank()) {
                c.drawText(line, x, y, paint)
                y += paint.textSize + 4f
            }
        }
    }
}
