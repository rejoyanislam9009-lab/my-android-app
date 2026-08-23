package com.guide.app

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Guide Smart হিসাব.
 * Paste a free-form expense note, automatically read the amount from each line,
 * calculate the total, divide it between people, and export/share a PDF.
 */
class SmartExpenseActivity : AppCompatActivity() {

    private data class ParsedExpense(val sourceLine: Int, val raw: String, val amount: Double)

    private val prefs by lazy { getSharedPreferences("guide_smart_expense_v338", MODE_PRIVATE) }
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var pendingCalculate: Runnable? = null

    private lateinit var titleInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var peopleInput: EditText
    private lateinit var currencySpinner: Spinner
    private lateinit var resultList: LinearLayout
    private lateinit var totalText: TextView
    private lateinit var shareText: TextView
    private lateinit var countText: TextView
    private lateinit var parseStatus: TextView

    private var parsedExpenses: List<ParsedExpense> = emptyList()
    private var unmatchedLines: List<Pair<Int, String>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        restoreDraft()
        bindAutoCalculate()
        calculate(false)
    }

    override fun onDestroy() {
        pendingCalculate?.let(debounceHandler::removeCallbacks)
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#07101D", "#0D1730")
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val compact = resources.configuration.screenWidthDp <= 360
            setPadding(dp(if (compact) 10 else 14), dp(9), dp(if (compact) 11 else 15), dp(9))
            background = gradient("#0D1B35", "#0A1530")
            elevation = dp(9).toFloat()
        }
        top.addView(button("‹", "#203252", 22f) { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(6), 0) }
        labels.addView(text("SMART EXPENSE", 9.8f, "#7F96C0", true).apply { letterSpacing = 0.11f })
        labels.addView(text("নোট লিখে মোট হিসাব", if (resources.configuration.screenWidthDp <= 360) 17f else 19f, "#FFFFFF", true))
        top.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("Σ", 24f, "#FFFFFF", true).apply {
            gravity = Gravity.CENTER
            background = gradientStroke("#276457", "#68D4AE", 1, 18)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        outer.addView(top)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val side = dp(if (resources.configuration.screenWidthDp <= 360) 12 else 17)
            setPadding(side, dp(14), side, dp(30))
        }

        body.addView(heroCard())
        body.addView(space(13))

        titleInput = styledInput("হিসাবের নাম — যেমন খাবার হিসাব", singleLine = true)
        body.addView(label("হিসাবের নাম"))
        body.addView(titleInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        body.addView(space(12))

        body.addView(label("হিসাবের নোট"))
        noteInput = styledInput(
            "উদাহরণ:\n10 tarikh - 4 riyal\n11 tarikh - 14 riyal\nপানি - 7\n13 তারিখ - 7 রিয়াল",
            singleLine = false
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            minLines = 7
            maxLines = 16
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        body.addView(noteInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        body.addView(text("প্রতি লাইনের শেষের amount ধরা হবে। ‘তারিখ’ বা tarikh-এর নম্বরকে টাকা হিসেবে ধরা হবে না যখন আলাদা amount আছে।", 10.3f, "#8192B5").apply { setPadding(dp(2), dp(6), dp(2), 0) })
        body.addView(space(12))

        val settingsCard = card("#14213A", "#365174")
        settingsCard.addView(label("কয় ভাগ হবে"))
        val settingsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        peopleInput = styledInput("কয় জন", singleLine = true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        settingsRow.addView(peopleInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        settingsRow.addView(hSpace(8))
        currencySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SmartExpenseActivity, android.R.layout.simple_spinner_dropdown_item, listOf("SAR • রিয়াল", "BDT • টাকা", "USD • Dollar"))
            background = gradientStroke("#182641", "#405B82", 1, 14)
            setPadding(dp(10), 0, dp(8), 0)
        }
        settingsRow.addView(currencySpinner, LinearLayout.LayoutParams(0, dp(50), 1f))
        settingsCard.addView(settingsRow)
        body.addView(settingsCard)
        body.addView(space(11))

        val calcRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        calcRow.addView(button("✦ হিসাব করুন", "#2B725E", 12f) { calculate(true) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        calcRow.addView(hSpace(8))
        calcRow.addView(button("⌫ Clear", "#5D3E50", 12f) { clearDraft() }, LinearLayout.LayoutParams(0, dp(50), 1f))
        body.addView(calcRow)
        body.addView(space(14))

        val summary = card("#152A31", "#3B8E73")
        summary.addView(text("হিসাবের সারাংশ", 13f, "#A8B9D7", true))
        totalText = text("মোট SAR 0.00", 27f, "#F2C47E", true).apply { setPadding(0, dp(6), 0, 0) }
        shareText = text("১ ভাগে • জনপ্রতি SAR 0.00", 15f, "#74D6B3", true).apply { setPadding(0, dp(5), 0, 0) }
        countText = text("0টি amount পাওয়া গেছে", 10.8f, "#8FA1C5").apply { setPadding(0, dp(5), 0, 0) }
        parseStatus = text("নোট লিখলেই হিসাব আপডেট হবে", 10.4f, "#7E91B6").apply { setPadding(0, dp(3), 0, 0) }
        summary.addView(totalText)
        summary.addView(shareText)
        summary.addView(countText)
        summary.addView(parseStatus)
        body.addView(summary)
        body.addView(space(13))

        val pdfRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        pdfRow.addView(button("📄 PDF বানান", "#405A91", 11.5f) { sharePdf(false) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        pdfRow.addView(hSpace(8))
        pdfRow.addView(button("↗ PDF Share", "#67518C", 11.5f) { sharePdf(true) }, LinearLayout.LayoutParams(0, dp(50), 1f))
        body.addView(pdfRow)
        body.addView(text("PDF-এ মূল নোট, ধরা amount, মোট টাকা, কয় ভাগ এবং জনপ্রতি কত—সব থাকবে।", 10.2f, "#8090B1").apply { setPadding(dp(2), dp(6), dp(2), 0) })
        body.addView(space(17))

        body.addView(text("যে amountগুলো ধরা হয়েছে", 13f, "#C5D0E7", true))
        body.addView(space(7))
        resultList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(resultList)

        outer.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(body)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(outer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(outer)
        return outer
    }

    private fun heroCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = gradientStroke("#26305B", "#7668F0", 1, 22)
        elevation = dp(4).toFloat()
        addView(text("🧮 Smart হিসাব", 21f, "#FFFFFF", true))
        addView(text("ছবির মতো একসাথে অনেক লাইনের খরচ লিখুন। Guide amount বের করে Total করবে, ইচ্ছামতো ভাগ করবে এবং PDF বানাবে।", 11.5f, "#B9C5E0").apply { setPadding(0, dp(5), 0, 0) })
        addView(text("Auto parse • Decimal support • বাংলা/English digit • PDF / Share", 10.3f, "#8EE0C2", true).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun bindAutoCalculate() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleCalculate()
                saveDraft()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }
        noteInput.addTextChangedListener(watcher)
        peopleInput.addTextChangedListener(watcher)
        titleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = saveDraft()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        currencySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveDraft()
                calculate(false)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun scheduleCalculate() {
        pendingCalculate?.let(debounceHandler::removeCallbacks)
        pendingCalculate = Runnable { calculate(false) }.also { debounceHandler.postDelayed(it, 260L) }
    }

    private fun calculate(showToast: Boolean) {
        val lines = noteInput.text.toString().lines()
        val parsed = mutableListOf<ParsedExpense>()
        val unmatched = mutableListOf<Pair<Int, String>>()
        lines.forEachIndexed { index, original ->
            val raw = original.trim()
            if (raw.isBlank()) return@forEachIndexed
            val amount = parseAmount(raw)
            if (amount != null) parsed.add(ParsedExpense(index + 1, raw, amount))
            else unmatched.add((index + 1) to raw)
        }
        parsedExpenses = parsed
        unmatchedLines = unmatched

        val total = parsed.sumOf { it.amount }
        val people = peopleCount()
        val share = if (people <= 0) total else total / people
        val currency = currencyCode()

        totalText.text = "মোট $currency ${formatMoney(total)}"
        shareText.text = "$people ভাগে • জনপ্রতি $currency ${formatMoney(share)}"
        countText.text = "${parsed.size}টি amount পাওয়া গেছে • ${unmatched.size}টি line ধরা যায়নি"
        parseStatus.text = when {
            lines.all { it.isBlank() } -> "নোট লিখলেই হিসাব আপডেট হবে"
            parsed.isEmpty() -> "⚠ কোনো amount পাওয়া যায়নি — amount-এর আগে/পরে - : = অথবা রিয়াল/SAR লিখুন"
            unmatched.isNotEmpty() -> "⚠ ${unmatched.size}টি line বাদ গেছে • নিচে Preview দেখে প্রয়োজন হলে নোট Edit করুন"
            else -> "✓ হিসাব প্রস্তুত • PDF / Share করা যাবে"
        }
        parseStatus.setTextColor(Color.parseColor(if (parsed.isNotEmpty() && unmatched.isEmpty()) "#76D7B5" else if (parsed.isEmpty()) "#F0A2A6" else "#E7C17F"))
        renderParsedList()
        saveDraft()
        if (showToast) {
            Toast.makeText(this, if (parsed.isEmpty()) "কোনো amount পাওয়া যায়নি" else "✓ মোট $currency ${formatMoney(total)} • জনপ্রতি $currency ${formatMoney(share)}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderParsedList() {
        resultList.removeAllViews()
        if (parsedExpenses.isEmpty() && unmatchedLines.isEmpty()) {
            resultList.addView(emptyCard("এখনও কোনো হিসাব নেই", "উপরে নোট লিখুন। যেমন: 13 তারিখ - 7 রিয়াল"))
            return
        }
        parsedExpenses.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = gradientStroke("#15233C", "#344F73", 1, 15)
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text("Line ${item.sourceLine}", 9.7f, "#7084AA", true))
            labels.addView(text(item.raw, 11.5f, "#D8E0F0", true).apply { setPadding(0, dp(2), 0, 0) })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("${currencyCode()} ${formatMoney(item.amount)}", 12.5f, "#75D8B5", true))
            resultList.addView(row)
            if (index < parsedExpenses.lastIndex || unmatchedLines.isNotEmpty()) resultList.addView(space(6))
        }
        unmatchedLines.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = gradientStroke("#31212C", "#78475B", 1, 14)
                addView(text("⚠ Line ${item.first} • amount পাওয়া যায়নি", 10.3f, "#F0A8AE", true))
                addView(text(item.second, 10.8f, "#C8B8C2").apply { setPadding(0, dp(2), 0, 0) })
            }
            resultList.addView(row)
            if (index < unmatchedLines.lastIndex) resultList.addView(space(6))
        }
    }

    private fun parseAmount(raw: String): Double? {
        val line = normalizeDigits(raw)
            .replace('٫', '.')
            .replace('٬', ',')
        val number = "(-?\\d+(?:[\\.,]\\d+)?)"
        val currencyRegex = Regex("$number\\s*(?:sar|s\\.?a\\.?r\\.?|riyal|rial|riyals|rials|রিয়াল|রিয়াল|রিয়াল|টাকা|৳|dollar|usd)", RegexOption.IGNORE_CASE)
        val explicit = currencyRegex.findAll(line).lastOrNull()?.groupValues?.getOrNull(1)?.toAmount()
        if (explicit != null) return explicit

        val separators = listOf(" - ", "-", "–", "—", ":", "=")
        val positions = separators.map { line.lastIndexOf(it) }.filter { it >= 0 }
        if (positions.isNotEmpty()) {
            val last = positions.maxOrNull() ?: -1
            val tail = line.substring((last + 1).coerceAtMost(line.length))
            val tailAmount = Regex(number).findAll(tail).lastOrNull()?.value?.toAmount()
            if (tailAmount != null) return tailAmount
        }

        val containsDateWord = Regex("(?:তারিখ|তারিক|tarikh|date)", RegexOption.IGNORE_CASE).containsMatchIn(line)
        val allNumbers = Regex(number).findAll(line).mapNotNull { it.value.toAmount() }.toList()
        if (containsDateWord && allNumbers.size <= 1) return null
        return allNumbers.lastOrNull()
    }

    private fun normalizeDigits(value: String): String {
        val bangla = "০১২৩৪৫৬৭৮৯"
        val arabicIndic = "٠١٢٣٤٥٦٧٨٩"
        val easternArabic = "۰۱۲۳۴۵۶۷۸۹"
        return buildString(value.length) {
            value.forEach { ch ->
                val b = bangla.indexOf(ch)
                val a = arabicIndic.indexOf(ch)
                val e = easternArabic.indexOf(ch)
                append(when {
                    b >= 0 -> ('0'.code + b).toChar()
                    a >= 0 -> ('0'.code + a).toChar()
                    e >= 0 -> ('0'.code + e).toChar()
                    else -> ch
                })
            }
        }
    }

    private fun String.toAmount(): Double? = replace(",", "").toDoubleOrNull()

    private fun peopleCount(): Int = peopleInput.text.toString().trim().toIntOrNull()?.coerceIn(1, 999) ?: 1

    private fun currencyCode(): String = when (currencySpinner.selectedItemPosition) {
        1 -> "BDT"
        2 -> "USD"
        else -> "SAR"
    }

    private fun sharePdf(shareNow: Boolean) {
        calculate(false)
        if (parsedExpenses.isEmpty()) {
            Toast.makeText(this, "PDF বানানোর জন্য আগে হিসাব লিখুন", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            val file = createPdfFile()
            if (!shareNow) {
                Toast.makeText(this, "✓ PDF তৈরি হয়েছে • Share বাটন থেকে পাঠাতে পারবেন", Toast.LENGTH_LONG).show()
                sharePdfFile(file)
            } else {
                sharePdfFile(file)
            }
        }.onFailure {
            Toast.makeText(this, "PDF তৈরি/শেয়ার করা যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    private fun createPdfFile(): File {
        val dir = File(cacheDir, "shared_reports").apply { mkdirs() }
        val safeTitle = titleInput.text.toString().trim().ifBlank { "Smart-Hisab" }
            .replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifBlank { "Smart-Hisab" }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        val file = File(dir, "Guide-$safeTitle-$stamp.pdf")

        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 42f
        val contentWidth = pageWidth - margin * 2
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 43, 72); textSize = 22f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(39, 101, 83); textSize = 14f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 55, 72); textSize = 11.5f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 104, 124); textSize = 9.5f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(142, 91, 35); textSize = 16f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun startPage() {
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page!!.canvas
            y = 48f
            canvas!!.drawText("GUIDE • SMART EXPENSE", margin, y, smallPaint)
            y += 28f
        }

        fun finishPage() {
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        fun ensure(height: Float) {
            if (page == null) startPage()
            if (y + height > pageHeight - 44f) {
                finishPage()
                startPage()
            }
        }

        fun wrap(textValue: String, paint: Paint): List<String> {
            val text = textValue.ifBlank { " " }
            val words = text.split(Regex("\\s+"))
            val lines = mutableListOf<String>()
            var current = ""
            words.forEach { word ->
                val test = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(test) <= contentWidth) current = test
                else {
                    if (current.isNotBlank()) lines.add(current)
                    current = word
                }
            }
            if (current.isNotBlank()) lines.add(current)
            return if (lines.isEmpty()) listOf(" ") else lines
        }

        fun drawWrapped(value: String, paint: Paint, extraAfter: Float = 4f) {
            val lines = wrap(value, paint)
            ensure(lines.size * (paint.textSize + 5f) + extraAfter)
            lines.forEach { line ->
                canvas!!.drawText(line, margin, y, paint)
                y += paint.textSize + 5f
            }
            y += extraAfter
        }

        startPage()
        drawWrapped(titleInput.text.toString().trim().ifBlank { "Smart হিসাব" }, titlePaint, 8f)
        drawWrapped("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy • hh:mm a", Locale.ENGLISH))}", smallPaint, 12f)
        drawWrapped("Parsed expense lines", headingPaint, 6f)
        parsedExpenses.forEachIndexed { index, item ->
            drawWrapped("${index + 1}. ${item.raw}  =  ${currencyCode()} ${formatMoney(item.amount)}", bodyPaint, 3f)
        }
        if (unmatchedLines.isNotEmpty()) {
            y += 6f
            drawWrapped("Lines not included in total", headingPaint, 5f)
            unmatchedLines.forEach { item -> drawWrapped("Line ${item.first}: ${item.second}", smallPaint, 2f) }
        }

        y += 10f
        ensure(100f)
        canvas!!.drawLine(margin, y, pageWidth - margin, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(198, 205, 217); strokeWidth = 1f })
        y += 22f
        val total = parsedExpenses.sumOf { it.amount }
        val people = peopleCount()
        val perPerson = total / people
        drawWrapped("TOTAL  ${currencyCode()} ${formatMoney(total)}", totalPaint, 5f)
        drawWrapped("Divide: $people person(s) • Per person ${currencyCode()} ${formatMoney(perPerson)}", headingPaint, 8f)
        drawWrapped("Guide Smart হিসাব • Original note is preserved in the app draft so you can edit and recalculate.", smallPaint, 0f)

        finishPage()
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun sharePdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val total = parsedExpenses.sumOf { it.amount }
        val people = peopleCount()
        val perPerson = total / people
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, titleInput.text.toString().trim().ifBlank { "Guide Smart হিসাব" })
            putExtra(Intent.EXTRA_TEXT, "মোট ${currencyCode()} ${formatMoney(total)} • $people ভাগে জনপ্রতি ${currencyCode()} ${formatMoney(perPerson)}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "হিসাব PDF শেয়ার করুন"))
    }

    private fun restoreDraft() {
        titleInput.setText(prefs.getString("title", "খাবার হিসাব") ?: "খাবার হিসাব")
        noteInput.setText(prefs.getString("note", "") ?: "")
        peopleInput.setText(prefs.getInt("people", 1).coerceAtLeast(1).toString())
        currencySpinner.setSelection(prefs.getInt("currency", 0).coerceIn(0, 2))
    }

    private fun saveDraft() {
        if (!::titleInput.isInitialized || !::noteInput.isInitialized || !::peopleInput.isInitialized || !::currencySpinner.isInitialized) return
        prefs.edit()
            .putString("title", titleInput.text.toString())
            .putString("note", noteInput.text.toString())
            .putInt("people", peopleCount())
            .putInt("currency", currencySpinner.selectedItemPosition.coerceIn(0, 2))
            .apply()
    }

    private fun clearDraft() {
        AlertDialog.Builder(this)
            .setTitle("Smart হিসাব Clear করবেন?")
            .setMessage("বর্তমান নোট ও হিসাবের draft মুছে নতুন করে শুরু হবে।")
            .setNegativeButton("বাতিল", null)
            .setPositiveButton("Clear") { _, _ ->
                prefs.edit().clear().apply()
                titleInput.setText("খাবার হিসাব")
                noteInput.setText("")
                peopleInput.setText("1")
                currencySpinner.setSelection(0)
                calculate(false)
            }
            .show()
    }

    private fun styledInput(hintValue: String, singleLine: Boolean): EditText = EditText(this).apply {
        hint = hintValue
        setHintTextColor(Color.parseColor("#7183A8"))
        setTextColor(Color.WHITE)
        textSize = 13f
        setSingleLine(singleLine)
        if (singleLine) setPadding(dp(14), 0, dp(14), 0)
        background = gradientStroke("#121D35", "#3B5277", 1, 15)
    }

    private fun label(value: String) = text(value, 10.8f, "#A1B1D0", true).apply { setPadding(dp(2), 0, 0, dp(6)) }

    private fun card(fill: String, stroke: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = gradientStroke(fill, stroke, 1, 18)
        elevation = dp(3).toFloat()
    }

    private fun emptyCard(title: String, subtitle: String): View = card("#121D32", "#2E4262").apply {
        addView(text(title, 13f, "#B9C5DD", true))
        addView(text(subtitle, 10.5f, "#7F90B3").apply { setPadding(0, dp(3), 0, 0) })
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun button(value: String, fill: String, size: Float, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = size
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = gradientStroke(fill, "#45FFFFFF", 1, 14)
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun gradient(start: String, end: String): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    )

    private fun gradientStroke(fill: String, stroke: String, width: Int, radiusDp: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(fill), Color.parseColor(fill))
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(width), Color.parseColor(stroke))
    }

    private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun hSpace(width: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
}
