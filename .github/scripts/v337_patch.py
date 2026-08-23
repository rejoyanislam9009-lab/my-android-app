from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


def function_bounds(text: str, signature: str):
    start = text.find(signature)
    if start < 0:
        return None
    brace = text.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    end = -1
    for i in range(brace, len(text)):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                end = i + 1
                break
    if end < 0:
        raise SystemExit(f'closing brace not found: {signature}')
    while end < len(text) and text[end] == '\n':
        end += 1
    return start, end


def replace_function(text: str, signature: str, replacement: str) -> str:
    bounds = function_bounds(text, signature)
    if not bounds:
        raise SystemExit(f'function not found: {signature}')
    start, end = bounds
    return text[:start] + replacement + text[end:]


ip = Path('app/src/main/java/com/guide/app/IslamicAudioActivity.kt')
s = ip.read_text()

if 'GuideOfflineIslamicAudioV337' not in s:
    # ------------------------------------------------------------------
    # Models/state + content ScrollView handle.
    # ------------------------------------------------------------------
    topic_anchor = '    private data class Topic(val icon: String, val title: String, val subtitle: String, val query: String)\n'
    require(s, topic_anchor, 'Topic model')
    s = s.replace(topic_anchor, topic_anchor + r'''    // GuideOfflineIslamicAudioV337
    private data class RemoteAudioV337(
        val id: String,
        val title: String,
        val subtitle: String,
        val commonsFile: String,
        val category: String,
        val license: String = "CC0 / Public Domain"
    )

''', 1)

    field_anchor = '    private lateinit var playPauseButton: Button\n'
    require(s, field_anchor, 'player field')
    s = s.replace(field_anchor, field_anchor + '''    private lateinit var contentScrollV337: ScrollView\n    private var activeDownloadIdV337: String? = null\n''', 1)

    old_scroll = '''        outer.addView(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))'''
    new_scroll = '''        contentScrollV337 = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        outer.addView(contentScrollV337, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))'''
    require(s, old_scroll, 'Islamic Audio content scroll')
    s = s.replace(old_scroll, new_scroll, 1)

    # ------------------------------------------------------------------
    # Tabs include an explicit Offline Downloads library.
    # ------------------------------------------------------------------
    new_tabs = r'''    private fun renderTabs() {
        tabRow.removeAllViews()
        listOf("কোরআন", "ওয়াজ", "নাশিদ/গজল", "ডাউনলোড", "আমার অডিও").forEachIndexed { index, tab ->
            val selected = selectedTab == tab
            tabRow.addView(button(tab, if (selected) "#4F4BC3" else "#1C2A48", 11.5f) {
                if (selectedTab != tab) {
                    selectedTab = tab
                    renderTabs()
                    renderContent()
                    contentScrollV337.post { contentScrollV337.scrollTo(0, 0) }
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                if (index > 0) marginStart = dp(7)
            })
        }
    }

'''
    s = replace_function(s, '    private fun renderTabs() {', new_tabs)

    new_content = r'''    private fun renderContent() {
        content.removeAllViews()
        when (selectedTab) {
            "ওয়াজ" -> renderWaz()
            "নাশিদ/গজল" -> renderNasheed()
            "ডাউনলোড" -> renderDownloadsV337()
            "আমার অডিও" -> renderMyAudio()
            else -> renderQuran()
        }
    }

'''
    s = replace_function(s, '    private fun renderContent() {', new_content)

    # ------------------------------------------------------------------
    # Quran: play online OR downloaded local files, with per-surah download.
    # ------------------------------------------------------------------
    new_quran = r'''    private fun renderQuran() {
        content.addView(heroCard(
            "القرآن الكريم",
            "কোরআন তেলাওয়াত • Online + Offline",
            "114 সূরা • ক্বারী বাছাই • একবার Download করলে internet ছাড়াই শুনুন",
            "#16443A",
            "#65D1AC"
        ))
        content.addView(space(10))
        content.addView(actionBanner(
            "⬇ সূরা Download করে Offline শুনুন",
            "প্রতিটি সূরার Download button আছে। Audio app-এর private storage-এ থাকবে; storage permission লাগে না।",
            "#174E43"
        ) {
            selectedTab = "ডাউনলোড"
            renderTabs()
            renderContent()
            contentScrollV337.post { contentScrollV337.scrollTo(0, 0) }
        })
        content.addView(space(13))

        content.addView(text("ক্বারী নির্বাচন", 11f, "#9EB0D3", true))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@IslamicAudioActivity, android.R.layout.simple_spinner_dropdown_item, reciters.map { it.first })
            setSelection(selectedReciter)
            background = gradientStroke("#15213B", "#47608A", 1, 15)
            setPadding(dp(12), 0, dp(10), 0)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (selectedReciter != position) {
                        selectedReciter = position
                        rerenderContentPreserveV337()
                    }
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
            val visible = surahs().filter {
                q.isBlank() || it.number.toString() == q || it.name.lowercase(Locale.getDefault()).contains(q) || it.arabic.contains(query.trim())
            }
            visible.forEachIndexed { index, surah ->
                val offline = isSurahDownloadedV337(surah)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(12), dp(12), dp(12))
                    background = gradientStroke(if (offline) "#15362F" else "#16223B", if (offline) "#4BBF96" else "#334D73", 1, 18)
                    elevation = dp(3).toFloat()
                }
                val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                titleRow.addView(text(surah.number.toString(), 12f, "#FFFFFF", true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(if (offline) "#28664F" else "#2C4960", 14)
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
                val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(7), 0) }
                labels.addView(text(surah.arabic, 18f, "#FFFFFF", true).apply { textDirection = View.TEXT_DIRECTION_RTL })
                labels.addView(text("${surah.name} • ${reciters[selectedReciter].first}", 10.3f, "#8FA6CF"))
                if (offline) labels.addView(text("✓ OFFLINE READY", 9.8f, "#71D9B0", true).apply { setPadding(0, dp(2), 0, 0) })
                titleRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                card.addView(titleRow)
                card.addView(space(9))
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                actions.addView(button(if (offline) "▶ Offline শুনুন" else "▶ শুনুন", "#23725B", 10.6f) { loadAndPlaySurah(surah) }, LinearLayout.LayoutParams(0, dp(43), 1f))
                actions.addView(hSpace(7))
                actions.addView(button(if (offline) "🗑 Offline মুছুন" else "⬇ Download", if (offline) "#674052" else "#31567F", 10.3f) {
                    toggleSurahDownloadV337(surah)
                }, LinearLayout.LayoutParams(0, dp(43), 1f))
                card.addView(actions)
                list.addView(card)
                if (index < visible.lastIndex) list.addView(space(7))
            }
        }
        renderSurahs("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = renderSurahs(value?.toString().orEmpty())
            override fun afterTextChanged(value: android.text.Editable?) = Unit
        })
    }

'''
    s = replace_function(s, '    private fun renderQuran() {', new_quran)

    # Inject real, open-license recorded Waz before Guide's original text/TTS library.
    waz_bounds = function_bounds(s, '    private fun renderWaz() {')
    if not waz_bounds:
        raise SystemExit('renderWaz not found after v3.35')
    wz_start, wz_end = waz_bounds
    wz = s[wz_start:wz_end]
    info_anchor = '        val info = LinearLayout(this).apply {'
    require(wz, info_anchor, 'v3.35 Waz info anchor')
    recorded = r'''        content.addView(text("🎧 Recorded Waz • Download & Offline", 13f, "#C6D1E9", true))
        content.addView(text("নিচের recordingsগুলো open/public license source থেকে। Download করলে পরে internet ছাড়াই চলবে।", 10.4f, "#8999BB").apply { setPadding(0, dp(3), 0, dp(7)) })
        remoteWazCatalogV337().forEachIndexed { index, item ->
            content.addView(remoteAudioCardV337(item))
            if (index < remoteWazCatalogV337().lastIndex) content.addView(space(8))
        }
        content.addView(space(16))

'''
    wz = wz.replace(info_anchor, recorded + info_anchor, 1)
    s = s[:wz_start] + wz + s[wz_end:]

    # ------------------------------------------------------------------
    # Nasheed/Ghazal becomes an actual downloadable open-audio catalog.
    # ------------------------------------------------------------------
    new_nasheed = r'''    private fun renderNasheed() {
        content.addView(heroCard(
            "🎵 নাশিদ / ইসলামিক গজল",
            "Download • Offline • নিজের Audio",
            "Open/public-license Islamic audio Download করে রাখুন • পরে internet ছাড়াই শুনুন",
            "#493A22",
            "#D5B267"
        ))
        content.addView(space(12))
        content.addView(text("🎧 Downloadable Islamic Audio", 13f, "#E2CCA0", true))
        content.addView(text("প্রতিটি item-এর নিচে source/license দেখানো আছে। Download হলে ✓ Offline Ready হবে।", 10.4f, "#9DA9C1").apply { setPadding(0, dp(3), 0, dp(8)) })
        remoteNasheedCatalogV337().forEachIndexed { index, item ->
            content.addView(remoteAudioCardV337(item))
            if (index < remoteNasheedCatalogV337().lastIndex) content.addView(space(8))
        }

        content.addView(space(16))
        content.addView(actionBanner("＋ ফোন থেকে নাশিদ/গজল যোগ করুন", "নিজের/অনুমোদিত MP3/M4A/OGG যোগ করলে offline library-তে থাকবে", "#5E4A27") {
            pendingImportCategory = "নাশিদ/গজল"
            importAudio.launch(arrayOf("audio/*"))
        })
        content.addView(space(16))
        content.addView(text("আরও Collection খুঁজুন", 13f, "#C6D1E9", true))
        content.addView(space(7))
        nasheedTopics().forEachIndexed { index, topic ->
            content.addView(topicCard(topic, "নাশিদ/গজল"))
            if (index < nasheedTopics().lastIndex) content.addView(space(8))
        }
        addImportedSection("নাশিদ/গজল")
    }

'''
    s = replace_function(s, '    private fun renderNasheed() {', new_nasheed)

    # ------------------------------------------------------------------
    # Quran playback prefers local downloaded files; online only if missing.
    # ------------------------------------------------------------------
    new_load = r'''    private fun loadAndPlaySurah(surah: Surah) {
        val offline = quranOfflineFilesV337(surah)
        if (offline.isNotEmpty()) {
            startQueue(offline.map { Uri.fromFile(it).toString() }, "${surah.number}. ${surah.name} • ${reciters[selectedReciter].first} • Offline")
            return
        }
        if (!isOnline()) {
            showNotice("Offline audio নেই", "এই সূরাটি আগে Download করুন, অথবা Wi-Fi/Mobile Data চালু করে online শুনুন।")
            return
        }
        nowTitle.text = "${surah.number}. ${surah.name}"
        nowStatus.text = "তেলাওয়াত প্রস্তুত হচ্ছে…"
        playPauseButton.text = "…"
        val edition = reciters.getOrNull(selectedReciter)?.second ?: "ar.alafasy"
        Thread {
            val result = runCatching { fetchSurahAudioUrlsV337(surah, edition) }.getOrElse { emptyList() }
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

'''
    s = replace_function(s, '    private fun loadAndPlaySurah(surah: Surah) {', new_load)

    # stopPlayback also resets built-in Waz TTS state from v3.35; preserve that
    # existing function and insert offline helpers before startQueue.
    helper_anchor = '    private fun startQueue(items: List<String>, title: String) {'
    require(s, helper_anchor, 'startQueue helper anchor')
    helpers = r'''    private fun offlineRootV337(): java.io.File = java.io.File(filesDir, "islamic_offline_v337").apply { mkdirs() }

    private fun quranSurahDirV337(surah: Surah): java.io.File {
        val edition = reciters.getOrNull(selectedReciter)?.second ?: "ar.alafasy"
        return java.io.File(offlineRootV337(), "quran/$edition/${surah.number}")
    }

    private fun isSurahDownloadedV337(surah: Surah): Boolean =
        java.io.File(quranSurahDirV337(surah), ".complete").exists() && quranOfflineFilesV337(surah).isNotEmpty()

    private fun quranOfflineFilesV337(surah: Surah): List<java.io.File> {
        val dir = quranSurahDirV337(surah)
        if (!java.io.File(dir, ".complete").exists()) return emptyList()
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }
            ?.sortedBy { it.name.substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
    }

    private fun fetchSurahAudioUrlsV337(surah: Surah, edition: String): List<String> {
        val connection = (URL("https://api.alquran.cloud/v1/surah/${surah.number}/$edition").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 18000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Guide-Android/3.37")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val data = JSONObject(body).getJSONObject("data")
        val ayahs = data.getJSONArray("ayahs")
        return buildList {
            for (i in 0 until ayahs.length()) {
                val audio = ayahs.getJSONObject(i).optString("audio")
                if (audio.startsWith("http")) add(audio)
            }
        }
    }

    private fun toggleSurahDownloadV337(surah: Surah) {
        if (isSurahDownloadedV337(surah)) {
            AlertDialog.Builder(this)
                .setTitle("Offline Download মুছবেন?")
                .setMessage("${surah.name} • ${reciters[selectedReciter].first}\n\nমুছলে আবার offline শুনতে Download করতে হবে।")
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("মুছুন") { _, _ ->
                    quranSurahDirV337(surah).deleteRecursively()
                    rerenderContentPreserveV337()
                    showNotice("Offline audio মুছে গেছে", "${surah.name} এখন আবার online stream করবে।")
                }
                .show()
            return
        }
        downloadSurahV337(surah)
    }

    private fun downloadSurahV337(surah: Surah) {
        if (!isOnline()) {
            showNotice("ইন্টারনেট প্রয়োজন", "সূরা Download করতে Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন।")
            return
        }
        if (activeDownloadIdV337 != null) {
            showNotice("Download চলছে", "একটি audio download শেষ হলে পরেরটি শুরু করুন।")
            return
        }
        val edition = reciters.getOrNull(selectedReciter)?.second ?: "ar.alafasy"
        val downloadId = "quran_${edition}_${surah.number}"
        activeDownloadIdV337 = downloadId
        nowTitle.text = "⬇ ${surah.name}"
        nowStatus.text = "Download প্রস্তুত হচ্ছে…"
        playPauseButton.text = "…"
        Thread {
            val dir = quranSurahDirV337(surah)
            val result = runCatching {
                dir.deleteRecursively()
                dir.mkdirs()
                val urls = fetchSurahAudioUrlsV337(surah, edition)
                if (urls.isEmpty()) error("No audio URLs")
                urls.forEachIndexed { index, url ->
                    val target = java.io.File(dir, String.format(Locale.US, "%04d.mp3", index + 1))
                    downloadUrlToFileV337(url, target)
                    runOnUiThread {
                        nowStatus.text = "Download ${index + 1}/${urls.size} আয়াত"
                    }
                }
                java.io.File(dir, ".complete").writeText("$edition|${surah.number}|${urls.size}")
                urls.size
            }
            runOnUiThread {
                activeDownloadIdV337 = null
                playPauseButton.text = "▶"
                result.onSuccess { count ->
                    nowTitle.text = "✓ ${surah.name} Offline Ready"
                    nowStatus.text = "$count আয়াত Download complete"
                    rerenderContentPreserveV337()
                    showNotice("✓ Download Complete", "${surah.name}\n${reciters[selectedReciter].first}\n\nএখন internet ছাড়াই শুনতে পারবেন।")
                }.onFailure {
                    dir.deleteRecursively()
                    nowTitle.text = "Download ব্যর্থ"
                    nowStatus.text = "আবার চেষ্টা করুন"
                    showNotice("Download হয়নি", "Network/Audio server সমস্যা হয়েছে। Internet ঠিক আছে কি না দেখে আবার চেষ্টা করুন।")
                }
            }
        }.start()
    }

    private fun commonsUrlV337(fileName: String): String {
        val encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        return "https://commons.wikimedia.org/wiki/Special:Redirect/file/$encoded"
    }

    private fun commonsPageV337(fileName: String): String {
        val encoded = URLEncoder.encode("File:$fileName", "UTF-8").replace("+", "%20")
        return "https://commons.wikimedia.org/wiki/$encoded"
    }

    private fun remoteFileV337(item: RemoteAudioV337): java.io.File =
        java.io.File(offlineRootV337(), "library/${item.category}/${item.id}.ogg")

    private fun remoteWazCatalogV337(): List<RemoteAudioV337> = listOf(
        RemoteAudioV337("waz_economics_cc0", "ইসলামি অর্থনীতি", "Obaid Ullah Hamza • বাংলা আলোচনা • প্রায় 5 মিনিট", "Lecture by Obaid Ullah Hamza.ogg", "waz"),
        RemoteAudioV337("waz_ziauddin_cc0", "ইসলামি আলেমের সংক্ষিপ্ত বক্তব্য", "Zia Uddin • বাংলা/Sylheti voice recording", "Zia Uddin's Voice.ogg", "waz"),
        RemoteAudioV337("waz_monjurul_cc0", "ইসলামি বক্তব্য • সংক্ষিপ্ত", "Monjurul Islam Effendi • বাংলাদেশ", "Monjurul Islam Effendi's Voice.ogg", "waz"),
        RemoteAudioV337("waz_farooqi_cc0", "ইসলামি বয়ান", "Zia Ur Rehman Farooqi • recorded speech", "Zia Ur Rehman Farooqi’s Speech.ogg", "waz"),
        RemoteAudioV337("waz_qasmi_cc0", "ইসলামি বক্তব্য", "Isar Ul Qasmi • recorded speech", "Isar Ul Qasmi's Speech.ogg", "waz")
    )

    private fun remoteNasheedCatalogV337(): List<RemoteAudioV337> = listOf(
        RemoteAudioV337("nasheed_asma_cc0", "আসমাউল হুসনা", "Allah-এর সুন্দর নামসমূহ • audio", "Asma Ul Husna.ogg", "nasheed"),
        RemoteAudioV337("nasheed_hafez_cc0", "ফারসি ইসলামিক কবিতা", "Sultan Ahmad Nanupuri • Hafez poem recitation", "Sultan Ahmad Nanupuri’s speech.ogg", "nasheed"),
        RemoteAudioV337("nasheed_islami_masr_cc0", "Islamic chant / anthem", "Arabic Islamic vocal audio", "Eslami ya masr anthem.ogg", "nasheed"),
        RemoteAudioV337("nasheed_adhan_cc0", "সুন্দর আজান", "Adhan • spiritual audio", "Beautiful adhan.ogg", "nasheed"),
        RemoteAudioV337("nasheed_iqamah_cc0", "ইকামাহ", "Second call to prayer • Islamic audio", "Iqamah.ogg", "nasheed")
    )

    private fun allRemoteCatalogV337(): List<RemoteAudioV337> = remoteWazCatalogV337() + remoteNasheedCatalogV337()

    private fun remoteAudioCardV337(item: RemoteAudioV337): View {
        val file = remoteFileV337(item)
        val offline = file.exists() && file.length() > 1024L
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = gradientStroke(if (offline) "#15332D" else "#17223A", if (offline) "#49B58F" else "#3C5278", 1, 19)
            elevation = dp(3).toFloat()
        }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(text(item.title, 14.5f, "#FFFFFF", true))
        labels.addView(text(item.subtitle, 10.4f, "#91A4C5").apply { setPadding(0, dp(2), 0, 0) })
        if (offline) labels.addView(text("✓ OFFLINE READY", 9.7f, "#70D8B0", true).apply { setPadding(0, dp(2), 0, 0) })
        titleRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(titleRow)
        card.addView(space(9))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button(if (offline) "▶ Offline শুনুন" else "▶ Online শুনুন", "#286954", 10.4f) { playRemoteAudioV337(item) }, LinearLayout.LayoutParams(0, dp(43), 1f))
        actions.addView(hSpace(7))
        actions.addView(button(if (offline) "🗑 Download মুছুন" else "⬇ Download", if (offline) "#674052" else "#365B84", 10.2f) { toggleRemoteDownloadV337(item) }, LinearLayout.LayoutParams(0, dp(43), 1f))
        card.addView(actions)
        card.addView(text("${item.license} • Wikimedia Commons", 9.2f, "#7889AA").apply {
            setPadding(0, dp(8), 0, 0)
            setOnClickListener {
                if (isOnline()) runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(commonsPageV337(item.commonsFile)))) }
            }
        })
        return card
    }

    private fun playRemoteAudioV337(item: RemoteAudioV337) {
        val file = remoteFileV337(item)
        if (file.exists() && file.length() > 1024L) {
            startQueue(listOf(Uri.fromFile(file).toString()), "${item.title} • Offline")
            return
        }
        if (!isOnline()) {
            showNotice("Offline audio নেই", "এই audio আগে Download করুন, অথবা Internet চালু করে online শুনুন।")
            return
        }
        startQueue(listOf(commonsUrlV337(item.commonsFile)), item.title)
    }

    private fun toggleRemoteDownloadV337(item: RemoteAudioV337) {
        val file = remoteFileV337(item)
        if (file.exists() && file.length() > 1024L) {
            AlertDialog.Builder(this)
                .setTitle("Download মুছবেন?")
                .setMessage("${item.title}\n\nমুছলে আবার offline শুনতে Download করতে হবে।")
                .setNegativeButton("বাতিল", null)
                .setPositiveButton("মুছুন") { _, _ ->
                    file.delete()
                    rerenderContentPreserveV337()
                }
                .show()
            return
        }
        downloadRemoteAudioV337(item)
    }

    private fun downloadRemoteAudioV337(item: RemoteAudioV337) {
        if (!isOnline()) {
            showNotice("ইন্টারনেট প্রয়োজন", "Audio Download করতে Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন।")
            return
        }
        if (activeDownloadIdV337 != null) {
            showNotice("Download চলছে", "বর্তমান Download শেষ হলে পরেরটি শুরু করুন।")
            return
        }
        activeDownloadIdV337 = item.id
        nowTitle.text = "⬇ ${item.title}"
        nowStatus.text = "Download হচ্ছে…"
        playPauseButton.text = "…"
        val file = remoteFileV337(item)
        Thread {
            val result = runCatching {
                file.parentFile?.mkdirs()
                downloadUrlToFileV337(commonsUrlV337(item.commonsFile), file)
                if (file.length() < 1024L) error("Downloaded file is empty")
                file.length()
            }
            runOnUiThread {
                activeDownloadIdV337 = null
                playPauseButton.text = "▶"
                result.onSuccess { bytes ->
                    nowTitle.text = "✓ ${item.title} Offline Ready"
                    nowStatus.text = "${formatBytesV337(bytes)} Download complete"
                    rerenderContentPreserveV337()
                    showNotice("✓ Download Complete", "${item.title}\nএখন Internet ছাড়াই শুনতে পারবেন।")
                }.onFailure {
                    file.delete()
                    nowTitle.text = "Download ব্যর্থ"
                    nowStatus.text = "আবার চেষ্টা করুন"
                    showNotice("Download হয়নি", "Network বা source সমস্যা হয়েছে। পরে আবার চেষ্টা করুন।")
                }
            }
        }.start()
    }

    private fun downloadUrlToFileV337(sourceUrl: String, target: java.io.File) {
        target.parentFile?.mkdirs()
        val connection = (URL(sourceUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Guide-Android/3.37 (offline-audio)")
        }
        connection.inputStream.use { input ->
            java.io.FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
        }
        connection.disconnect()
    }

    private fun rerenderContentPreserveV337() {
        val y = if (::contentScrollV337.isInitialized) contentScrollV337.scrollY else 0
        renderContent()
        if (::contentScrollV337.isInitialized) contentScrollV337.post { contentScrollV337.scrollTo(0, y) }
    }

    private fun directorySizeV337(file: java.io.File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySizeV337(it) } ?: 0L
    }

    private fun formatBytesV337(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun renderDownloadsV337() {
        val root = offlineRootV337()
        val totalBytes = directorySizeV337(root)
        content.addView(heroCard(
            "⬇ OFFLINE LIBRARY",
            "ডাউনলোড করা কোরআন • ওয়াজ • নাশিদ",
            "মোট ব্যবহার ${formatBytesV337(totalBytes)} • সব audio app-এর private storage-এ নিরাপদে আছে",
            "#17374A",
            "#5BA7D0"
        ))
        content.addView(space(12))

        var found = false
        content.addView(text("📖 Downloaded Quran", 13f, "#C6D1E9", true))
        content.addView(space(7))
        reciters.forEachIndexed { reciterIndex, reciter ->
            val old = selectedReciter
            selectedReciter = reciterIndex
            val downloaded = surahs().filter { isSurahDownloadedV337(it) }
            selectedReciter = old
            if (downloaded.isNotEmpty()) {
                found = true
                content.addView(text(reciter.first, 10.5f, "#8699BC", true).apply { setPadding(0, dp(5), 0, dp(5)) })
                downloaded.forEach { surah ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(12), dp(10), dp(10), dp(10))
                        background = gradientStroke("#15342D", "#3F9779", 1, 16)
                    }
                    val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    labels.addView(text("${surah.number}. ${surah.name}", 13.5f, "#FFFFFF", true))
                    labels.addView(text("✓ Offline • ${reciter.first}", 9.8f, "#72D8B1"))
                    row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(button("▶", "#276956", 12f) {
                        selectedReciter = reciterIndex
                        loadAndPlaySurah(surah)
                    }, LinearLayout.LayoutParams(dp(48), dp(40)))
                    content.addView(row)
                    content.addView(space(6))
                }
            }
        }
        if (!found) content.addView(emptyCard("কোনো Quran download নেই", "কোরআন tab থেকে সূরার Download button চাপুন।"))

        content.addView(space(17))
        content.addView(text("🎧 Downloaded Waz / Nasheed", 13f, "#C6D1E9", true))
        content.addView(space(7))
        val remoteDownloaded = allRemoteCatalogV337().filter { remoteFileV337(it).exists() && remoteFileV337(it).length() > 1024L }
        if (remoteDownloaded.isEmpty()) {
            content.addView(emptyCard("কোনো recorded audio download নেই", "ওয়াজ বা নাশিদ/গজল tab থেকে Download করুন।"))
        } else {
            remoteDownloaded.forEachIndexed { index, item ->
                content.addView(remoteAudioCardV337(item))
                if (index < remoteDownloaded.lastIndex) content.addView(space(7))
            }
        }

        if (totalBytes > 0L) {
            content.addView(space(18))
            content.addView(actionBanner("🗑 সব Offline Download মুছুন", "বর্তমান ${formatBytesV337(totalBytes)} storage খালি হবে • imported নিজের audio মুছবে না", "#663C49") {
                AlertDialog.Builder(this)
                    .setTitle("সব Download মুছবেন?")
                    .setMessage("Quran, recorded Waz ও Nasheed-এর downloaded copy মুছে যাবে। Imported নিজের audio থাকবে।")
                    .setNegativeButton("বাতিল", null)
                    .setPositiveButton("সব মুছুন") { _, _ ->
                        offlineRootV337().deleteRecursively()
                        offlineRootV337().mkdirs()
                        rerenderContentPreserveV337()
                    }
                    .show()
            })
        }
    }

'''
    s = s.replace(helper_anchor, helpers + helper_anchor, 1)

    ip.write_text(s)
    print('v3.37 offline Quran + downloadable open Islamic audio applied')
else:
    print('v3.37 Islamic Audio patch already applied')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 50', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.37.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.37.0"', cs, count=1)
    cp.write_text(cs)
print('v3.37 version metadata applied')
