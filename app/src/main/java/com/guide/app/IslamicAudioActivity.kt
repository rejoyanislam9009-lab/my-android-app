package com.guide.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Guide v3.34 Islamic Audio Center.
 *
 * Quran recitation streams from the key-free AlQuran Cloud audio endpoint.
 * Waz/nasheed pages intentionally do not bundle third-party copyrighted audio;
 * users can discover online material or import audio they own/have permission to use.
 * Imported audio uses Android's document picker and therefore needs no storage permission.
 */
class IslamicAudioActivity : AppCompatActivity() {

    private data class Surah(val number: Int, val arabic: String, val name: String)
    private data class LocalAudio(val id: String, val title: String, val uri: String, val category: String)
    private data class Topic(val icon: String, val title: String, val subtitle: String, val query: String)

    private val prefs by lazy { getSharedPreferences("islamic_audio_local", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowStatus: TextView
    private lateinit var playPauseButton: Button
    private var selectedTab = "কোরআন"
    private var selectedReciter = 0
    private var pendingImportCategory = "ওয়াজ"

    private var player: MediaPlayer? = null
    private var queue: List<String> = emptyList()
    private var queueIndex = 0
    private var queueTitle = ""
    private var isPrepared = false

    private val reciters = listOf(
        "Mishary Rashid Alafasy" to "ar.alafasy",
        "Abdul Basit • Murattal" to "ar.abdulbasitmurattal",
        "Mahmoud Khalil Al-Husary" to "ar.husary",
        "Mohamed Siddiq Al-Minshawi" to "ar.minshawi"
    )

    private val importAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            saveImportedAudio(uri, pendingImportCategory)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        renderTabs()
        renderContent()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#06101A", "#0D1730")
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val compact = resources.configuration.screenWidthDp <= 360
            setPadding(dp(if (compact) 10 else 14), dp(9), dp(if (compact) 11 else 16), dp(9))
            background = gradient("#0D1B35", "#0A1530")
            elevation = dp(9).toFloat()
        }
        top.addView(button("‹", "#203252", 20f) { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(6), 0) }
        labels.addView(text("ISLAMIC AUDIO", 9.8f, "#7F96C0", true).apply { letterSpacing = 0.11f })
        labels.addView(text("কোরআন • ওয়াজ • নাশিদ", if (resources.configuration.screenWidthDp <= 360) 17f else 19f, "#FFFFFF", true))
        top.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("☪", 24f, "#FFFFFF", true).apply {
            gravity = Gravity.CENTER
            background = gradientStroke("#176B57", "#65D3AD", 1, 18)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        outer.addView(top)

        tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(8)) }
        outer.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabRow)
        })

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val compact = resources.configuration.screenWidthDp <= 360
            val side = dp(if (compact) 12 else 17)
            setPadding(side, dp(8), side, dp(28))
        }
        outer.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val playerBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(10), dp(12), dp(10))
            background = gradientStroke("#111E37", "#35567A", 1, 0)
        }
        val playerLabels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nowTitle = text("কিছু চালু নেই", 12.5f, "#FFFFFF", true).apply { maxLines = 1 }
        nowStatus = text("Quran বা নিজের audio থেকে Play করুন", 10f, "#8395BA").apply { maxLines = 1 }
        playerLabels.addView(nowTitle)
        playerLabels.addView(nowStatus)
        playerBar.addView(playerLabels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        playPauseButton = button("▶", "#286E5A", 15f) { togglePlayback() }
        playerBar.addView(playPauseButton, LinearLayout.LayoutParams(dp(48), dp(42)))
        playerBar.addView(hSpace(7))
        playerBar.addView(button("■", "#653B4D", 13f) { stopPlayback() }, LinearLayout.LayoutParams(dp(46), dp(42)))
        outer.addView(playerBar)

        ViewCompat.setOnApplyWindowInsetsListener(outer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(outer)
        return outer
    }

    private fun renderTabs() {
        tabRow.removeAllViews()
        listOf("কোরআন", "ওয়াজ", "নাশিদ/গজল", "আমার অডিও").forEachIndexed { index, tab ->
            val selected = selectedTab == tab
            tabRow.addView(button(tab, if (selected) "#4F4BC3" else "#1C2A48", 11.5f) {
                if (selectedTab != tab) {
                    selectedTab = tab
                    renderTabs()
                    renderContent()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                if (index > 0) marginStart = dp(7)
            })
        }
    }

    private fun renderContent() {
        content.removeAllViews()
        when (selectedTab) {
            "ওয়াজ" -> renderWaz()
            "নাশিদ/গজল" -> renderNasheed()
            "আমার অডিও" -> renderMyAudio()
            else -> renderQuran()
        }
    }

    private fun renderQuran() {
        content.addView(heroCard(
            "القرآن الكريم",
            "কোরআন তেলাওয়াত",
            "114 সূরা • Reciter বাছাই • আয়াত ধরে streaming • কোনো API key লাগে না",
            "#16443A",
            "#65D1AC"
        ))
        content.addView(space(13))

        content.addView(text("ক্বারী নির্বাচন", 11f, "#9EB0D3", true))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@IslamicAudioActivity, android.R.layout.simple_spinner_dropdown_item, reciters.map { it.first })
            setSelection(selectedReciter)
            background = gradientStroke("#15213B", "#47608A", 1, 15)
            setPadding(dp(12), 0, dp(10), 0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedReciter = position
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        }
        content.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        content.addView(space(11))

        val search = EditText(this).apply {
            hint = "সূরা নম্বর বা নাম খুঁজুন"
            setHintTextColor(Color.parseColor("#6E82A8"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = gradientStroke("#101B32", "#384F73", 1, 15)
        }
        content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        content.addView(space(12))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        fun renderSurahs(query: String) {
            val q = query.trim().lowercase(Locale.getDefault())
            list.removeAllViews()
            surahs().filter {
                q.isBlank() || it.number.toString() == q || it.name.lowercase(Locale.getDefault()).contains(q) || it.arabic.contains(query.trim())
            }.forEachIndexed { index, surah ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(12), dp(12), dp(12))
                    background = gradientStroke("#16223B", "#334D73", 1, 18)
                }
                card.addView(text(surah.number.toString(), 12f, "#FFFFFF", true).apply {
                    gravity = Gravity.CENTER
                    background = rounded("#2C4960", 14)
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(7), 0) }
                labels.addView(text(surah.arabic, 18f, "#FFFFFF", true).apply { textDirection = View.TEXT_DIRECTION_RTL })
                labels.addView(text(surah.name, 10.5f, "#8FA6CF"))
                card.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                card.addView(button("▶ শুনুন", "#23725B", 10.8f) { loadAndPlaySurah(surah) }, LinearLayout.LayoutParams(dp(92), dp(42)))
                list.addView(card)
                if (index < 113) list.addView(space(7))
            }
        }
        renderSurahs("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSurahs(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun renderWaz() {
        content.addView(heroCard(
            "🎙️ বিষয়ভিত্তিক বয়ান",
            "ওয়াজ / ইসলামিক আলোচনা",
            "নিজের/অনুমোদিত audio যোগ করুন, অথবা বিষয় ধরে অনলাইনে শুনুন। কপিরাইটেড বক্তৃতা APK-তে কপি করা হয় না।",
            "#3C2D47",
            "#C18AD0"
        ))
        content.addView(space(12))
        content.addView(actionBanner("＋ ফোন থেকে ওয়াজ যোগ করুন", "Audio file বাছাই করলে Guide-এর ভেতরেই offline player-এ চলবে", "#53355B") {
            pendingImportCategory = "ওয়াজ"
            importAudio.launch(arrayOf("audio/*"))
        })
        content.addView(space(14))
        content.addView(text("বিষয় নির্বাচন করুন", 13f, "#C6D1E9", true))
        content.addView(space(7))
        wazTopics().forEachIndexed { index, topic ->
            content.addView(topicCard(topic, "ওয়াজ"))
            if (index < wazTopics().lastIndex) content.addView(space(8))
        }
        addImportedSection("ওয়াজ")
    }

    private fun renderNasheed() {
        content.addView(heroCard(
            "🎵 নাশিদ / ইসলামিক গজল",
            "হামদ • নাত • ইসলামিক নাশিদ",
            "নিজের বৈধ audio import করে offline শুনুন। Online button কেবল search খুলবে—অনুমতি ছাড়া কোনো copyrighted track bundle করা নেই।",
            "#493A22",
            "#D5B267"
        ))
        content.addView(space(12))
        content.addView(actionBanner("＋ ফোন থেকে নাশিদ/গজল যোগ করুন", "MP3/M4A/OGG সহ Android-supported audio যোগ করা যাবে", "#5E4A27") {
            pendingImportCategory = "নাশিদ/গজল"
            importAudio.launch(arrayOf("audio/*"))
        })
        content.addView(space(14))
        content.addView(text("Collection", 13f, "#C6D1E9", true))
        content.addView(space(7))
        nasheedTopics().forEachIndexed { index, topic ->
            content.addView(topicCard(topic, "নাশিদ/গজল"))
            if (index < nasheedTopics().lastIndex) content.addView(space(8))
        }
        addImportedSection("নাশিদ/গজল")
    }

    private fun renderMyAudio() {
        content.addView(heroCard(
            "🎧 আমার ইসলামিক অডিও",
            "নিজের Library",
            "ফোন থেকে যোগ করা ওয়াজ/নাশিদ এখানে থাকবে এবং internet ছাড়াই চলবে।",
            "#203654",
            "#6FA3D8"
        ))
        content.addView(space(12))
        content.addView(actionBanner("＋ নতুন audio যোগ করুন", "যোগ করার সময় ওয়াজ বা নাশিদ category নির্বাচন করবেন", "#2D4B70") {
            AlertDialog.Builder(this)
                .setTitle("কোন বিভাগে যোগ করবেন?")
                .setItems(arrayOf("ওয়াজ", "নাশিদ/গজল")) { _, which ->
                    pendingImportCategory = if (which == 0) "ওয়াজ" else "নাশিদ/গজল"
                    importAudio.launch(arrayOf("audio/*"))
                }
                .setNegativeButton("বাতিল", null)
                .show()
        })
        content.addView(space(14))
        val all = localAudios()
        if (all.isEmpty()) {
            content.addView(emptyCard("এখনও কোনো audio যোগ করা হয়নি", "উপরের button থেকে ফোনের audio file যোগ করুন।"))
        } else {
            all.forEachIndexed { index, item ->
                content.addView(localAudioCard(item))
                if (index < all.lastIndex) content.addView(space(8))
            }
        }
    }

    private fun addImportedSection(category: String) {
        val list = localAudios().filter { it.category == category }
        content.addView(space(17))
        content.addView(text("আমার $category", 13f, "#C6D1E9", true))
        content.addView(space(7))
        if (list.isEmpty()) {
            content.addView(emptyCard("কোনো local audio নেই", "ফোন থেকে audio যোগ করলে এখানে দেখা যাবে।"))
        } else {
            list.forEachIndexed { index, item ->
                content.addView(localAudioCard(item))
                if (index < list.lastIndex) content.addView(space(8))
            }
        }
    }

    private fun topicCard(topic: Topic, category: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = gradientStroke("#17223A", "#3C5278", 1, 19)
            elevation = dp(3).toFloat()
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(topic.icon, 22f, "#FFFFFF", true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        labels.addView(text(topic.title, 14.5f, "#FFFFFF", true))
        labels.addView(text(topic.subtitle, 10.5f, "#8FA0C2"))
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        card.addView(space(9))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("🌐 অনলাইনে শুনুন", "#345377", 10.5f) { openOnlineSearch(topic.query) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        actions.addView(hSpace(7))
        actions.addView(button("＋ Audio যোগ", "#35664F", 10.5f) {
            pendingImportCategory = category
            importAudio.launch(arrayOf("audio/*"))
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        card.addView(actions)
        return card
    }

    private fun localAudioCard(item: LocalAudio): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = gradientStroke("#15243A", "#3D5E7D", 1, 18)
        }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(item.title, 13.5f, "#FFFFFF", true).apply { maxLines = 2 })
        labels.addView(text("${item.category} • Local / Offline", 10f, "#78C6A8", true))
        titleRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(button("▶", "#276956", 13f) { startQueue(listOf(item.uri), item.title) }, LinearLayout.LayoutParams(dp(48), dp(40)))
        titleRow.addView(hSpace(6))
        titleRow.addView(button("✕", "#653747", 12f) { confirmDelete(item) }, LinearLayout.LayoutParams(dp(44), dp(40)))
        card.addView(titleRow)
        return card
    }

    private fun loadAndPlaySurah(surah: Surah) {
        if (!isOnline()) {
            showNotice("ইন্টারনেট প্রয়োজন", "কোরআন streaming শুনতে Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন।")
            return
        }
        nowTitle.text = "${surah.number}. ${surah.name}"
        nowStatus.text = "তেলাওয়াত প্রস্তুত হচ্ছে…"
        playPauseButton.text = "…"
        val edition = reciters.getOrNull(selectedReciter)?.second ?: "ar.alafasy"
        Thread {
            val result = runCatching {
                val connection = (URL("https://api.alquran.cloud/v1/surah/${surah.number}/$edition").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 12000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val data = JSONObject(body).getJSONObject("data")
                val ayahs = data.getJSONArray("ayahs")
                buildList {
                    for (i in 0 until ayahs.length()) {
                        val audio = ayahs.getJSONObject(i).optString("audio")
                        if (audio.startsWith("http")) add(audio)
                    }
                }
            }.getOrElse { emptyList() }
            runOnUiThread {
                if (result.isEmpty()) {
                    playPauseButton.text = "▶"
                    nowStatus.text = "তেলাওয়াত লোড হয়নি"
                    showNotice("অডিও পাওয়া যায়নি", "এই ক্বারীর stream এখন পাওয়া যাচ্ছে না। অন্য ক্বারী নির্বাচন করে আবার চেষ্টা করুন।")
                } else {
                    startQueue(result, "${surah.number}. ${surah.name} • ${reciters[selectedReciter].first}")
                }
            }
        }.start()
    }

    private fun startQueue(items: List<String>, title: String) {
        if (items.isEmpty()) return
        stopPlayerOnly()
        queue = items
        queueIndex = 0
        queueTitle = title
        nowTitle.text = title
        playQueueItem()
    }

    private fun playQueueItem() {
        if (queueIndex !in queue.indices) {
            nowStatus.text = "✓ সম্পূর্ণ হয়েছে"
            playPauseButton.text = "▶"
            isPrepared = false
            return
        }
        val source = queue[queueIndex]
        isPrepared = false
        playPauseButton.text = "…"
        nowStatus.text = if (queue.size > 1) "আয়াত ${queueIndex + 1}/${queue.size} • Loading…" else "Loading…"
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        runCatching {
            if (source.startsWith("content://") || source.startsWith("file://")) mp.setDataSource(this, Uri.parse(source))
            else mp.setDataSource(source)
        }.onFailure {
            nowStatus.text = "Audio file খোলা যায়নি"
            playPauseButton.text = "▶"
            return
        }
        mp.setOnPreparedListener {
            isPrepared = true
            it.start()
            playPauseButton.text = "Ⅱ"
            nowStatus.text = if (queue.size > 1) "আয়াত ${queueIndex + 1}/${queue.size} • চলছে" else "চলছে"
        }
        mp.setOnCompletionListener {
            if (queueIndex + 1 < queue.size) {
                queueIndex++
                runCatching { it.release() }
                player = null
                playQueueItem()
            } else {
                nowStatus.text = "✓ সম্পূর্ণ হয়েছে"
                playPauseButton.text = "▶"
                isPrepared = false
            }
        }
        mp.setOnErrorListener { failed, _, _ ->
            runCatching { failed.release() }
            player = null
            if (queueIndex + 1 < queue.size) {
                queueIndex++
                playQueueItem()
            } else {
                nowStatus.text = "Audio চালানো যায়নি"
                playPauseButton.text = "▶"
            }
            true
        }
        mp.prepareAsync()
    }

    private fun togglePlayback() {
        val mp = player ?: return
        if (!isPrepared) return
        if (mp.isPlaying) {
            mp.pause()
            playPauseButton.text = "▶"
            nowStatus.text = "Pause"
        } else {
            mp.start()
            playPauseButton.text = "Ⅱ"
            nowStatus.text = if (queue.size > 1) "আয়াত ${queueIndex + 1}/${queue.size} • চলছে" else "চলছে"
        }
    }

    private fun stopPlayback() {
        stopPlayerOnly()
        queue = emptyList()
        queueIndex = 0
        queueTitle = ""
        nowTitle.text = "কিছু চালু নেই"
        nowStatus.text = "Quran বা নিজের audio থেকে Play করুন"
        playPauseButton.text = "▶"
    }

    private fun stopPlayerOnly() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.reset() }
            runCatching { it.release() }
        }
        player = null
        isPrepared = false
    }

    private fun saveImportedAudio(uri: Uri, category: String) {
        val title = queryDisplayName(uri).ifBlank { "Islamic audio ${System.currentTimeMillis()}" }
        val list = localAudios().toMutableList()
        list.add(0, LocalAudio(System.currentTimeMillis().toString(), title, uri.toString(), category))
        saveLocalAudios(list)
        showNotice("Audio যোগ হয়েছে", "$title\n$category বিভাগে সেভ হয়েছে এবং offline-এ চালানো যাবে।")
        renderContent()
    }

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun localAudios(): List<LocalAudio> {
        val raw = prefs.getString("items", "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(LocalAudio(o.getString("id"), o.getString("title"), o.getString("uri"), o.optString("category", "ওয়াজ")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLocalAudios(items: List<LocalAudio>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("uri", item.uri)
                put("category", item.category)
            })
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun confirmDelete(item: LocalAudio) {
        AlertDialog.Builder(this)
            .setTitle("Audio সরাবেন?")
            .setMessage(item.title)
            .setPositiveButton("সরান") { _, _ ->
                saveLocalAudios(localAudios().filterNot { it.id == item.id })
                renderContent()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun openOnlineSearch(query: String) {
        if (!isOnline()) {
            showNotice("ইন্টারনেট নেই", "Online ওয়াজ/নাশিদ খুঁজতে Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন।")
            return
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { showNotice("Browser খোলা যায়নি", "ফোনে browser বা YouTube app আছে কি না দেখুন।") }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showNotice(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ঠিক আছে", null)
            .show()
    }

    private fun actionBanner(title: String, subtitle: String, color: String, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = gradientStroke(color, "#4FFFFFFF", 1, 18)
            elevation = dp(3).toFloat()
            addView(text(title, 13.5f, "#FFFFFF", true))
            addView(text(subtitle, 10.5f, "#AFC0DB").apply { setPadding(0, dp(4), 0, 0) })
            setOnClickListener { action() }
        }
    }

    private fun heroCard(arabic: String, title: String, subtitle: String, fill: String, stroke: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(18), dp(17), dp(17))
            background = gradientStroke(fill, stroke, 1, 23)
            elevation = dp(6).toFloat()
            addView(text(arabic, 25f, "#FFFFFF", true).apply {
                if (arabic.any { it.code in 0x0600..0x06FF }) {
                    gravity = Gravity.END
                    textDirection = View.TEXT_DIRECTION_RTL
                }
            })
            addView(text(title, 17f, "#FFFFFF", true).apply { setPadding(0, dp(5), 0, 0) })
            addView(text(subtitle, 11f, "#B8CAE2").apply { setPadding(0, dp(5), 0, 0) })
        }
    }

    private fun emptyCard(title: String, subtitle: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(20), dp(16), dp(20))
            background = gradientStroke("#141F36", "#334B6D", 1, 18)
            addView(text(title, 14f, "#FFFFFF", true).apply { gravity = Gravity.CENTER })
            addView(text(subtitle, 10.5f, "#8396B9").apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
        }
    }

    private fun wazTopics() = listOf(
        Topic("🕌", "নামাজ ও খুশু", "সালাত, জামাত, মনোযোগ ও নিয়মিত নামাজ", "Bangla Islamic waz namaz salah khushu"),
        Topic("📖", "কোরআন ও সুন্নাহ", "কোরআনের শিক্ষা, হাদিস ও দৈনন্দিন আমল", "Bangla Islamic lecture Quran Sunnah"),
        Topic("🤲", "দোয়া ও তাওয়াক্কুল", "দোয়া, আল্লাহর উপর ভরসা ও বিপদে ধৈর্য", "Bangla waz dua tawakkul sabr"),
        Topic("🌙", "রমজান ও রোজা", "রোজার মাসআলা, আমল ও আত্মশুদ্ধি", "Bangla waz Ramadan fasting Islam"),
        Topic("🏠", "পরিবার ও দাম্পত্য", "পরিবার, পিতা-মাতা, স্বামী-স্ত্রী ও সন্তান", "Bangla Islamic waz family parents marriage"),
        Topic("💚", "আখলাক ও চরিত্র", "সততা, আমানত, গীবত, রাগ ও সুন্দর আচরণ", "Bangla Islamic lecture akhlaq character"),
        Topic("🕋", "হজ ও উমরাহ", "হজ-উমরাহর প্রস্তুতি ও প্রয়োজনীয় শিক্ষা", "Bangla waz Hajj Umrah guidance"),
        Topic("⏳", "আখিরাত ও আত্মশুদ্ধি", "মৃত্যু, কবর, হিসাব ও তাওবা", "Bangla Islamic waz akhirah tawbah death"),
        Topic("💼", "হালাল রিজিক ও প্রবাস জীবন", "কাজ, হালাল আয়, আমানত ও প্রবাসীর জীবন", "Bangla Islamic waz halal income expatriate"),
        Topic("❤️", "ঈমান ও তাওহীদ", "আকিদা, আল্লাহর পরিচয় ও ঈমান শক্ত করা", "Bangla Islamic lecture iman tawhid")
    )

    private fun nasheedTopics() = listOf(
        Topic("🌿", "হামদ", "আল্লাহর প্রশংসামূলক ইসলামিক সংগীত", "Bangla hamd Islamic nasheed"),
        Topic("ﷺ", "নাত / দরুদ", "রাসূল ﷺ-কে নিয়ে নাত ও সালাওয়াত", "Bangla naat Islamic nasheed salawat"),
        Topic("🕋", "মক্কা-মদিনা", "হারামাইন, হজ ও উমরাহ বিষয়ক নাশিদ", "Makkah Madinah Islamic nasheed Bangla"),
        Topic("🤲", "দোয়া ও আবেগঘন নাশিদ", "দোয়া, তাওবা ও আল্লাহর কাছে ফিরে আসা", "Bangla Islamic nasheed dua tawbah"),
        Topic("🌙", "রমজান নাশিদ", "রমজান ও ঈদের ইসলামিক নাশিদ", "Bangla Ramadan nasheed Islamic"),
        Topic("👨‍👩‍👧", "মা-বাবা ও পরিবার", "পিতা-মাতা ও পরিবারের প্রতি ভালোবাসা", "Bangla Islamic nasheed parents family"),
        Topic("🧒", "শিশুদের ইসলামিক নাশিদ", "সহজ, শিক্ষামূলক শিশুদের নাশিদ", "Bangla Islamic nasheed kids"),
        Topic("🌍", "Arabic Nasheed", "আরবি ইসলামিক নাশিদ collection", "Arabic Islamic nasheed vocal"),
        Topic("☁", "শান্ত/রিল্যাক্স নাশিদ", "শান্ত পরিবেশে শোনার জন্য vocal nasheed", "calm Islamic nasheed vocal"),
        Topic("✨", "অনুপ্রেরণামূলক", "সবর, আশা, ঈমান ও ভালো কাজের অনুপ্রেরণা", "motivational Islamic nasheed Bangla")
    )

    private fun surahs(): List<Surah> = listOf(
        Surah(1, "الفاتحة", "Al-Fatihah"), Surah(2, "البقرة", "Al-Baqarah"), Surah(3, "آل عمران", "Aal-Imran"),
        Surah(4, "النساء", "An-Nisa"), Surah(5, "المائدة", "Al-Ma'idah"), Surah(6, "الأنعام", "Al-An'am"),
        Surah(7, "الأعراف", "Al-A'raf"), Surah(8, "الأنفال", "Al-Anfal"), Surah(9, "التوبة", "At-Tawbah"),
        Surah(10, "يونس", "Yunus"), Surah(11, "هود", "Hud"), Surah(12, "يوسف", "Yusuf"), Surah(13, "الرعد", "Ar-Ra'd"),
        Surah(14, "إبراهيم", "Ibrahim"), Surah(15, "الحجر", "Al-Hijr"), Surah(16, "النحل", "An-Nahl"),
        Surah(17, "الإسراء", "Al-Isra"), Surah(18, "الكهف", "Al-Kahf"), Surah(19, "مريم", "Maryam"), Surah(20, "طه", "Taha"),
        Surah(21, "الأنبياء", "Al-Anbiya"), Surah(22, "الحج", "Al-Hajj"), Surah(23, "المؤمنون", "Al-Mu'minun"),
        Surah(24, "النور", "An-Nur"), Surah(25, "الفرقان", "Al-Furqan"), Surah(26, "الشعراء", "Ash-Shu'ara"),
        Surah(27, "النمل", "An-Naml"), Surah(28, "القصص", "Al-Qasas"), Surah(29, "العنكبوت", "Al-Ankabut"),
        Surah(30, "الروم", "Ar-Rum"), Surah(31, "لقمان", "Luqman"), Surah(32, "السجدة", "As-Sajdah"),
        Surah(33, "الأحزاب", "Al-Ahzab"), Surah(34, "سبأ", "Saba"), Surah(35, "فاطر", "Fatir"), Surah(36, "يس", "Ya-Sin"),
        Surah(37, "الصافات", "As-Saffat"), Surah(38, "ص", "Sad"), Surah(39, "الزمر", "Az-Zumar"), Surah(40, "غافر", "Ghafir"),
        Surah(41, "فصلت", "Fussilat"), Surah(42, "الشورى", "Ash-Shura"), Surah(43, "الزخرف", "Az-Zukhruf"),
        Surah(44, "الدخان", "Ad-Dukhan"), Surah(45, "الجاثية", "Al-Jathiyah"), Surah(46, "الأحقاف", "Al-Ahqaf"),
        Surah(47, "محمد", "Muhammad"), Surah(48, "الفتح", "Al-Fath"), Surah(49, "الحجرات", "Al-Hujurat"), Surah(50, "ق", "Qaf"),
        Surah(51, "الذاريات", "Adh-Dhariyat"), Surah(52, "الطور", "At-Tur"), Surah(53, "النجم", "An-Najm"), Surah(54, "القمر", "Al-Qamar"),
        Surah(55, "الرحمن", "Ar-Rahman"), Surah(56, "الواقعة", "Al-Waqi'ah"), Surah(57, "الحديد", "Al-Hadid"),
        Surah(58, "المجادلة", "Al-Mujadila"), Surah(59, "الحشر", "Al-Hashr"), Surah(60, "الممتحنة", "Al-Mumtahanah"),
        Surah(61, "الصف", "As-Saff"), Surah(62, "الجمعة", "Al-Jumu'ah"), Surah(63, "المنافقون", "Al-Munafiqun"),
        Surah(64, "التغابن", "At-Taghabun"), Surah(65, "الطلاق", "At-Talaq"), Surah(66, "التحريم", "At-Tahrim"),
        Surah(67, "الملك", "Al-Mulk"), Surah(68, "القلم", "Al-Qalam"), Surah(69, "الحاقة", "Al-Haqqah"), Surah(70, "المعارج", "Al-Ma'arij"),
        Surah(71, "نوح", "Nuh"), Surah(72, "الجن", "Al-Jinn"), Surah(73, "المزمل", "Al-Muzzammil"), Surah(74, "المدثر", "Al-Muddaththir"),
        Surah(75, "القيامة", "Al-Qiyamah"), Surah(76, "الإنسان", "Al-Insan"), Surah(77, "المرسلات", "Al-Mursalat"),
        Surah(78, "النبأ", "An-Naba"), Surah(79, "النازعات", "An-Nazi'at"), Surah(80, "عبس", "Abasa"), Surah(81, "التكوير", "At-Takwir"),
        Surah(82, "الانفطار", "Al-Infitar"), Surah(83, "المطففين", "Al-Mutaffifin"), Surah(84, "الانشقاق", "Al-Inshiqaq"),
        Surah(85, "البروج", "Al-Buruj"), Surah(86, "الطارق", "At-Tariq"), Surah(87, "الأعلى", "Al-A'la"), Surah(88, "الغاشية", "Al-Ghashiyah"),
        Surah(89, "الفجر", "Al-Fajr"), Surah(90, "البلد", "Al-Balad"), Surah(91, "الشمس", "Ash-Shams"), Surah(92, "الليل", "Al-Layl"),
        Surah(93, "الضحى", "Ad-Duha"), Surah(94, "الشرح", "Ash-Sharh"), Surah(95, "التين", "At-Tin"), Surah(96, "العلق", "Al-Alaq"),
        Surah(97, "القدر", "Al-Qadr"), Surah(98, "البينة", "Al-Bayyinah"), Surah(99, "الزلزلة", "Az-Zalzalah"),
        Surah(100, "العاديات", "Al-Adiyat"), Surah(101, "القارعة", "Al-Qari'ah"), Surah(102, "التكاثر", "At-Takathur"),
        Surah(103, "العصر", "Al-Asr"), Surah(104, "الهمزة", "Al-Humazah"), Surah(105, "الفيل", "Al-Fil"), Surah(106, "قريش", "Quraysh"),
        Surah(107, "الماعون", "Al-Ma'un"), Surah(108, "الكوثر", "Al-Kawthar"), Surah(109, "الكافرون", "Al-Kafirun"),
        Surah(110, "النصر", "An-Nasr"), Surah(111, "المسد", "Al-Masad"), Surah(112, "الإخلاص", "Al-Ikhlas"),
        Surah(113, "الفلق", "Al-Falaq"), Surah(114, "الناس", "An-Nas")
    )

    private fun button(label: String, fill: String, size: Float, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = size
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        background = gradientStroke(fill, "#34FFFFFF", 1, 13)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun gradientStroke(fill: String, stroke: String, strokeDp: Int, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(shade(fill, 1.12f), Color.parseColor(fill), shade(fill, 0.76f))
    ).apply {
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(strokeDp), Color.parseColor(stroke))
    }

    private fun rounded(fill: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(fill))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun shade(hex: String, factor: Float): Int {
        val c = Color.parseColor(hex)
        return Color.argb(
            Color.alpha(c),
            (Color.red(c) * factor).toInt().coerceIn(0, 255),
            (Color.green(c) * factor).toInt().coerceIn(0, 255),
            (Color.blue(c) * factor).toInt().coerceIn(0, 255)
        )
    }

    private fun space(heightDp: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(heightDp)) }
    private fun hSpace(widthDp: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
