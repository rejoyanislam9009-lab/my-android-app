from pathlib import Path
import base64
import gzip

DATA_B64 = "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"

DATA = gzip.decompress(base64.b64decode(DATA_B64)).decode('utf-8')

TEMPLATE = r'''package com.guide.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/** Guide v3.32 large offline vocabulary bank for everyday Saudi life. */
class SaudiMegaVocabularyActivity : AppCompatActivity() {
    private data class Entry(val category: String, val arabic: String, val latin: String, val bangla: String, val english: String)

    private lateinit var listBox: LinearLayout
    private lateinit var categoryBox: LinearLayout
    private lateinit var resultText: TextView
    private var category = "সব"
    private var query = ""
    private var visibleLimit = 60
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val entries: List<Entry> by lazy {
        RAW_DATA.trim().lineSequence().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size >= 5) Entry(p[0], p[1], p[2], p[3], p[4]) else null
        }.toList()
    }
    private val categories by lazy { listOf("সব") + entries.map { it.category }.distinct() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("ar", "SA")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        setContentView(buildScreen())
        renderCategories()
        renderEntries()
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); tts = null; super.onDestroy() }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = gradient("#07101E", "#101A31") }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(16), dp(10)); background = gradient("#0E1A35", "#0B1630"); elevation = dp(8).toFloat()
        }
        top.addView(button("‹", "#203252", 20f) { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        labels.addView(text("SAUDI ARABIC MEGA", 9.8f, "#7F92BC", true).apply { letterSpacing = 0.11f })
        labels.addView(text("বড় শব্দভান্ডার", 19f, "#FFFFFF", true))
        top.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("ع+", 21f, "#FFFFFF", true).apply { gravity = Gravity.CENTER; background = stroke("#225E50", "#62D5AE", 18) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(34)) }
        val hero = card("#173B37", "#58D0A8")
        hero.addView(text("330+ Saudi daily words & phrases", 20f, "#FFFFFF", true))
        hero.addView(text("Arabic • বাংলা উচ্চারণ • বাংলা অর্থ • English • 🔊 শুনুন", 11.5f, "#81DDBB", true).apply { setPadding(0,dp(4),0,dp(5)) })
        hero.addView(text("মূল 137 phrase, Conversation ও Regional section-এর বাইরে আরও বড় vocabulary bank। Saudi speech শহর/বক্তাভেদে বদলাতে পারে—এখানে দৈনন্দিন বোঝাপড়ায় সবচেয়ে কাজে লাগে এমন common form রাখা হয়েছে।", 10.8f, "#B8C6DC"))
        content.addView(hero); content.addView(space(13))

        val search = EditText(this).apply {
            hint = "Arabic / বাংলা / English / pronunciation খুঁজুন"
            setHintTextColor(Color.parseColor("#7282A5")); setTextColor(Color.WHITE); textSize = 13.5f; setSingleLine(true)
            setPadding(dp(14),0,dp(14),0); background = stroke("#121D35", "#405781", 16)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s?.toString()?.trim().orEmpty(); visibleLimit = 60; renderEntries() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        content.addView(space(10))
        categoryBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
        content.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(categoryBox) })
        content.addView(space(8))
        resultText = text("", 11f, "#8D9EC0", true)
        content.addView(resultText); content.addView(space(7))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox)
        root.addView(ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        return root
    }

    private fun renderCategories() {
        categoryBox.removeAllViews()
        categories.forEachIndexed { index, name ->
            categoryBox.addView(button(name, if (category == name) "#5B50C8" else "#22314F", 10.7f) {
                category = name; visibleLimit = 60; renderCategories(); renderEntries()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(41)).apply { if (index > 0) marginStart = dp(6) })
        }
    }

    private fun filtered(): List<Entry> {
        val q = query.lowercase(Locale.getDefault())
        return entries.filter { e ->
            val cat = category == "সব" || e.category == category
            val search = q.isBlank() || listOf(e.arabic, e.latin, SaudiBanglaPronunciation.fromLatin(e.latin), e.bangla, e.english)
                .any { it.lowercase(Locale.getDefault()).contains(q) }
            cat && search
        }
    }

    private fun renderEntries() {
        if (!::listBox.isInitialized) return
        val all = filtered()
        val shown = all.take(visibleLimit)
        resultText.text = "${all.size}টি পাওয়া গেছে • মোট ${entries.size}টি Mega item"
        listBox.removeAllViews()
        shown.forEachIndexed { i, e ->
            val c = card("#17223B", "#40567D")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(e.category, 9.5f, "#AAB6D2", true).apply { setPadding(dp(8),dp(3),dp(8),dp(3)); background = stroke("#273755", "#394D72", 11) })
            row.addView(Space(this), LinearLayout.LayoutParams(0,1,1f))
            row.addView(button("🔊", "#29536C", 12f) { speak(e.arabic) }, LinearLayout.LayoutParams(dp(50), dp(38)))
            c.addView(row)
            c.addView(text(e.arabic, 25f, "#FFFFFF", true).apply { gravity = Gravity.END; textDirection = View.TEXT_DIRECTION_RTL; setPadding(0,dp(7),0,dp(5)) })
            c.addView(label("বাংলা উচ্চারণ", SaudiBanglaPronunciation.fromLatin(e.latin), "#72D9B5"))
            c.addView(label("English pronunciation", e.latin, "#93AEF4"))
            c.addView(label("বাংলা অর্থ", e.bangla, "#F2C47A"))
            c.addView(label("English", e.english, "#A8BCF3"))
            listBox.addView(c); if (i < shown.lastIndex) listBox.addView(space(8))
        }
        if (shown.size < all.size) {
            listBox.addView(space(10))
            listBox.addView(button("আরও ${minOf(60, all.size - shown.size)}টি দেখুন", "#3D4A78", 11.5f) { visibleLimit += 60; renderEntries() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        }
        if (all.isEmpty()) {
            val empty = card("#18223A", "#3D5279")
            empty.addView(text("কিছু পাওয়া যায়নি", 17f, "#FFFFFF", true))
            empty.addView(text("অন্য শব্দ লিখুন বা category পরিবর্তন করুন।", 10.8f, "#8D9CBD").apply { setPadding(0,dp(4),0,0) })
            listBox.addView(empty)
        }
    }

    private fun speak(value: String) {
        if (!ttsReady) { GuideUiFeedback.info(this, "ফোনে Arabic (Saudi Arabia) Text-to-Speech voice ইনস্টল করলে উচ্চারণ শুনতে পারবেন।", "Arabic voice"); return }
        tts?.language = Locale("ar","SA"); tts?.setSpeechRate(0.82f); tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "guide-mega-${System.currentTimeMillis()}")
    }

    private fun label(name: String, value: String, color: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0,dp(3),0,dp(3))
        addView(text(name,9.4f,"#7585A9",true)); addView(text(value,13f,color,true).apply { setPadding(0,dp(2),0,0) })
    }
    private fun card(bg: String, border: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14)); background = stroke(bg,border,20); elevation = dp(4).toFloat() }
    private fun text(v:String,size:Float,color:String,bold:Boolean=false)=TextView(this).apply { text=v; textSize=size; setTextColor(Color.parseColor(color)); if(bold)setTypeface(typeface,Typeface.BOLD); includeFontPadding=false }
    private fun button(v:String,bg:String,size:Float,action:()->Unit)=Button(this).apply { text=v; isAllCaps=false; textSize=size; setTextColor(Color.WHITE); setTypeface(typeface,Typeface.BOLD); background=stroke(bg,lighten(bg),14); setOnClickListener{action()} }
    private fun space(h:Int)=Space(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun gradient(a:String,b:String)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b)))
    private fun stroke(bg:String,border:String,radius:Int)=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=dp(radius).toFloat(); setColor(Color.parseColor(bg)); setStroke(dp(1),Color.parseColor(border)) }
    private fun lighten(hex:String):String { val c=Color.parseColor(hex); fun f(v:Int)=(v+(255-v)*0.18f).toInt().coerceIn(0,255); return String.format("#%02X%02X%02X",f(Color.red(c)),f(Color.green(c)),f(Color.blue(c))) }

    companion object {
        private const val RAW_DATA = """__DATA__"""
    }
}
'''

source = TEMPLATE.replace('__DATA__', DATA)
out = Path('app/src/main/java/com/guide/app/SaudiMegaVocabularyActivity.kt')
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(source)
print(f'v3.32 SaudiMegaVocabularyActivity generated with {len(DATA.splitlines())} items')
