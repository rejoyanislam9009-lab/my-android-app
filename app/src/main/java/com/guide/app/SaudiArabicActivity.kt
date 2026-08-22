package com.guide.app

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Guide v3.30 Saudi Arabic learning center.
 *
 * The lesson library is intentionally bundled in the APK so browsing, learning,
 * favorites and quiz practice work without internet. Progress/favorites are kept
 * in guide_ui, which is already included in Guide's Firebase snapshot backup.
 */
class SaudiArabicActivity : AppCompatActivity() {
    private data class Phrase(
        val id: String,
        val category: String,
        val arabic: String,
        val latin: String,
        val bangla: String,
        val english: String,
        val note: String = ""
    )

    private val prefs by lazy { getSharedPreferences("guide_ui", MODE_PRIVATE) }
    private lateinit var phraseContainer: LinearLayout
    private lateinit var categoryContainer: LinearLayout
    private lateinit var progressText: TextView
    private lateinit var progressBarFill: View
    private lateinit var resultText: TextView
    private lateinit var searchInput: EditText
    private var selectedCategory = "সব"
    private var query = ""
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val phrases: List<Phrase> by lazy { buildPhrases() }
    private val categories by lazy {
        listOf("সব", "★ পছন্দ", "✓ শেখা", "অভিবাদন", "দৈনন্দিন", "কাজ", "কেনাকাটা", "যাতায়াত", "খাবার", "রুম/মেস", "জরুরি", "সংখ্যা/সময়")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("ar", "SA")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        setContentView(buildScreen())
        renderCategories()
        renderPhraseList()
        updateProgress()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#07101E", "#0E1830")
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(16), dp(10))
            background = gradient("#0E1A35", "#0B1630")
            elevation = dp(8).toFloat()
        }
        top.addView(button("‹", "#203252", 19f) { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val topLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        topLabels.addView(text("SAUDI ARABIC", 10.5f, "#7F92BC", true).apply { letterSpacing = 0.12f })
        topLabels.addView(text("আরবি ভাষা শেখা", 19f, "#FFFFFF", true))
        top.addView(topLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("ع", 25f, "#FFFFFF", true).apply {
            gravity = Gravity.CENTER
            background = gradientStroke("#21765E", "#6AD6AF", 1, 18)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        outer.addView(top)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(34))
        }

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            background = gradientStroke("#173B37", "#5DD1A8", 1, 24)
            elevation = dp(7).toFloat()
        }
        hero.addView(text("لهجة سعودية", 31f, "#FFFFFF", true).apply {
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_RTL
        })
        hero.addView(text("Saudi / Gulf কথ্য আরবি • বাংলা + English অর্থ", 14f, "#BCEADA", true).apply { setPadding(0, dp(4), 0, 0) })
        hero.addView(text("সৌদি আরবে দৈনন্দিন কথা, কাজ, বাজার, গাড়ি, রেস্টুরেন্ট ও Room/Mess-এ সবচেয়ে দরকারি শব্দ ও বাক্য। সব lesson offline-এও দেখা যাবে।", 12f, "#C4D4E8").apply { setPadding(0, dp(8), 0, dp(12)) })

        progressText = text("", 12.5f, "#FFFFFF", true)
        hero.addView(progressText)
        val progressTrack = LinearLayout(this).apply {
            gravity = Gravity.START
            setPadding(0, 0, 0, 0)
            background = rounded("#344F6470", 8)
        }
        progressBarFill = View(this).apply { background = rounded("#72D7B2", 8) }
        progressTrack.addView(progressBarFill, LinearLayout.LayoutParams(0, dp(8)))
        hero.addView(progressTrack, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(8) })
        content.addView(hero)
        content.addView(space(14))

        val daily = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = gradientStroke("#172641", "#6D66E8", 1, 19)
        }
        val dailyLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dailyLabels.addView(text("আজকের ৫টি Phrase", 15f, "#FFFFFF", true))
        dailyLabels.addView(text("প্রতিদিন ৫টি করে শিখলে দ্রুত progress হবে", 10.5f, "#94A4C8"))
        daily.addView(dailyLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        daily.addView(button("শুরু করুন", "#5547A8", 11.5f) { showDailyLesson() }, LinearLayout.LayoutParams(dp(104), dp(42)))
        content.addView(daily)
        content.addView(space(16))

        content.addView(text("খুঁজুন", 12f, "#98A7C9", true))
        searchInput = EditText(this).apply {
            hint = "Arabic / বাংলা / English / pronunciation"
            setHintTextColor(Color.parseColor("#7180A4"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(16), 0, dp(16), 0)
            background = gradientStroke("#111C35", "#405883", 1, 16)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString()?.trim().orEmpty()
                    renderPhraseList()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(searchInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        content.addView(space(13))

        content.addView(text("বিভাগ", 12f, "#98A7C9", true))
        categoryContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(7), 0, dp(7)) }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(categoryContainer)
        })
        content.addView(space(8))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("🎯 Quick Quiz", "#4E447B", 12f) { startQuiz() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(hSpace(8))
        actions.addView(button("★ Favorites", "#765A2D", 12f) {
            selectedCategory = "★ পছন্দ"; renderCategories(); renderPhraseList()
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        content.addView(actions)
        content.addView(space(15))

        resultText = text("", 11f, "#8392B5", true)
        content.addView(resultText)
        content.addView(space(7))

        phraseContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(phraseContainer)

        outer.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    private fun renderCategories() {
        categoryContainer.removeAllViews()
        categories.forEachIndexed { index, name ->
            val selected = selectedCategory == name
            categoryContainer.addView(button(name, if (selected) "#5B50C8" else "#22314F", 11f) {
                selectedCategory = name
                renderCategories()
                renderPhraseList()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                if (index > 0) marginStart = dp(7)
            })
        }
    }

    private fun renderPhraseList() {
        if (!::phraseContainer.isInitialized) return
        val learned = learnedIds()
        val favorites = favoriteIds()
        val q = query.lowercase(Locale.getDefault())
        val filtered = phrases.filter { phrase ->
            val categoryMatch = when (selectedCategory) {
                "সব" -> true
                "★ পছন্দ" -> phrase.id in favorites
                "✓ শেখা" -> phrase.id in learned
                else -> phrase.category == selectedCategory
            }
            val searchMatch = q.isBlank() || listOf(phrase.arabic, phrase.latin, phrase.bangla, phrase.english, phrase.note)
                .any { it.lowercase(Locale.getDefault()).contains(q) }
            categoryMatch && searchMatch
        }

        resultText.text = "${filtered.size}টি phrase • ${learned.size}/${phrases.size} শেখা হয়েছে"
        phraseContainer.removeAllViews()
        if (filtered.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(26), dp(18), dp(26))
                background = gradientStroke("#151F37", "#385071", 1, 20)
            }
            empty.addView(text("কোনো phrase পাওয়া যায়নি", 17f, "#FFFFFF", true).apply { gravity = Gravity.CENTER })
            empty.addView(text("Search বা category পরিবর্তন করে দেখুন।", 11.5f, "#8D9CBD").apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
            phraseContainer.addView(empty)
            return
        }

        filtered.forEachIndexed { index, phrase ->
            phraseContainer.addView(phraseCard(phrase, phrase.id in learned, phrase.id in favorites))
            if (index < filtered.lastIndex) phraseContainer.addView(space(9))
        }
    }

    private fun phraseCard(item: Phrase, isLearned: Boolean, isFavorite: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(14))
            background = gradientStroke(if (isLearned) "#15302D" else "#17223B", if (isLearned) "#55CFA7" else "#41567D", 1, 21)
            elevation = dp(4).toFloat()
        }

        val tagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tagRow.addView(text(item.category, 10f, "#AEB9D4", true).apply {
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = rounded("#263555", 12)
        })
        tagRow.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        tagRow.addView(text(if (isFavorite) "★" else "☆", 23f, if (isFavorite) "#F4C66E" else "#7D8DAF", true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { toggleFavorite(item.id) }
        }, LinearLayout.LayoutParams(dp(42), dp(38)))
        card.addView(tagRow)

        card.addView(text(item.arabic, 27f, "#FFFFFF", true).apply {
            gravity = Gravity.END
            textDirection = View.TEXT_DIRECTION_RTL
            setPadding(0, dp(7), 0, 0)
        })
        card.addView(text(item.latin, 12f, "#79D8B7", true).apply {
            gravity = Gravity.END
            setPadding(0, dp(3), 0, dp(9))
        })

        card.addView(labelValue("বাংলা অর্থ", item.bangla, "#F4C57B"))
        card.addView(labelValue("English", item.english, "#9DB8FF"))
        if (item.note.isNotBlank()) {
            card.addView(text("Saudi usage • ${item.note}", 10.5f, "#8797BA").apply { setPadding(0, dp(7), 0, 0) })
        }

        card.addView(space(11))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("🔊 শুনুন", "#2D5470", 11.5f) { speakArabic(item.arabic) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        row.addView(hSpace(7))
        row.addView(button(if (isLearned) "✓ শেখা হয়েছে" else "✓ শিখেছি", if (isLearned) "#24735D" else "#4E477B", 11.5f) {
            toggleLearned(item.id)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        card.addView(row)
        return card
    }

    private fun labelValue(label: String, value: String, accent: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        row.addView(text(label, 9.8f, "#7585A9", true).apply { letterSpacing = 0.06f })
        row.addView(text(value, 13.5f, accent, true).apply { setPadding(0, dp(2), 0, 0) })
        return row
    }

    private fun toggleLearned(id: String) {
        val set = learnedIds().toMutableSet()
        val added = if (id in set) { set.remove(id); false } else { set.add(id); true }
        prefs.edit().putStringSet(KEY_LEARNED, set).apply()
        CloudSyncManager.scheduleUpload(this)
        GuideUiFeedback.success(this, if (added) "Phrase-টি শেখা হিসেবে সেভ হয়েছে।" else "Phrase-টি শেখা তালিকা থেকে সরানো হয়েছে।", if (added) "Progress saved" else "Progress updated")
        updateProgress()
        renderPhraseList()
    }

    private fun toggleFavorite(id: String) {
        val set = favoriteIds().toMutableSet()
        val added = if (id in set) { set.remove(id); false } else { set.add(id); true }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
        CloudSyncManager.scheduleUpload(this)
        GuideUiFeedback.info(this, if (added) "Favorite-এ রাখা হয়েছে।" else "Favorite থেকে সরানো হয়েছে।", "Saudi Arabic")
        renderPhraseList()
    }

    private fun updateProgress() {
        if (!::progressText.isInitialized) return
        val count = learnedIds().size.coerceAtMost(phrases.size)
        val percent = if (phrases.isEmpty()) 0 else ((count.toDouble() / phrases.size) * 100.0).roundToInt()
        progressText.text = "Learning progress  $count / ${phrases.size}  •  $percent%"
        progressBarFill.post {
            val parent = progressBarFill.parent as? View ?: return@post
            val width = ((parent.width * percent) / 100f).roundToInt().coerceAtLeast(if (percent > 0) dp(4) else 0)
            progressBarFill.layoutParams = progressBarFill.layoutParams.apply { this.width = width }
        }
    }

    private fun showDailyLesson() {
        val start = if (phrases.isEmpty()) 0 else (LocalDate.now().dayOfYear * 5) % phrases.size
        val daily = (0 until minOf(5, phrases.size)).map { phrases[(start + it) % phrases.size] }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = gradientStroke("#13233D", "#63CFA9", 1, 24)
        }
        box.addView(text("আজকের ৫টি Phrase", 21f, "#FFFFFF", true))
        box.addView(text("প্রতিটি Arabic বাক্য জোরে পড়ুন, অর্থ দেখুন, তারপর ✓ শিখেছি দিন।", 11f, "#94A5C8").apply { setPadding(0, dp(4), 0, dp(10)) })
        daily.forEachIndexed { i, p ->
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = rounded("#1A2A45", 17)
            }
            c.addView(text("${i + 1}. ${p.arabic}", 20f, "#FFFFFF", true).apply { gravity = Gravity.END; textDirection = View.TEXT_DIRECTION_RTL })
            c.addView(text(p.latin, 10.5f, "#72D8B3", true).apply { gravity = Gravity.END })
            c.addView(text("${p.bangla}  •  ${p.english}", 11.5f, "#D3DCEF", true).apply { setPadding(0, dp(5), 0, 0) })
            c.setOnClickListener { speakArabic(p.arabic) }
            box.addView(c)
            if (i < daily.lastIndex) box.addView(space(7))
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(box) }).setPositiveButton("ঠিক আছে", null).show()
    }

    private fun startQuiz() {
        val pool = when (selectedCategory) {
            "সব", "★ পছন্দ", "✓ শেখা" -> phrases
            else -> phrases.filter { it.category == selectedCategory }
        }
        if (pool.size < 4) {
            GuideUiFeedback.info(this, "Quiz-এর জন্য অন্তত ৪টি phrase দরকার। অন্য category নির্বাচন করুন।", "Quick Quiz")
            return
        }
        val question = pool.random()
        val wrong = pool.filter { it.id != question.id }.shuffled().take(3)
        val options = (wrong + question).shuffled()
        val labels = options.map { it.bangla }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${question.arabic}\n${question.latin}")
            .setMessage("সঠিক বাংলা অর্থ কোনটি?")
            .setItems(labels) { _, which ->
                if (options[which].id == question.id) {
                    val learned = learnedIds().toMutableSet(); learned.add(question.id)
                    prefs.edit().putStringSet(KEY_LEARNED, learned).apply()
                    CloudSyncManager.scheduleUpload(this)
                    GuideUiFeedback.success(this, "সঠিক! ${question.arabic} = ${question.bangla}", "Quiz correct")
                    updateProgress(); renderPhraseList()
                } else {
                    GuideUiFeedback.warning(this, "সঠিক উত্তর: ${question.bangla} • ${question.english}", "আরেকবার চেষ্টা করুন")
                }
            }
            .setNegativeButton("বন্ধ", null)
            .show()
    }

    private fun speakArabic(value: String) {
        if (!ttsReady) {
            GuideUiFeedback.info(this, "ফোনে Arabic Text-to-Speech voice প্রস্তুত নেই। Android Text-to-Speech settings থেকে Arabic (Saudi Arabia) voice ইনস্টল করলে শুনতে পারবেন।", "Arabic voice")
            return
        }
        tts?.language = Locale("ar", "SA")
        tts?.setSpeechRate(0.82f)
        tts?.speak(value, TextToSpeech.QUEUE_FLUSH, null, "guide-saudi-${System.currentTimeMillis()}")
    }

    private fun learnedIds(): Set<String> = prefs.getStringSet(KEY_LEARNED, emptySet())?.toSet() ?: emptySet()
    private fun favoriteIds(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    private fun buildPhrases(): List<Phrase> = listOf(
        // Greetings
        p("g01", "অভিবাদন", "السلام عليكم", "as-salaamu alaykum", "আসসালামু আলাইকুম / আপনার উপর শান্তি বর্ষিত হোক", "Peace be upon you", "সব জায়গায় নিরাপদ ও সম্মানজনক greeting"),
        p("g02", "অভিবাদন", "وعليكم السلام", "wa alaykum as-salaam", "ওয়া আলাইকুমুস সালাম", "And peace be upon you", "السلام عليكم-এর উত্তর"),
        p("g03", "অভিবাদন", "هلا", "hala", "হ্যালো / স্বাগতম", "Hi / welcome", "Saudi/Gulf কথ্য ভাষায় খুব সাধারণ"),
        p("g04", "অভিবাদন", "هلا والله", "hala wallah", "আরে, স্বাগতম / খুব আন্তরিক হ্যালো", "Hey, welcome!", "বন্ধুসুলভ Saudi greeting"),
        p("g05", "অভিবাদন", "مرحبا", "marhaba", "হ্যালো / স্বাগতম", "Hello / welcome"),
        p("g06", "অভিবাদন", "صباح الخير", "sabaah al-khair", "সুপ্রভাত", "Good morning"),
        p("g07", "অভিবাদন", "مساء الخير", "masaa al-khair", "শুভ সন্ধ্যা", "Good evening"),
        p("g08", "অভিবাদন", "كيف حالك؟", "kaif haalak?", "আপনি কেমন আছেন?", "How are you?"),
        p("g09", "অভিবাদন", "وش أخبارك؟", "wesh akhbaarak?", "কী খবর?", "What's new? / How are things?", "وش = কী; Saudi কথ্য রূপ"),
        p("g10", "অভিবাদন", "وشلونك؟", "wesh-lonak?", "কেমন আছেন?", "How are you?", "Saudi/Gulf colloquial"),
        p("g11", "অভিবাদন", "تمام الحمد لله", "tamaam, alhamdulillah", "ভালো আছি, আলহামদুলিল্লাহ", "I'm fine, praise be to God"),
        p("g12", "অভিবাদন", "الله يعطيك العافية", "allah ya'teek al-aafiyah", "আল্লাহ আপনাকে সুস্থতা দিন / ধন্যবাদ ধরনের সম্মানজনক কথা", "May God give you wellness / thank you", "কাজের পর কৃতজ্ঞতা বা শুভকামনায় খুব প্রচলিত"),
        p("g13", "অভিবাদন", "الله يسلمك", "allah yisallimak", "আল্লাহ আপনাকে নিরাপদ রাখুন / ধন্যবাদ", "May God keep you safe / thank you"),
        p("g14", "অভিবাদন", "مع السلامة", "ma'a as-salaamah", "বিদায় / ভালো থাকবেন", "Goodbye"),

        // Daily essentials
        p("d01", "দৈনন্দিন", "إيه", "eeh", "হ্যাঁ", "Yes", "Saudi কথ্য ভাষায় نعم-এর বদলে খুব শোনা যায়"),
        p("d02", "দৈনন্দিন", "لا", "laa", "না", "No"),
        p("d03", "দৈনন্দিন", "طيب", "tayyib", "ঠিক আছে / আচ্ছা", "Okay / alright"),
        p("d04", "দৈনন্দিন", "تمام", "tamaam", "ঠিক আছে / সব ভালো", "Fine / perfect / okay"),
        p("d05", "দৈনন্দিন", "أبشر", "abshir", "ঠিক আছে, করে দিচ্ছি / নিশ্চিন্ত থাকুন", "Consider it done", "পুরুষকে বলা; নারীকে أبشري"),
        p("d06", "দৈনন্দিন", "ما عليه", "ma alaih", "সমস্যা নেই / কিছু হয়নি", "No problem / it's okay"),
        p("d07", "দৈনন্দিন", "عادي", "aadi", "ঠিক আছে / স্বাভাবিক / সমস্যা নেই", "It's okay / normal"),
        p("d08", "দৈনন্দিন", "الحين", "al-heen", "এখন", "Now", "Saudi/Gulf কথ্য রূপ; MSA: الآن"),
        p("d09", "দৈনন্দিন", "بعدين", "ba'dain", "পরে", "Later"),
        p("d10", "দৈনন্দিন", "وش؟", "wesh?", "কি?", "What?", "Saudi colloquial"),
        p("d11", "দৈনন্দিন", "وين؟", "wain?", "কোথায়?", "Where?"),
        p("d12", "দৈনন্দিন", "ليش؟", "laish?", "কেন?", "Why?"),
        p("d13", "দৈনন্দিন", "كم؟", "kam?", "কত?", "How much / how many?"),
        p("d14", "দৈনন্দিন", "ممكن؟", "mumkin?", "সম্ভব? / পারবেন?", "Possible? / Can you?"),
        p("d15", "দৈনন্দিন", "لو سمحت", "law samaht", "দয়া করে / মাফ করবেন", "Please / excuse me"),
        p("d16", "দৈনন্দিন", "أبي هذا", "abi hatha", "আমি এটা চাই", "I want this", "أبي Najdi/Saudi কথ্য; Hijaz-এ أبغى-ও খুব প্রচলিত"),
        p("d17", "দৈনন্দিন", "أبغى هذا", "abgha hatha", "আমি এটা চাই", "I want this", "Hijazi/Western Saudi-তে খুব প্রচলিত"),
        p("d18", "দৈনন্দিন", "ما أبي", "ma abi", "আমি চাই না", "I don't want"),
        p("d19", "দৈনন্দিন", "ما أدري", "ma adri", "আমি জানি না", "I don't know"),
        p("d20", "দৈনন্দিন", "فهمت", "fahamt", "আমি বুঝেছি", "I understood"),
        p("d21", "দৈনন্দিন", "ما فهمت", "ma fahamt", "আমি বুঝিনি", "I didn't understand"),
        p("d22", "দৈনন্দিন", "تكلم شوي شوي", "takallam shway shway", "আস্তে আস্তে কথা বলুন", "Speak slowly"),
        p("d23", "দৈনন্দিন", "ممكن تعيد؟", "mumkin ta'eed?", "আবার বলবেন?", "Can you repeat?"),
        p("d24", "দৈনন্দিন", "وش اسمك؟", "wesh ismak?", "আপনার নাম কী?", "What's your name?"),
        p("d25", "দৈনন্দিন", "أنا من بنغلاديش", "ana min Bangladesh", "আমি বাংলাদেশ থেকে এসেছি", "I'm from Bangladesh"),
        p("d26", "দৈনন্দিন", "أنا أتعلم عربي", "ana ata'allam arabi", "আমি আরবি শিখছি", "I'm learning Arabic"),
        p("d27", "দৈনন্দিন", "يلا", "yalla", "চলুন / চল", "Let's go / come on"),
        p("d28", "দৈনন্দিন", "خلاص", "khalaas", "হয়ে গেছে / যথেষ্ট / ঠিক আছে", "Done / enough / okay"),
        p("d29", "দৈনন্দিন", "تم", "tamm", "সম্পন্ন / হয়ে গেছে", "Done / completed"),
        p("d30", "দৈনন্দিন", "مرة كويس", "marrah kuwayyis", "খুব ভালো", "Very good", "Saudi কথায় مرة = খুব/অনেক"),

        // Work
        p("w01", "কাজ", "الدوام", "ad-dawaam", "ডিউটি / কর্মসময়", "Work shift / working hours"),
        p("w02", "কাজ", "متى الدوام؟", "mata ad-dawaam?", "ডিউটি কখন?", "When is the shift?"),
        p("w03", "কাজ", "أنا في الدوام", "ana fi ad-dawaam", "আমি ডিউটিতে আছি", "I'm at work"),
        p("w04", "কাজ", "خلصت الدوام", "khallas-t ad-dawaam", "আমার ডিউটি শেষ", "I finished my shift"),
        p("w05", "কাজ", "عندي بريك", "indi break", "আমার বিরতি আছে", "I have a break", "কথ্য Arabic-এ English break-ও শোনা যায়"),
        p("w06", "কাজ", "أنا مشغول", "ana mashghool", "আমি ব্যস্ত", "I'm busy"),
        p("w07", "কাজ", "أحتاج مساعدة", "ahtaaj musa'adah", "আমার সাহায্য দরকার", "I need help"),
        p("w08", "কাজ", "المدير وين؟", "al-mudeer wain?", "ম্যানেজার কোথায়?", "Where is the manager?"),
        p("w09", "কাজ", "بكرة إجازة؟", "bukrah ijaazah?", "কাল ছুটি?", "Is tomorrow a day off?"),
        p("w10", "কাজ", "اليوم إجازة", "al-yawm ijaazah", "আজ ছুটি", "Today is a day off"),
        p("w11", "কাজ", "تأخرت شوي", "ta'akhkhart shway", "আমি একটু দেরি করেছি", "I'm a little late"),
        p("w12", "কাজ", "أجي الحين", "aji al-heen", "আমি এখন আসছি", "I'm coming now"),
        p("w13", "কাজ", "أجي بعد شوي", "aji ba'd shway", "আমি একটু পরে আসছি", "I'll come in a little while"),
        p("w14", "কাজ", "انتظر شوي", "intazir shway", "একটু অপেক্ষা করুন", "Wait a little"),
        p("w15", "কাজ", "خلصت الشغل", "khallas-t ash-shughl", "কাজ শেষ করেছি", "I finished the work"),

        // Shopping
        p("s01", "কেনাকাটা", "بكم هذا؟", "bikam hatha?", "এটার দাম কত?", "How much is this?"),
        p("s02", "কেনাকাটা", "كم آخر سعر؟", "kam aakhir si'r?", "শেষ দাম কত?", "What's the final price?"),
        p("s03", "কেনাকাটা", "غالي مرة", "ghaali marrah", "অনেক দামি", "Very expensive"),
        p("s04", "কেনাকাটা", "فيه أرخص؟", "feeh arkhas?", "এর চেয়ে সস্তা আছে?", "Is there a cheaper one?"),
        p("s05", "কেনাকাটা", "عندك مقاس أكبر؟", "indak maqaas akbar?", "বড় সাইজ আছে?", "Do you have a bigger size?"),
        p("s06", "কেনাকাটা", "عندك مقاس أصغر؟", "indak maqaas asghar?", "ছোট সাইজ আছে?", "Do you have a smaller size?"),
        p("s07", "কেনাকাটা", "أبي هذا", "abi hatha", "আমি এটা নেব / চাই", "I want this"),
        p("s08", "কেনাকাটা", "كاش ولا شبكة؟", "cash walla shabakah?", "ক্যাশ নাকি কার্ড/POS?", "Cash or card?", "Saudi-তে شبكة বলতে card/POS payment বোঝানো হয়"),
        p("s09", "কেনাকাটা", "أدفع بالبطاقة", "adfa' bil-bitaaqah", "আমি কার্ডে পেমেন্ট করব", "I'll pay by card"),
        p("s10", "কেনাকাটা", "عندك صرف؟", "indak sarf?", "আপনার কাছে ভাংতি আছে?", "Do you have change?"),
        p("s11", "কেনাকাটা", "أبي فاتورة", "abi faatoorah", "আমি রসিদ/ইনভয়েস চাই", "I want a receipt / invoice"),

        // Transport
        p("t01", "যাতায়াত", "وين الموقع؟", "wain al-mawqi'?", "লোকেশন কোথায়?", "Where is the location?"),
        p("t02", "যাতায়াত", "أرسل لي اللوكيشن", "arsil li al-location", "আমাকে লোকেশন পাঠান", "Send me the location", "লোকেশন শব্দটি কথ্য Saudi Arabic-এ খুব প্রচলিত"),
        p("t03", "যাতায়াত", "ودّني هنا", "waddini hina", "আমাকে এখানে নিয়ে যান", "Take me here"),
        p("t04", "যাতায়াত", "وقف هنا", "waqqif hina", "এখানে থামান", "Stop here"),
        p("t05", "যাতায়াত", "يمين", "yameen", "ডান", "Right"),
        p("t06", "যাতায়াত", "يسار", "yasaar", "বাম", "Left"),
        p("t07", "যাতায়াত", "على طول", "ala tool", "সোজা যান", "Go straight"),
        p("t08", "যাতায়াত", "كم الحساب؟", "kam al-hisaab?", "ভাড়া/বিল কত?", "How much is the fare / bill?"),
        p("t09", "যাতায়াত", "متى نوصل؟", "mata noosal?", "আমরা কখন পৌঁছাব?", "When will we arrive?"),
        p("t10", "যাতায়াত", "أنا ضايع", "ana dhaayi'", "আমি পথ হারিয়েছি", "I'm lost"),
        p("t11", "যাতায়াত", "وين محطة البنزين؟", "wain mahattat al-banzeen?", "পেট্রোল পাম্প কোথায়?", "Where is the gas station?"),
        p("t12", "যাতায়াত", "وين المطار؟", "wain al-mataar?", "এয়ারপোর্ট কোথায়?", "Where is the airport?"),

        // Food
        p("f01", "খাবার", "أبي رز ودجاج", "abi ruz wa dajaaj", "আমি ভাত ও মুরগি চাই", "I want rice and chicken"),
        p("f02", "খাবার", "بدون حار", "bidoon haar", "ঝাল ছাড়া", "Not spicy / without chili"),
        p("f03", "খাবার", "شوي حار", "shway haar", "একটু ঝাল", "A little spicy"),
        p("f04", "খাবার", "أبي موية", "abi moyyah", "আমি পানি চাই", "I want water", "موية Saudi কথ্য ভাষায় পানি"),
        p("f05", "খাবার", "الحساب لو سمحت", "al-hisaab law samaht", "বিলটা দিন, দয়া করে", "The bill, please"),
        p("f06", "খাবার", "سفري", "safari", "প্যাক করে নিয়ে যাব / takeaway", "Takeaway / to go", "Restaurant-এ খুব দরকারি Saudi/Gulf usage"),
        p("f07", "খাবার", "محلي", "mahalli", "এখানে বসে খাব", "Dine in"),
        p("f08", "খাবার", "بدون سكر", "bidoon sukkar", "চিনি ছাড়া", "Without sugar"),
        p("f09", "খাবার", "زيادة سكر", "ziyaadat sukkar", "আরও চিনি", "Extra sugar"),
        p("f10", "খাবার", "واحد شاي", "waahid shaay", "এক কাপ চা", "One tea"),
        p("f11", "খাবার", "قهوة عربية", "qahwah arabiyyah", "আরবি কফি", "Arabic coffee"),
        p("f12", "খাবার", "لذيذ", "latheeth", "সুস্বাদু", "Delicious"),

        // Room / mess
        p("r01", "রুম/মেস", "كم الإيجار؟", "kam al-ejaar?", "রুম ভাড়া কত?", "How much is the rent?"),
        p("r02", "রুম/মেস", "دفعت الإيجار", "dafa't al-ejaar", "আমি রুম ভাড়া দিয়েছি", "I paid the rent"),
        p("r03", "রুম/মেস", "باقي علي مبلغ", "baaqi alayya mablagh", "আমার কিছু টাকা বাকি আছে", "I still owe some money"),
        p("r04", "রুম/মেস", "فاتورة الكهرباء", "faatoorat al-kahrabaa", "বিদ্যুৎ বিল", "Electricity bill"),
        p("r05", "রুম/মেস", "فاتورة الموية", "faatoorat al-moyyah", "পানির বিল", "Water bill", "موية কথ্য Saudi শব্দ"),
        p("r06", "রুম/মেস", "فاتورة الإنترنت", "faatoorat al-internet", "ইন্টারনেট বিল", "Internet bill"),
        p("r07", "রুম/মেস", "نقسم الحساب", "niqsim al-hisaab", "আমরা বিল ভাগ করি", "Let's split the bill"),
        p("r08", "রুম/মেস", "كل واحد عليه مية", "kull waahid alaih miyah", "প্রত্যেকের ১০০ করে দিতে হবে", "Each person owes one hundred"),
        p("r09", "রুম/মেস", "أنا دفعت", "ana dafa't", "আমি টাকা দিয়েছি", "I paid"),
        p("r10", "রুম/মেস", "أنت دفعت؟", "inta dafa't?", "আপনি টাকা দিয়েছেন?", "Did you pay?"),
        p("r11", "রুম/মেস", "باقي عليك خمسين", "baaqi alaik khamseen", "আপনার আরও ৫০ বাকি", "You still owe fifty"),
        p("r12", "রুম/মেস", "الحساب كامل", "al-hisaab kaamil", "হিসাব পুরো হয়েছে", "The bill is fully settled"),
        p("r13", "রুম/মেস", "مين اشترى الأغراض؟", "meen ishtara al-aghraadh?", "বাজার/জিনিসপত্র কে কিনেছে?", "Who bought the supplies?"),
        p("r14", "রুম/মেস", "كم صرفنا اليوم؟", "kam sarafna al-yawm?", "আজ আমরা কত খরচ করেছি?", "How much did we spend today?"),

        // Emergency
        p("e01", "জরুরি", "ساعدني", "saa'idni", "আমাকে সাহায্য করুন", "Help me"),
        p("e02", "জরুরি", "اتصل بالإسعاف", "ittasil bil-is'aaf", "অ্যাম্বুলেন্সে ফোন করুন", "Call an ambulance"),
        p("e03", "জরুরি", "اتصل بالشرطة", "ittasil bish-shurtah", "পুলিশে ফোন করুন", "Call the police"),
        p("e04", "জরুরি", "أنا تعبان", "ana ta'baan", "আমি অসুস্থ / শরীর খারাপ", "I'm unwell"),
        p("e05", "জরুরি", "أحتاج مستشفى", "ahtaaj mustashfa", "আমার হাসপাতাল দরকার", "I need a hospital"),
        p("e06", "জরুরি", "عندي ألم", "indi alam", "আমার ব্যথা আছে", "I'm in pain"),
        p("e07", "জরুরি", "وين الصيدلية؟", "wain as-saydaliyyah?", "ফার্মেসি কোথায়?", "Where is the pharmacy?"),
        p("e08", "জরুরি", "ضاعت محفظتي", "dhaa'at mahfazati", "আমার মানিব্যাগ হারিয়েছে", "I lost my wallet"),
        p("e09", "জরুরি", "ضاع جوالي", "dhaa' jawwaali", "আমার মোবাইল হারিয়েছে", "I lost my phone", "جوال Saudi-তে mobile phone-এর সাধারণ শব্দ"),
        p("e10", "জরুরি", "ممكن تساعدني؟", "mumkin tusaa'idni?", "আপনি আমাকে সাহায্য করতে পারবেন?", "Can you help me?"),

        // Numbers / time
        p("n01", "সংখ্যা/সময়", "واحد", "waahid", "এক", "One"),
        p("n02", "সংখ্যা/সময়", "اثنين", "ithnain", "দুই", "Two"),
        p("n03", "সংখ্যা/সময়", "ثلاثة", "thalaathah", "তিন", "Three"),
        p("n04", "সংখ্যা/সময়", "أربعة", "arba'ah", "চার", "Four"),
        p("n05", "সংখ্যা/সময়", "خمسة", "khamsah", "পাঁচ", "Five"),
        p("n06", "সংখ্যা/সময়", "ستة", "sittah", "ছয়", "Six"),
        p("n07", "সংখ্যা/সময়", "سبعة", "sab'ah", "সাত", "Seven"),
        p("n08", "সংখ্যা/সময়", "ثمانية", "thamaaniyah", "আট", "Eight"),
        p("n09", "সংখ্যা/সময়", "تسعة", "tis'ah", "নয়", "Nine"),
        p("n10", "সংখ্যা/সময়", "عشرة", "asharah", "দশ", "Ten"),
        p("n11", "সংখ্যা/সময়", "خمسين", "khamseen", "পঞ্চাশ", "Fifty"),
        p("n12", "সংখ্যা/সময়", "مية", "miyah", "একশ", "One hundred", "কথ্য উচ্চারণ"),
        p("n13", "সংখ্যা/সময়", "ألف", "alf", "এক হাজার", "One thousand"),
        p("n14", "সংখ্যা/সময়", "اليوم", "al-yawm", "আজ", "Today"),
        p("n15", "সংখ্যা/সময়", "بكرة", "bukrah", "আগামীকাল", "Tomorrow", "Saudi কথ্য ভাষায় বহুল ব্যবহৃত"),
        p("n16", "সংখ্যা/সময়", "أمس", "ams", "গতকাল", "Yesterday"),
        p("n17", "সংখ্যা/সময়", "الساعة كم؟", "as-saa'ah kam?", "কয়টা বাজে?", "What time is it?"),
        p("n18", "সংখ্যা/সময়", "بعد خمس دقائق", "ba'd khams daqaa'iq", "পাঁচ মিনিট পরে", "After five minutes"),
        p("n19", "সংখ্যা/সময়", "بعد ساعة", "ba'd saa'ah", "এক ঘণ্টা পরে", "After one hour")
    )

    private fun p(id: String, category: String, arabic: String, latin: String, bangla: String, english: String, note: String = "") =
        Phrase(id, category, arabic, latin, bangla, english, note)

    private fun button(label: String, bg: String, size: Float, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = size
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = gradientStroke(bg, lighten(bg), 1, 14)
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun rounded(hex: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(start: String, end: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    )

    private fun gradientStroke(fill: String, stroke: String, strokeDp: Int, radius: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(fill), shade(fill, 0.76f))
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(strokeDp), Color.parseColor(stroke))
    }

    private fun shade(hex: String, factor: Float): Int {
        val c = Color.parseColor(hex)
        return Color.rgb(
            (Color.red(c) * factor).roundToInt().coerceIn(0, 255),
            (Color.green(c) * factor).roundToInt().coerceIn(0, 255),
            (Color.blue(c) * factor).roundToInt().coerceIn(0, 255)
        )
    }

    private fun lighten(hex: String): String {
        val c = Color.parseColor(hex)
        val r = (Color.red(c) * 1.28f).roundToInt().coerceIn(0, 255)
        val g = (Color.green(c) * 1.28f).roundToInt().coerceIn(0, 255)
        val b = (Color.blue(c) * 1.28f).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun hSpace(width: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(width), 1) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val KEY_LEARNED = "saudi_arabic_learned_v330"
        private const val KEY_FAVORITES = "saudi_arabic_favorites_v330"
    }
}
