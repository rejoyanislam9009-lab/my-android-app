package com.guide.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Practical overview of major Saudi regional conversational varieties.
 * Saudi speech varies by city, tribe, generation and speaker; the labels here
 * are learning-oriented regional families, not a claim that every local variety
 * is identical.
 */
class SaudiDialectActivity : AppCompatActivity() {
    private data class Example(val arabic: String, val banglaPron: String, val bangla: String, val english: String, val note: String = "")
    private data class Region(val icon: String, val title: String, val places: String, val summary: String, val examples: List<Example>)

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("ar", "SA")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        setContentView(buildScreen())
    }

    override fun onDestroy() { tts?.stop(); tts?.shutdown(); tts = null; super.onDestroy() }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = gradient("#07101F", "#111832") }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(16), dp(10)); background = gradient("#101B37", "#0A1530"); elevation = dp(8).toFloat()
        }
        top.addView(button("‹", "#203252", 20f) { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val tl = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        tl.addView(text("SAUDI REGIONAL ARABIC", 9.8f, "#7F92BC", true).apply { letterSpacing = 0.1f })
        tl.addView(text("আঞ্চলিক ভাষা + A–Z", 18.5f, "#FFFFFF", true))
        top.addView(tl, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("🗺", 23f, "#FFFFFF", true).apply { gravity = Gravity.CENTER; background = stroke("#6B4D25", "#E3B568", 18) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(34)) }
        val hero = card("#342A19", "#D4A75A")
        hero.addView(text("সৌদির প্রধান কথ্য রূপ", 21f, "#FFFFFF", true))
        hero.addView(text("Najdi • Hijazi • Eastern/Gulf • Southern • Northern • Bedouin/Common", 11.5f, "#F0C97F", true).apply { setPadding(0,dp(4),0,dp(7)) })
        hero.addView(text("একই Arabic শব্দ শহর, অঞ্চল, পরিবার ও বয়সভেদে একটু ভিন্ন শোনা যেতে পারে। এখানে বাস্তবে বোঝাপড়ার জন্য সবচেয়ে কাজে লাগে এমন regional pattern ও phrase দেওয়া হয়েছে।", 11.2f, "#D6DCE9"))
        content.addView(hero)
        content.addView(space(13))

        val az = card("#1A2946", "#6B72E7")
        az.addView(text("A–Z Practical Roadmap", 17f, "#FFFFFF", true))
        az.addView(text("শুরু থেকে দৈনন্দিন কথোপকথন পর্যন্ত ধাপে ধাপে শেখার পথ।", 10.8f, "#99A8C8").apply { setPadding(0,dp(4),0,dp(9)) })
        az.addView(button("📘 A–Z শেখার তালিকা খুলুন", "#4F49A8", 11.5f) { showRoadmap() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        content.addView(az)
        content.addView(space(16))
        content.addView(text("অঞ্চল নির্বাচন করুন", 15f, "#FFFFFF", true))
        content.addView(text("প্রতিটি card খুললে Arabic + বাংলা উচ্চারণ + অর্থ + English পাবেন।", 10.8f, "#8494B8").apply { setPadding(0,dp(3),0,dp(9)) })

        regions().forEachIndexed { index, region ->
            val c = card(if (index % 2 == 0) "#17243D" else "#15293A", if (index % 2 == 0) "#465D88" else "#397E74")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(region.icon, 24f, "#FFFFFF", true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val ls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8),0,0,0) }
            ls.addView(text(region.title, 15f, "#FFFFFF", true))
            ls.addView(text(region.places, 10.5f, "#7CD9B7", true).apply { setPadding(0,dp(2),0,dp(2)) })
            ls.addView(text(region.summary, 10.3f, "#95A3C0"))
            row.addView(ls, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("›", 24f, "#75D8B5", true))
            c.addView(row); c.setOnClickListener { showRegion(region) }
            content.addView(c); if (index < regions().lastIndex) content.addView(space(8))
        }

        root.addView(ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(content) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        return root
    }

    private fun showRegion(region: Region) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(22)); background = gradient("#0F1931", "#101D37") }
        box.addView(text("${region.icon} ${region.title}", 20f, "#FFFFFF", true))
        box.addView(text(region.places, 11f, "#78D7B5", true).apply { setPadding(0,dp(3),0,dp(2)) })
        box.addView(text(region.summary, 10.8f, "#94A4C3").apply { setPadding(0,0,0,dp(11)) })
        region.examples.forEachIndexed { i, ex ->
            val c = card("#18243D", "#415982")
            c.addView(text("${i+1}. ${ex.arabic}", 22f, "#FFFFFF", true).apply { gravity = Gravity.END; textDirection = View.TEXT_DIRECTION_RTL })
            c.addView(label("বাংলা উচ্চারণ", ex.banglaPron, "#75DAB7"))
            c.addView(label("বাংলা অর্থ", ex.bangla, "#F1C477"))
            c.addView(label("English", ex.english, "#9DB6FF"))
            if (ex.note.isNotBlank()) c.addView(text("নোট • ${ex.note}", 9.8f, "#8798BA").apply { setPadding(0,dp(5),0,0) })
            c.addView(button("🔊 শুনুন", "#2D5670", 11f) { speak(ex.arabic) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(8) })
            box.addView(c); if (i < region.examples.lastIndex) box.addView(space(7))
        }
        val dialog = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(box) }).setNegativeButton("বন্ধ", null).create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) }; dialog.show()
    }

    private fun showRoadmap() {
        val steps = listOf(
            "A • Arabic sounds — ع، ح، خ، غ، ق কীভাবে শুনবেন",
            "B • Basic greetings — সালাম, হালা, কেমন আছেন",
            "C • Courtesy — please, thank you, excuse me",
            "D • Daily questions — কী, কোথায়, কেন, কখন, কত",
            "E • Essential verbs — চাই, যাই, আসি, দিই, নিই, করি",
            "F • Family & people — পরিবার, বন্ধু, সহকর্মী",
            "G • Getting around — ট্যাক্সি, রাস্তা, লোকেশন, এয়ারপোর্ট",
            "H • Home & room — ভাড়া, বিদ্যুৎ, পানি, ইন্টারনেট",
            "I • Iqama & HR — বেতন, ছুটি, কাগজপত্র",
            "J • Job Arabic — ডিউটি, বস, কাজ শুরু/শেষ",
            "K • Kitchen & food — খাবার, পানি, ঝাল, বিল",
            "L • Local dialects — Najdi/Hijazi/Eastern/Southern/Northern",
            "M • Money — দাম, টাকা, বাকি, কার্ড, ক্যাশ",
            "N • Numbers & time — ১–১০০০, দিন, সময়, তারিখ",
            "O • Online/mobile — সিম, ডাটা, Wi‑Fi, রিচার্জ",
            "P • Pharmacy & health — ব্যথা, ওষুধ, হাসপাতাল",
            "Q • Quick responses — ঠিক আছে, না, এখন, পরে, হয়ে যাবে",
            "R • Restaurant — dine-in, takeaway, order, bill",
            "S • Shopping — দাম, discount, size, exchange",
            "T • Telephone — ফোন করুন, message, location send",
            "U • Urgent help — পুলিশ, অ্যাম্বুলেন্স, হারানো জিনিস",
            "V • Visits & hospitality — কফি, স্বাগতম, বসুন",
            "W • Worksite safety — সাবধান, থামুন, সমস্যা, সাহায্য",
            "X • eXtra Saudi expressions — أبشر، خلاص، الحين، وش، وين",
            "Y • Your conversations — নিজের প্রয়োজনের বাক্য practice",
            "Z • Zero-to-daily confidence — প্রতিদিন ৫ phrase + ১ dialogue"
        )
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(20)); background = gradient("#0F1931", "#101D37") }
        box.addView(text("📘 A–Z Practical Saudi Arabic", 20f, "#FFFFFF", true))
        box.addView(text("এই roadmap অনুযায়ী মূল Phrase Library + Conversation + Regional section ব্যবহার করুন।", 10.8f, "#91A1C1").apply { setPadding(0,dp(4),0,dp(11)) })
        steps.forEach { s ->
            box.addView(text(s, 11.5f, "#D7E0F1", true).apply { setPadding(dp(11),dp(9),dp(11),dp(9)); background = stroke("#18243D", "#334A72", 13) })
            box.addView(space(5))
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(box) }).setPositiveButton("ঠিক আছে", null).show()
    }

    private fun speak(arabic: String) {
        if (!ttsReady) { GuideUiFeedback.info(this, "Arabic (Saudi Arabia) TTS voice ইনস্টল থাকলে শুনতে পারবেন। Dialect-এর প্রকৃত উচ্চারণ বক্তাভেদে একটু বদলাতে পারে।", "Arabic voice"); return }
        tts?.language = Locale("ar","SA"); tts?.setSpeechRate(0.80f); tts?.speak(arabic, TextToSpeech.QUEUE_FLUSH, null, "guide-dialect-${System.currentTimeMillis()}")
    }

    private fun regions(): List<Region> = listOf(
        Region("🏙", "Najdi / Central Saudi", "Riyadh • Qassim • central Najd", "Riyadh-কেন্দ্রিক কথ্য Saudi বোঝার জন্য খুব গুরুত্বপূর্ণ। وش، وين، الحين، أبي/أبغى বহুল শোনা যায়।", listOf(
            e("وش تبي؟", "ওয়েশ তাবি?", "আপনি কী চান?", "What do you want?"),
            e("وينك الحين؟", "ওয়াইনাক আলহীন?", "আপনি এখন কোথায়?", "Where are you now?"),
            e("أبي أروح الدوام", "আবি আরুহ আদ-দাওয়াম", "আমি কাজে যেতে চাই", "I want to go to work"),
            e("أبشر", "আবশির", "ঠিক আছে / হয়ে যাবে", "Sure / will do"),
            e("ما عليه", "মা আলাইহ", "সমস্যা নেই / ঠিক আছে", "No problem / it's okay"),
            e("علومك؟", "উলুমাক?", "কী খবর?", "How are things?", "Najdi-তে বন্ধুসুলভ greeting হিসেবে শোনা যায়"),
            e("زين", "জাইন", "ভালো / ঠিক আছে", "Good / fine"),
            e("توّي جيت", "তাওয়ি জিত", "আমি এইমাত্র এসেছি", "I just arrived")
        )),
        Region("🌊", "Hijazi / Western Saudi", "Jeddah • Makkah • Madinah • Taif", "পশ্চিমাঞ্চলের শহুরে কথ্য রূপ; Jeddah/Makkah/Madinah-তে নানা background-এর প্রভাবে variation বেশি।", listOf(
            e("إيش تبغى؟", "ইশ তাবগা?", "আপনি কী চান?", "What do you want?"),
            e("فينك؟", "ফিনাক?", "আপনি কোথায়?", "Where are you?", "Hijazi-তে فين খুব সাধারণ"),
            e("دحين", "দাহীন", "এখন", "Now", "কিছু Hijazi বক্তার কাছে الحين-এর বদলে শোনা যায়"),
            e("مرّة كويس", "মাররা কুয়াইয়িস", "খুব ভালো", "Very good"),
            e("أبغى واحد", "আবগা ওয়াহিদ", "আমি একটা চাই", "I want one"),
            e("لسّه", "লিস্সা", "এখনও / এখনও হয়নি", "Still / not yet"),
            e("طيب خلاص", "তাইয়্যিব খালাস", "ঠিক আছে, হয়ে গেছে", "Okay, done"),
            e("تعال هنا", "তা'আল হিনা", "এখানে আসুন", "Come here")
        )),
        Region("🌴", "Eastern / Gulf-influenced", "Dammam • Khobar • Qatif • Al-Ahsa", "Eastern Province-এ Gulf প্রতিবেশী কথ্য রূপের সঙ্গে মিল পাওয়া যায়; শহরভেদে পার্থক্য উল্লেখযোগ্য।", listOf(
            e("شلونك؟", "শ্লোনাক?", "আপনি কেমন আছেন?", "How are you?"),
            e("وين رايح؟", "ওয়াইন রাইহ?", "কোথায় যাচ্ছেন?", "Where are you going?"),
            e("أبي ماي", "আবি মাই", "আমি পানি চাই", "I want water", "কিছু Gulf-influenced কথ্য রূপে ماي শোনা যায়; Saudi common-এ موية-ও প্রচলিত"),
            e("واجد", "ওয়াজিদ", "অনেক", "A lot", "Gulf অঞ্চলে বহুল পরিচিত"),
            e("ماكو مشكلة", "মাকু মুশকিলা", "সমস্যা নেই", "No problem", "কিছু Gulf speech-এ শোনা যায়; Saudi-wide standard নয়"),
            e("الحين أجي", "আলহীন আজি", "আমি এখন আসছি", "I'm coming now"),
            e("تمام إن شاء الله", "তামাম ইনশা আল্লাহ", "ঠিক আছে ইনশাআল্লাহ", "Okay, God willing"),
            e("كم الحساب؟", "কাম আল-হিসাব?", "বিল কত?", "How much is the bill?")
        )),
        Region("⛰", "Southern Saudi", "Asir • Abha • Jazan • Najran", "দক্ষিণে অনেক স্থানীয় variety আছে; এখানে newcomer-এর জন্য common Southern/Saudi-understandable forms রাখা হয়েছে।", listOf(
            e("كيفك؟", "কাইফাক?", "আপনি কেমন আছেন?", "How are you?"),
            e("وين بتروح؟", "ওয়াইন বিতরুহ?", "কোথায় যাবেন?", "Where are you going?"),
            e("أبغى أروح السوق", "আবগা আরুহ আস-সুক", "আমি বাজারে যেতে চাই", "I want to go to the market"),
            e("ما معي فلوس", "মা মা'ই ফুলুস", "আমার কাছে টাকা নেই", "I don't have money"),
            e("اصبر شوي", "ইসবির শুয়াই", "একটু অপেক্ষা করুন", "Wait a little"),
            e("الحين أرجع", "আলহীন আরজি'", "আমি এখন ফিরে আসছি", "I'm coming back now"),
            e("الله يسلمك", "আল্লাহ ইয়াসাল্লিমাক", "আল্লাহ আপনাকে নিরাপদ রাখুন / ধন্যবাদ", "May God keep you safe / thank you"),
            e("تمام يا رجال", "তামাম ইয়া রিজ্জাল", "ঠিক আছে ভাই/বন্ধু", "Okay, man/friend", "বন্ধুত্বপূর্ণ context; formal জায়গায় ব্যবহার না করাই ভালো")
        )),
        Region("🏜", "Northern Saudi", "Hail • Tabuk • Al-Jawf • Northern Borders", "উত্তরাঞ্চলে Najdi ও Bedouin বৈশিষ্ট্যের overlap দেখা যায়; speaker ও tribe অনুযায়ী variation থাকে।", listOf(
            e("وش عندك؟", "ওয়েশ ইন্দাক?", "আপনার কী আছে / কী ব্যাপার?", "What do you have / what's up?"),
            e("وين تروح؟", "ওয়াইন তروح?", "কোথায় যাচ্ছেন?", "Where are you going?"),
            e("أبي هالشي", "আবি হাশ-শাই", "আমি এই জিনিসটা চাই", "I want this thing"),
            e("ما أدري", "মা আদরি", "আমি জানি না", "I don't know"),
            e("اصبر", "ইসবির", "অপেক্ষা করুন", "Wait"),
            e("الحين نجي", "আলহীন নাজি", "আমরা এখন আসছি", "We're coming now"),
            e("زين إن شاء الله", "জাইন ইনশা আল্লাহ", "ভালো, ইনশাআল্লাহ", "Good, God willing"),
            e("الله يحييك", "আল্লাহ ইয়াহিয়িক", "স্বাগতম / আল্লাহ আপনাকে ভালো রাখুন", "Welcome")
        )),
        Region("🐪", "Bedouin / broadly understood Saudi forms", "Across tribal & rural contexts", "একটি একক ‘Bedouin dialect’ নেই; বিভিন্ন tribal variety আছে। এখানে শুধু বহুল বোঝা যায় এমন কয়েকটি common form দেখানো হয়েছে।", listOf(
            e("يا مرحبا", "ইয়া মারহাবা", "স্বাগতম", "Welcome"),
            e("حياك الله", "হাইয়্যাক আল্লাহ", "আল্লাহ আপনাকে জীবন/কল্যাণ দিন—স্বাগতম", "Welcome / may God greet you"),
            e("وش علومك؟", "ওয়েশ উলুমাক?", "কী খবর?", "How are things?"),
            e("عساك طيب", "আসাক তাইয়্যিব", "আশা করি আপনি ভালো আছেন", "Hope you're well"),
            e("أبشر بسعدك", "আবশির বি-সা'দাক", "নিশ্চিন্ত থাকুন / আনন্দের সঙ্গে করব", "Gladly / consider it done"),
            e("ما قصرت", "মা কাসসারত", "আপনি কোনো কমতি রাখেননি—অনেক ধন্যবাদ", "You did great / thank you"),
            e("الله يبيض وجهك", "আল্লাহ ইয়াবাইয়িদ ওয়াজহাক", "আল্লাহ আপনাকে সম্মান দিন", "May God honor you", "কৃতজ্ঞতা/প্রশংসায় শোনা যায়"),
            e("في أمان الله", "ফি আমানিল্লাহ", "আল্লাহর হেফাজতে থাকুন", "Go in God's protection")
        )),
        Region("📖", "MSA / Formal reference", "News • forms • official communication", "কথ্য Saudi শেখার পাশাপাশি formal Arabic চিনতে এই reference কাজে লাগবে। দৈনন্দিন কথা সবসময় MSA-তে হয় না।", listOf(
            e("ماذا تريد؟", "মাযা তুরিদ?", "আপনি কী চান?", "What do you want?", "কথ্যে وش تبي؟ / إيش تبغى؟ বেশি স্বাভাবিক হতে পারে"),
            e("أين أنت؟", "আইনা আনতা?", "আপনি কোথায়?", "Where are you?", "কথ্যে وينك؟ / فينك؟"),
            e("الآن", "আল-আন", "এখন", "Now", "কথ্যে الحين / دحين"),
            e("أريد ماء", "উরিদ মা'", "আমি পানি চাই", "I want water", "কথ্যে أبي موية / أبغى موية"),
            e("كم السعر؟", "কাম আস-সি'র?", "দাম কত?", "How much is the price?"),
            e("لا أفهم", "লা আফহাম", "আমি বুঝি না", "I don't understand"),
            e("هل يمكنك مساعدتي؟", "হাল ইয়ুমকিনুক মুসা'আদাতি?", "আপনি কি আমাকে সাহায্য করতে পারেন?", "Can you help me?"),
            e("شكراً جزيلاً", "শুকরান জাজিলান", "অনেক ধন্যবাদ", "Thank you very much")
        ))
    )

    private fun e(arabic: String, banglaPron: String, bangla: String, english: String, note: String = "") = Example(arabic,banglaPron,bangla,english,note)
    private fun label(name: String, value: String, color: String): View = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(4),0,dp(4)); addView(text(name,9.5f,"#7283A8",true)); addView(text(value,12.8f,color,true).apply { setPadding(0,dp(2),0,0) }) }
    private fun card(bg: String, border: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14)); background = stroke(bg,border,19); elevation = dp(4).toFloat() }
    private fun text(v:String,size:Float,color:String,bold:Boolean=false)=TextView(this).apply { text=v; textSize=size; setTextColor(Color.parseColor(color)); if(bold)setTypeface(typeface,Typeface.BOLD); includeFontPadding=false }
    private fun button(v:String,bg:String,size:Float,action:()->Unit)=Button(this).apply { text=v; isAllCaps=false; textSize=size; setTextColor(Color.WHITE); setTypeface(typeface,Typeface.BOLD); background=stroke(bg,lighten(bg),14); setOnClickListener{action()} }
    private fun space(h:Int)=Space(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun gradient(a:String,b:String)=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(a),Color.parseColor(b)))
    private fun stroke(bg:String,border:String,radius:Int)=GradientDrawable().apply { shape=GradientDrawable.RECTANGLE; cornerRadius=dp(radius).toFloat(); setColor(Color.parseColor(bg)); setStroke(dp(1),Color.parseColor(border)) }
    private fun lighten(hex:String):String { val c=Color.parseColor(hex); fun f(v:Int)=(v+(255-v)*0.18f).toInt().coerceIn(0,255); return String.format("#%02X%02X%02X",f(Color.red(c)),f(Color.green(c)),f(Color.blue(c))) }
}
