from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# Guide v3.35
# - Adds an original Guide built-in Bengali waz library inside Islamic Audio.
# - All built-in scripts are original app content, so no third-party sermon audio
#   is redistributed. The phone's Bengali TTS can read them aloud offline when a
#   Bengali voice is installed; the full text always remains available offline.
# - Existing Quran streaming, local audio import, nasheed discovery and every
#   other Guide feature remain unchanged.
ip = Path('app/src/main/java/com/guide/app/IslamicAudioActivity.kt')
s = ip.read_text()

if 'GuideBuiltInWazV335' not in s:
    # Data model + TTS state.
    anchor = '    private data class Topic(val icon: String, val title: String, val subtitle: String, val query: String)\n\n'
    require(s, anchor, 'Topic data class')
    s = s.replace(anchor, anchor + '''    // GuideBuiltInWazV335\n    private data class BuiltinWaz(
        val id: String,
        val icon: String,
        val title: String,
        val subtitle: String,
        val body: String,
        val tags: String
    )

    private var wazTts: android.speech.tts.TextToSpeech? = null
    private var wazTtsReady = false
    private var currentBuiltinWazId: String? = null

''', 1)

    # Initialize a Bengali voice if the device provides one.
    old_create = '''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        renderTabs()
        renderContent()
    }'''
    new_create = '''    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wazTts = android.speech.tts.TextToSpeech(this) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val engine = wazTts ?: return@TextToSpeech
                val result = engine.setLanguage(Locale("bn", "BD"))
                wazTtsReady = result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                    result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                engine.setSpeechRate(0.88f)
                engine.setPitch(1.0f)
            }
        }
        setContentView(buildScreen())
        renderTabs()
        renderContent()
    }'''
    require(s, old_create, 'onCreate')
    s = s.replace(old_create, new_create, 1)

    old_destroy = '''    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }'''
    new_destroy = '''    override fun onDestroy() {
        stopPlayback()
        wazTts?.stop()
        wazTts?.shutdown()
        wazTts = null
        super.onDestroy()
    }'''
    require(s, old_destroy, 'onDestroy')
    s = s.replace(old_destroy, new_destroy, 1)

    # Replace the Waz tab with a true in-app offline library while keeping import
    # and online discovery below it.
    start = s.find('    private fun renderWaz() {')
    end = s.find('    private fun renderNasheed() {', start)
    if start < 0 or end < 0:
        raise SystemExit('pattern not found: renderWaz block')

    new_waz_block = r'''    private fun renderWaz() {
        val builtins = builtInWazV335()
        content.addView(heroCard(
            "🎙️ Guide Built-in Waz",
            "অ্যাপের ভেতরেই ওয়াজ শুনুন ও পড়ুন",
            "${builtins.size}টি original বাংলা ইসলামিক আলোচনা • লেখা সবসময় offline • বাংলা TTS voice থাকলে offline শুনতেও পারবেন",
            "#3C2D47",
            "#C18AD0"
        ))
        content.addView(space(12))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = gradientStroke("#17342F", "#4BB995", 1, 18)
            addView(text("✓ Built-in • Internet লাগবে না", 12.5f, "#7DE0BD", true))
            addView(text("এই ওয়াজগুলো Guide-এর নিজস্ব লেখা। মানুষের রেকর্ডিং কপি করা হয়নি। আপনার ফোনে বাংলা TTS voice থাকলে ‘শুনুন’ চাপলে voice-এ পড়বে।", 10.5f, "#B7CBE0").apply { setPadding(0, dp(4), 0, 0) })
        }
        content.addView(info)
        content.addView(space(12))

        val search = EditText(this).apply {
            hint = "ওয়াজ খুঁজুন — নামাজ, দোয়া, পরিবার, রিজিক..."
            setHintTextColor(Color.parseColor("#7486A8"))
            setTextColor(Color.WHITE)
            textSize = 13f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = gradientStroke("#121D35", "#415777", 1, 15)
        }
        content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        content.addView(space(12))

        val builtInList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(builtInList)

        fun drawBuiltinWaz(query: String) {
            val q = query.trim().lowercase(Locale.getDefault())
            val visible = builtins.filter {
                q.isBlank() || listOf(it.title, it.subtitle, it.body, it.tags)
                    .any { value -> value.lowercase(Locale.getDefault()).contains(q) }
            }
            builtInList.removeAllViews()
            if (visible.isEmpty()) {
                builtInList.addView(emptyCard("কোনো ওয়াজ পাওয়া যায়নি", "অন্য শব্দ লিখে আবার খুঁজুন।"))
            } else {
                visible.forEachIndexed { index, item ->
                    builtInList.addView(builtinWazCardV335(item))
                    if (index < visible.lastIndex) builtInList.addView(space(8))
                }
            }
        }
        drawBuiltinWaz("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                drawBuiltinWaz(value?.toString().orEmpty())
            }
            override fun afterTextChanged(value: android.text.Editable?) = Unit
        })

        content.addView(space(18))
        content.addView(text("নিজের পছন্দের ওয়াজ", 13f, "#C6D1E9", true))
        content.addView(space(7))
        content.addView(actionBanner("＋ ফোন থেকে ওয়াজ যোগ করুন", "নিজের/অনুমোদিত audio যোগ করলে Guide-এর player-এ offline চলবে", "#53355B") {
            pendingImportCategory = "ওয়াজ"
            importAudio.launch(arrayOf("audio/*"))
        })

        content.addView(space(16))
        content.addView(text("আরও বিষয় খুঁজুন", 13f, "#C6D1E9", true))
        content.addView(space(7))
        wazTopics().forEachIndexed { index, topic ->
            content.addView(topicCard(topic, "ওয়াজ"))
            if (index < wazTopics().lastIndex) content.addView(space(8))
        }
        addImportedSection("ওয়াজ")
    }

    private fun builtinWazCardV335(item: BuiltinWaz): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = gradientStroke("#18243D", "#5A4C79", 1, 19)
            elevation = dp(3).toFloat()

            val titleRow = LinearLayout(this@IslamicAudioActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(text(item.icon, 23f, "#FFFFFF", true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val labels = LinearLayout(this@IslamicAudioActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
            labels.addView(text(item.title, 14.5f, "#FFFFFF", true))
            labels.addView(text(item.subtitle, 10.5f, "#94A7C8").apply { maxLines = 2 })
            titleRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(titleRow)
            addView(space(9))

            val actions = LinearLayout(this@IslamicAudioActivity).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(button("▶ শুনুন", "#286954", 10.8f) { speakBuiltinWazV335(item) }, LinearLayout.LayoutParams(0, dp(43), 1f))
            actions.addView(hSpace(7))
            actions.addView(button("📖 পড়ুন", "#4E4879", 10.8f) { showBuiltinWazV335(item) }, LinearLayout.LayoutParams(0, dp(43), 1f))
            addView(actions)
        }
    }

    private fun showBuiltinWazV335(item: BuiltinWaz) {
        AlertDialog.Builder(this)
            .setTitle("${item.icon} ${item.title}")
            .setMessage(item.body)
            .setPositiveButton("🔊 শুনুন") { _, _ -> speakBuiltinWazV335(item) }
            .setNegativeButton("বন্ধ", null)
            .show()
    }

    private fun speakBuiltinWazV335(item: BuiltinWaz) {
        if (!wazTtsReady || wazTts == null) {
            showNotice(
                "বাংলা Voice পাওয়া যায়নি",
                "ওয়াজের পুরো লেখা offline আছে। শুনতে ফোনের Text-to-Speech settings থেকে বাংলা voice install/enable করুন, তারপর আবার চেষ্টা করুন।"
            )
            return
        }
        stopPlayerOnly()
        wazTts?.stop()
        currentBuiltinWazId = item.id
        nowTitle.text = item.title
        nowStatus.text = "Guide Built-in Waz • বাংলা voice চলছে"
        playPauseButton.text = "■"
        val spoken = "${item.title}. ${item.body}"
        wazTts?.speak(spoken, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "guide_waz_${item.id}")
    }

    private fun builtInWazV335(): List<BuiltinWaz> = listOf(
        BuiltinWaz(
            "w01", "🕌", "নামাজকে জীবনের কেন্দ্র করুন", "পাঁচ ওয়াক্ত সালাত ও নিয়মিততার ছোট আলোচনা",
            "নামাজ শুধু একটি কাজ শেষ করা নয়; দিনের ব্যস্ততার মধ্যে আল্লাহকে স্মরণ করার নির্ধারিত সময়। কাজ, ব্যবসা বা পড়াশোনার সময় আগে থেকেই নামাজের সময় মাথায় রাখুন। অজু, জামাত এবং সময়মতো সালাতের জন্য ছোট পরিকল্পনা করলে নিয়মিত হওয়া সহজ হয়। মন ছুটে গেলে হতাশ না হয়ে ধীরে ধীরে মনোযোগ ফিরিয়ে আনুন। প্রতিদিনের লক্ষ্য হোক—আজকের পাঁচ ওয়াক্ত নামাজ যতটা সম্ভব সময়মতো আদায় করব এবং নামাজের পর অল্প সময় দোয়া করব।",
            "নামাজ সালাত জামাত খুশু ইবাদত"
        ),
        BuiltinWaz(
            "w02", "📖", "কোরআনের সঙ্গে প্রতিদিনের সম্পর্ক", "অল্প হলেও নিয়মিত কোরআন পড়া ও বুঝার অভ্যাস",
            "কোরআনের সঙ্গে সম্পর্ক বড় পরিমাণ দিয়ে শুরু করতে হয় না। প্রতিদিন কয়েক আয়াত পড়া, অর্থ দেখা এবং একটি শিক্ষা মনে রাখা—এই ছোট অভ্যাসও মূল্যবান। শুধু তিলাওয়াত নয়, নিজের চরিত্র ও সিদ্ধান্তে কোরআনের শিক্ষা কীভাবে কাজে লাগানো যায় তা ভাবুন। কোনো আয়াত বুঝতে অসুবিধা হলে বিশ্বস্ত তাফসির বা আলেমের ব্যাখ্যা দেখুন। নিয়মিত অল্প পড়া অনিয়মিত অনেক পড়ার চেয়ে অভ্যাস গঠনে বেশি কার্যকর।",
            "কোরআন তিলাওয়াত তাফসির আমল"
        ),
        BuiltinWaz(
            "w03", "🤲", "দোয়া ও তাওয়াক্কুল", "চেষ্টা, দোয়া এবং আল্লাহর উপর ভরসা",
            "তাওয়াক্কুল মানে চেষ্টা ছেড়ে দেওয়া নয়। যথাসাধ্য প্রস্তুতি নিন, হালাল পথে পরিশ্রম করুন, তারপর ফল আল্লাহর হাতে ছেড়ে দিন। দোয়ার সময় নিজের প্রয়োজন স্পষ্টভাবে বলুন এবং ভালো ফলের পাশাপাশি কল্যাণকর সিদ্ধান্তও চাইুন। দেরি হলে হতাশ না হয়ে ধৈর্য রাখুন। অনেক সময় আমরা যে ফল চাই তা সঙ্গে সঙ্গে না এলেও অপেক্ষা, পরিবর্তন বা অন্য কোনো দরজা আমাদের জন্য কল্যাণকর হতে পারে।",
            "দোয়া তাওয়াক্কুল ধৈর্য আশা"
        ),
        BuiltinWaz(
            "w04", "💚", "সুন্দর চরিত্র ও মানুষের হক", "সততা, নম্রতা, ক্ষমা ও আমানতদারি",
            "ভালো চরিত্র শুধু সুন্দর কথা নয়; লেনদেন, প্রতিশ্রুতি, রাগের সময় আচরণ এবং অন্যের অধিকার রক্ষা—সবকিছু এর অংশ। কারও টাকা, সময় বা দায়িত্ব আপনার কাছে থাকলে তা আমানত মনে করুন। ভুল হলে দ্রুত ক্ষমা চান এবং অন্যের ভুল ক্ষমা করার অভ্যাস করুন। গীবত, অপমান ও অপ্রয়োজনীয় তর্ক থেকে দূরে থাকা মনকে শান্ত রাখে। মানুষ আপনার ইবাদত সবসময় দেখবে না, কিন্তু আপনার আচরণ তারা প্রতিদিন অনুভব করবে।",
            "আখলাক চরিত্র সততা আমানত গীবত"
        ),
        BuiltinWaz(
            "w05", "👪", "মা-বাবার প্রতি দায়িত্ব", "সম্মান, সময় দেওয়া ও দোয়া",
            "মা-বাবার সঙ্গে ভালো ব্যবহার বড় দায়িত্ব। দূরে থাকলে নিয়মিত ফোন করা, তাদের প্রয়োজন জিজ্ঞেস করা এবং সামর্থ্য অনুযায়ী সাহায্য করা সম্পর্ককে শক্ত করে। মতের অমিল হলেও কণ্ঠস্বর ও ভাষায় সম্মান রাখুন। বয়স বাড়লে তাদের অনেক ছোট প্রয়োজনও গুরুত্বপূর্ণ হয়ে ওঠে। জীবিত থাকলে সেবা করুন, আর সবসময় তাদের জন্য কল্যাণ ও রহমতের দোয়া করুন।",
            "মা বাবা পিতামাতা পরিবার সম্মান"
        ),
        BuiltinWaz(
            "w06", "🏠", "দাম্পত্য ও পরিবারের শান্তি", "কথা বলা, দায়িত্ব ভাগ এবং সম্মান",
            "পরিবারে শান্তি তৈরি হয় দৈনন্দিন ছোট আচরণ থেকে। অভিযোগ জমিয়ে না রেখে শান্ত সময়ে কথা বলুন। আয়-ব্যয়, সন্তান, সময় এবং দায়িত্ব নিয়ে পরিষ্কার বোঝাপড়া রাখুন। একজনের ভুলকে পুরো ব্যক্তিত্বের বিচার বানাবেন না। ভালো কাজের প্রশংসা করুন এবং প্রয়োজন হলে ক্ষমা চান। পরিবারে দ্বীন, সম্মান এবং পারস্পরিক দয়া—এই তিনটি বিষয় নিয়মিত চর্চা করলে অনেক সমস্যা বড় হওয়ার আগেই কমে যায়।",
            "দাম্পত্য পরিবার স্বামী স্ত্রী সন্তান"
        ),
        BuiltinWaz(
            "w07", "💼", "হালাল রিজিক ও কাজের আমানত", "কর্মজীবনে সততা ও দায়িত্ব",
            "হালাল রিজিকের চেষ্টা ইবাদতের মানসিকতা নিয়ে করা যায়। কাজের সময় ফাঁকি না দেওয়া, মিথ্যা হিসাব না করা, অন্যের সম্পদ নষ্ট না করা এবং চুক্তি মেনে চলা—এসব আমানতদারির অংশ। আয় কম হলেও হারাম বা প্রতারণার পথ এড়িয়ে চলুন। দক্ষতা বাড়ান, সময়মতো কাজ করুন এবং মানুষের পাওনা আটকে রাখবেন না। রিজিকের জন্য চেষ্টা আমাদের দায়িত্ব, আর বরকতের জন্য সৎ পথ ধরে থাকা জরুরি।",
            "হালাল রিজিক কাজ চাকরি ব্যবসা আমানত"
        ),
        BuiltinWaz(
            "w08", "✈️", "প্রবাস জীবনে ঈমান ও শৃঙ্খলা", "বিদেশে কাজ, একাকীত্ব ও ভালো পরিবেশ",
            "প্রবাসে পরিবার থেকে দূরে থাকা, কাজের চাপ এবং ভিন্ন পরিবেশ ঈমান ও মানসিকতার উপর প্রভাব ফেলতে পারে। নামাজের সময়, হালাল খাবার, ভালো বন্ধু এবং পরিবারের সঙ্গে নিয়মিত যোগাযোগের জন্য নিজস্ব রুটিন বানান। একাকীত্বে ক্ষতিকর অভ্যাসে না গিয়ে কোরআন, ব্যায়াম, শেখা এবং বিশ্রামে সময় দিন। রুমমেটদের সঙ্গে পরিষ্কার হিসাব ও সম্মান বজায় রাখুন। প্রবাসের আয় শুধু আজকের প্রয়োজন নয়—পরিবার, ঋণ এবং ভবিষ্যতের জন্যও পরিকল্পিতভাবে ব্যবহার করুন।",
            "প্রবাস সৌদি কাজ রুমমেট বিদেশ রুটিন"
        ),
        BuiltinWaz(
            "w09", "💰", "ঋণ, পাওনা ও মানুষের টাকা", "হিসাব পরিষ্কার রাখা ও সময়মতো পরিশোধ",
            "ঋণ বা কারও পাওনা থাকলে লিখে রাখুন এবং পরিশোধের বাস্তব পরিকল্পনা করুন। টাকা নেওয়ার সময় ফেরতের সময় ও পদ্ধতি পরিষ্কার করুন। সামর্থ্য থাকা সত্ত্বেও অযথা বিলম্ব সম্পর্ক নষ্ট করে। আবার পাওনাদার হলে মানুষের বাস্তব অসুবিধা বুঝে সময় দেওয়ার মানসিকতাও ভালো। ছোট ছোট হিসাব ভুলে যাওয়ার আগে লিখিত রেকর্ড রাখুন। পরিষ্কার হিসাব বিশ্বাস তৈরি করে এবং ঝগড়া কমায়।",
            "ঋণ দেনা পাওনা হিসাব টাকা"
        ),
        BuiltinWaz(
            "w10", "🤝", "সদকা ও মানুষের পাশে থাকা", "অল্প সাহায্যেরও মূল্য আছে",
            "সাহায্য শুধু বড় অঙ্কের টাকা নয়। ক্ষুধার্তকে খাবার, অসুস্থকে সহায়তা, কারও কাজ সহজ করা, ভালো পরামর্শ বা সময় দেওয়া—সবই উপকারের পথ। নিজের সামর্থ্যের বাইরে গিয়ে দেখানো নয়; নিয়মিত ছোট সাহায্য বেশি টেকসই। সাহায্যের সময় মানুষের মর্যাদা রক্ষা করুন এবং প্রচার করার প্রয়োজন না থাকলে গোপন রাখুন। পরিবার ও নিকটজনের প্রয়োজনও ভুলে যাবেন না।",
            "সদকা দান সাহায্য যাকাত মানবতা"
        ),
        BuiltinWaz(
            "w11", "🌙", "রমজানের প্রস্তুতি", "রোজা, কোরআন, দোয়া ও সময় ব্যবস্থাপনা",
            "রমজান আসার আগে ঘুম, কাজ এবং ইবাদতের একটি বাস্তব রুটিন তৈরি করুন। শুধু খাবারের সময় বদলানো নয়—ভাষা, রাগ, চোখ ও সময়কেও সংযত করার চেষ্টা করুন। কোরআনের জন্য নির্দিষ্ট সময়, দোয়ার তালিকা এবং সামর্থ্য অনুযায়ী সদকার পরিকল্পনা রাখুন। কাজের চাপ থাকলে ছোট কিন্তু নিয়মিত আমল বেছে নিন। রমজানের লক্ষ্য হোক শুধু মাস শেষ করা নয়, বরং কিছু ভালো অভ্যাস মাসের পরেও ধরে রাখা।",
            "রমজান রোজা সেহরি ইফতার তারাবি"
        ),
        BuiltinWaz(
            "w12", "🕋", "হজ ও উমরাহর মানসিক প্রস্তুতি", "ইবাদত, ধৈর্য ও মানুষের প্রতি সহনশীলতা",
            "হজ বা উমরাহর আগে শুধু যাত্রার ব্যবস্থা নয়, প্রয়োজনীয় মাসআলা ও ধাপগুলো বিশ্বস্ত উৎস থেকে শিখুন। ভিড়, অপেক্ষা এবং ক্লান্তির মধ্যে ধৈর্য রাখা ইবাদতের পরিবেশকে সুন্দর করে। অন্য হাজি বা মুসাফিরকে কষ্ট না দেওয়া, পরিচ্ছন্নতা এবং নিয়ম মেনে চলাও গুরুত্বপূর্ণ। দোয়ার তালিকা রাখুন, তবে অন্যের লেখা দোয়া মুখস্থ করার চাপে না পড়ে নিজের ভাষায়ও আল্লাহর কাছে চাইতে পারেন।",
            "হজ উমরাহ মক্কা মদিনা ইহরাম"
        ),
        BuiltinWaz(
            "w13", "🌱", "তাওবা ও নতুন করে শুরু", "ভুলের পর হতাশ না হয়ে সংশোধন",
            "মানুষ ভুল করে, কিন্তু ভুলকে স্থায়ী পরিচয় বানানো দরকার নেই। ভুল বুঝতে পারলে থামুন, অনুতপ্ত হন এবং আবার না করার বাস্তব ব্যবস্থা নিন। কারও অধিকার নষ্ট করলে সম্ভব হলে তা ফিরিয়ে দিন বা ক্ষমা চান। একই ভুল বারবার হলে কারণ খুঁজুন—পরিবেশ, বন্ধু, ফোনের ব্যবহার বা অভ্যাস—যেটা সমস্যা তৈরি করছে সেটাকে বদলান। আশা হারানো নয়; প্রতিবার ফিরে আসার চেষ্টা গুরুত্বপূর্ণ।",
            "তাওবা ক্ষমা গুনাহ সংশোধন আশা"
        ),
        BuiltinWaz(
            "w14", "⏳", "সময় ও জীবনের অগ্রাধিকার", "দৈনিক সময়কে অর্থপূর্ণ করা",
            "সময় একবার চলে গেলে ফেরত আসে না। প্রতিদিনের কাজকে তিন ভাগে দেখুন—অবশ্যক দায়িত্ব, উপকারী উন্নতি এবং অপ্রয়োজনীয় সময় নষ্ট। নামাজ, কাজ, পরিবার, বিশ্রাম ও শেখার জন্য বাস্তব সময় রাখুন। ফোনের অকারণ স্ক্রল যদি অনেক সময় নেয়, নির্দিষ্ট সীমা তৈরি করুন। বড় লক্ষ্যকে ছোট দৈনিক কাজে ভাগ করলে চাপ কমে এবং ধারাবাহিকতা বাড়ে।",
            "সময় জীবন লক্ষ্য অভ্যাস পরিকল্পনা"
        ),
        BuiltinWaz(
            "w15", "🗣️", "রাগ, গীবত ও জিহ্বার হেফাজত", "কথার আগে থামা এবং সম্পর্ক বাঁচানো",
            "রাগের সময় বলা একটি বাক্য দীর্ঘ সম্পর্ক নষ্ট করতে পারে। খুব উত্তেজিত হলে সঙ্গে সঙ্গে জবাব না দিয়ে একটু সময় নিন। নিশ্চিত না হয়ে কারও খবর ছড়াবেন না এবং অন্যের অনুপস্থিতিতে অপমানজনক আলোচনা এড়িয়ে চলুন। মতবিরোধে ব্যক্তিকে নয়, সমস্যাকে নিয়ে কথা বলুন। নরম ভাষা দুর্বলতা নয়; অনেক সময় এটি বিরোধ সমাধানের সবচেয়ে শক্তিশালী উপায়।",
            "রাগ গীবত কথা জিহ্বা ঝগড়া"
        ),
        BuiltinWaz(
            "w16", "🧑‍🤝‍🧑", "বন্ধুত্ব ও ভালো সঙ্গ", "যে সঙ্গ আপনাকে ভালো পথে রাখে",
            "বন্ধুর প্রভাব দৈনন্দিন সিদ্ধান্তে অনেক বড়। এমন মানুষের সঙ্গ নিন যারা ভুল করলে সততার সঙ্গে মনে করিয়ে দেয়, উন্নতি করতে উৎসাহ দেয় এবং ক্ষতিকর কাজে টেনে নেয় না। বন্ধুত্বে গোপন কথা রক্ষা, হিংসা কমানো এবং সাফল্যে আনন্দিত হওয়া জরুরি। কারও সঙ্গে দূরত্ব তৈরি করতে হলে অপমান না করে শান্তভাবে সীমা নির্ধারণ করা যায়।",
            "বন্ধু সঙ্গ সমাজ যুবক"
        ),
        BuiltinWaz(
            "w17", "❤️", "দুশ্চিন্তায় ধৈর্য ও আশা", "চাপের সময় ইবাদত, সাহায্য ও বাস্তব পদক্ষেপ",
            "দুশ্চিন্তা হলে শুধু নিজের ভেতরে সব চাপ জমিয়ে রাখবেন না। নামাজ ও দোয়ার পাশাপাশি বিশ্বাসযোগ্য মানুষ, পরিবার বা প্রয়োজন হলে পেশাদার সাহায্যের সঙ্গে কথা বলুন। সমস্যা যেটুকু আপনার নিয়ন্ত্রণে আছে সেটার ছোট পদক্ষেপ ঠিক করুন। ঘুম, খাবার ও শরীরের যত্নও অবহেলা করবেন না। কঠিন সময়কে ঈমানের ব্যর্থতা ভাবার দরকার নেই; ধৈর্য মানে কষ্ট অস্বীকার করা নয়, বরং কষ্টের মধ্যেও সঠিক পথে চলার চেষ্টা।",
            "দুশ্চিন্তা ধৈর্য আশা মানসিক চাপ"
        ),
        BuiltinWaz(
            "w18", "🌅", "সকাল-সন্ধ্যার ভালো রুটিন", "জিকির, কৃতজ্ঞতা ও দিনের পরিকল্পনা",
            "দিনের শুরুতে কয়েক মিনিট নীরবতা, দোয়া, জিকির এবং আজকের গুরুত্বপূর্ণ কাজ ঠিক করা মনকে গুছিয়ে দেয়। সন্ধ্যায় দিনের ভুল ও ভালো কাজগুলো দেখে অল্প আত্মসমালোচনা করুন। কৃতজ্ঞ হওয়ার মতো তিনটি বিষয় মনে করা হতাশা কমাতে সাহায্য করতে পারে। খুব বড় রুটিন না বানিয়ে এমন কিছু বেছে নিন যা প্রতিদিন করা সম্ভব। ধারাবাহিক ছোট ভালো কাজই দীর্ঘমেয়াদে জীবন বদলায়।",
            "সকাল সন্ধ্যা জিকির কৃতজ্ঞতা রুটিন"
        )
    )

'''
    s = s[:start] + new_waz_block + s[end:]

    # Stop built-in TTS from the persistent player stop button as well.
    stop_anchor = '''    private fun stopPlayback() {
        stopPlayerOnly()
        queue = emptyList()'''
    stop_new = '''    private fun stopPlayback() {
        wazTts?.stop()
        currentBuiltinWazId = null
        stopPlayerOnly()
        queue = emptyList()'''
    require(s, stop_anchor, 'stopPlayback')
    s = s.replace(stop_anchor, stop_new, 1)

    # If the bottom player button is tapped while built-in TTS is speaking,
    # treat it as a simple stop action (Android TTS has no reliable pause API).
    toggle_anchor = '''    private fun togglePlayback() {
        val mp = player ?: return'''
    toggle_new = '''    private fun togglePlayback() {
        if (wazTts?.isSpeaking == true) {
            wazTts?.stop()
            currentBuiltinWazId = null
            playPauseButton.text = "▶"
            nowStatus.text = "ওয়াজ বন্ধ করা হয়েছে"
            return
        }
        val mp = player ?: return'''
    require(s, toggle_anchor, 'togglePlayback')
    s = s.replace(toggle_anchor, toggle_new, 1)

    ip.write_text(s)
    print('v3.35 built-in original offline waz library applied')
else:
    print('v3.35 IslamicAudioActivity patch already applied')

# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 48', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.35.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.35.0"', cs, count=1)
    cp.write_text(cs)
print('v3.35 version metadata applied')
