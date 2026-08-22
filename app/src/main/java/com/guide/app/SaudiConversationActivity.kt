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

/** Practical Saudi Arabic conversations for real-life situations. */
class SaudiConversationActivity : AppCompatActivity() {
    private data class Line(
        val speaker: String,
        val arabic: String,
        val banglaPron: String,
        val bangla: String,
        val english: String
    )
    private data class Pack(val icon: String, val title: String, val subtitle: String, val lines: List<Line>)

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

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient("#07101F", "#111832")
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(16), dp(10))
            background = gradient("#101B37", "#0A1530")
            elevation = dp(8).toFloat()
        }
        top.addView(button("‹", "#203252", 20f) { finish() }, LinearLayout.LayoutParams(dp(50), dp(48)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        labels.addView(text("SAUDI CONVERSATION", 10f, "#7F92BC", true).apply { letterSpacing = 0.1f })
        labels.addView(text("সহজ কথোপকথন A–Z", 18.5f, "#FFFFFF", true))
        top.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text("💬", 23f, "#FFFFFF", true).apply { gravity = Gravity.CENTER; background = stroke("#286B5C", "#6AD9B1", 18) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(top)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(34))
        }
        val hero = card("#15322F", "#55D0A4")
        hero.addView(text("বাস্তব কথোপকথন শিখুন", 21f, "#FFFFFF", true))
        hero.addView(text("Arabic • বাংলা উচ্চারণ • বাংলা অর্থ • English", 12f, "#7DE0BC", true).apply { setPadding(0, dp(4), 0, dp(6)) })
        hero.addView(text("সৌদি আরবে কাজ, বাজার, ট্যাক্সি, রেস্টুরেন্ট, হাসপাতাল, রুম/মেস, HR/কাফিল এবং জরুরি পরিস্থিতিতে যে কথাগুলো সবচেয়ে বেশি দরকার—সেগুলো dialogue আকারে অনুশীলন করুন।", 11.5f, "#C2CDE2"))
        content.addView(hero)
        content.addView(space(14))

        val tip = card("#172541", "#596C99")
        tip.addView(text("কীভাবে Practice করবেন", 14f, "#FFFFFF", true))
        tip.addView(text("১) প্রথমে বাংলা উচ্চারণ দেখে বলুন  •  ২) Arabic দেখুন  •  ৩) 🔊 শুনুন  •  ৪) বাংলা/English অর্থ মিলিয়ে নিন", 11f, "#AAB8D3").apply { setPadding(0, dp(5), 0, 0) })
        content.addView(tip)
        content.addView(space(16))
        content.addView(text("কথোপকথনের পরিস্থিতি", 15f, "#FFFFFF", true))
        content.addView(text("একটি scenario খুলে line-by-line practice করুন।", 10.8f, "#8494B8").apply { setPadding(0, dp(3), 0, dp(9)) })

        packs().forEachIndexed { index, pack ->
            val c = card(if (index % 2 == 0) "#17243D" else "#15293A", if (index % 2 == 0) "#415B86" else "#397E74")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(pack.icon, 24f, "#FFFFFF", true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(48), dp(48)))
            val ls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
            ls.addView(text(pack.title, 15f, "#FFFFFF", true))
            ls.addView(text(pack.subtitle, 10.5f, "#91A0BF").apply { setPadding(0, dp(2), 0, 0) })
            row.addView(ls, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text("›", 24f, "#75D8B5", true))
            c.addView(row)
            c.setOnClickListener { showPack(pack) }
            content.addView(c)
            if (index < packs().lastIndex) content.addView(space(8))
        }

        root.addView(ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false; addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun showPack(pack: Pack) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(22))
            background = gradient("#0F1931", "#101D37")
        }
        box.addView(text("${pack.icon} ${pack.title}", 20f, "#FFFFFF", true))
        box.addView(text(pack.subtitle, 11f, "#8FA1C4").apply { setPadding(0, dp(3), 0, dp(12)) })
        pack.lines.forEachIndexed { i, line ->
            val c = card(if (line.speaker == "আপনি") "#17302C" else "#18233C", if (line.speaker == "আপনি") "#4BB78F" else "#455B87")
            c.addView(text("${i + 1}. ${line.speaker}", 10f, "#7EDAB8", true))
            c.addView(text(line.arabic, 23f, "#FFFFFF", true).apply {
                gravity = Gravity.END; textDirection = View.TEXT_DIRECTION_RTL; setPadding(0, dp(5), 0, dp(4))
            })
            c.addView(label("বাংলা উচ্চারণ", line.banglaPron, "#75DAB7"))
            c.addView(label("বাংলা অর্থ", line.bangla, "#F1C477"))
            c.addView(label("English", line.english, "#9DB6FF"))
            c.addView(button("🔊 Arabic শুনুন", "#2D5670", 11f) { speak(line.arabic) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
            box.addView(c)
            if (i < pack.lines.lastIndex) box.addView(space(8))
        }
        val dialog = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(box) }).setNegativeButton("বন্ধ", null).create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) }
        dialog.show()
    }

    private fun speak(arabic: String) {
        if (!ttsReady) {
            GuideUiFeedback.info(this, "Android Text-to-Speech settings থেকে Arabic (Saudi Arabia) voice ইনস্টল করলে উচ্চারণ শুনতে পারবেন।", "Arabic voice দরকার")
            return
        }
        tts?.language = Locale("ar", "SA"); tts?.setSpeechRate(0.80f)
        tts?.speak(arabic, TextToSpeech.QUEUE_FLUSH, null, "guide-conv-${System.currentTimeMillis()}")
    }

    private fun packs(): List<Pack> = listOf(
        Pack("👋", "প্রথম পরিচয়", "Greeting, নাম, দেশ, কেমন আছেন", listOf(
            l("আপনি", "السلام عليكم", "আস-সালামু আলাইকুম", "আপনার উপর শান্তি বর্ষিত হোক", "Peace be upon you"),
            l("অন্যজন", "وعليكم السلام، هلا والله", "ওয়া আলাইকুম আস-সালাম, হালা ওয়াল্লাহ", "আপনার উপরও শান্তি, স্বাগতম", "And peace be upon you, welcome"),
            l("আপনি", "كيف حالك؟", "কাইফ হালাক?", "আপনি কেমন আছেন?", "How are you?"),
            l("অন্যজন", "تمام الحمد لله، وإنت؟", "তামাম আলহামদুলিল্লাহ, ওয়া ইন্তা?", "ভালো আলহামদুলিল্লাহ, আপনি?", "Fine, thank God, and you?"),
            l("আপনি", "أنا من بنغلاديش", "আনা মিন বানগলাদিশ", "আমি বাংলাদেশ থেকে এসেছি", "I am from Bangladesh"),
            l("অন্যজন", "أهلاً وسهلاً", "আহলান ওয়া সাহলান", "স্বাগতম", "Welcome")
        )),
        Pack("🏢", "কাজের জায়গা", "ডিউটি, বস, কাজ শেষ, ছুটি", listOf(
            l("আপনি", "متى الدوام اليوم؟", "মাতা আদ-দাওয়াম আল-ইয়াওম?", "আজ ডিউটি কখন?", "When is the shift today?"),
            l("সহকর্মী", "الدوام الساعة ثمانية", "আদ-দাওয়াম আস-সা'আহ থামানিয়া", "ডিউটি আটটায়", "The shift is at eight"),
            l("আপনি", "وش الشغل اليوم؟", "ওয়েশ আশ-শুগল আল-ইয়াওম?", "আজ কী কাজ?", "What is the work today?"),
            l("সহকর্মী", "المدير قال نخلص هذا أول", "আল-মুদির কাল নুখাল্লিস হাদা আওয়াল", "ম্যানেজার বলেছেন এটা আগে শেষ করতে", "The manager said finish this first"),
            l("আপনি", "خلصت شغلي", "খাল্লাসত শুগলি", "আমার কাজ শেষ করেছি", "I finished my work"),
            l("সহকর্মী", "تمام، تقدر تروح", "তামাম, তাকদার তروح", "ঠিক আছে, আপনি যেতে পারেন", "Okay, you can go")
        )),
        Pack("🧑‍💼", "HR / কাফিল / ইকামা", "বেতন, ছুটি, ইকামা, কাগজপত্র", listOf(
            l("আপনি", "متى ينزل الراتب؟", "মাতা ইয়ানজিল আর-রাতিব?", "বেতন কখন আসবে?", "When will the salary be paid?"),
            l("HR", "إن شاء الله نهاية الشهر", "ইনশা আল্লাহ নিহায়াত আশ-শাহর", "ইনশাআল্লাহ মাসের শেষে", "God willing, at the end of the month"),
            l("আপনি", "إقامتي متى تخلص؟", "ইকামাতি মাতা তুখলাস?", "আমার ইকামা কবে হবে?", "When will my iqama be ready?"),
            l("HR", "باقي يومين", "বাকি ইয়াওমাইন", "আর দুই দিন বাকি", "Two days remaining"),
            l("আপনি", "أبي إجازة يوم واحد", "আবি ইজাজা ইয়াওম ওয়াহিদ", "আমি এক দিনের ছুটি চাই", "I want one day off"),
            l("HR", "قدم الطلب وبنشوف", "কাদ্দিম আত-তালাব ওয়া বিনশুফ", "আবেদন দিন, আমরা দেখব", "Submit the request and we'll see")
        )),
        Pack("🛒", "দোকান / বাজার", "দাম, কমানো, কার্ড, ক্যাশ", listOf(
            l("আপনি", "بكم هذا؟", "বিকাম হাদা?", "এটার দাম কত?", "How much is this?"),
            l("দোকানি", "بخمسين ريال", "বি-খামসিন রিয়াল", "৫০ রিয়াল", "Fifty riyals"),
            l("আপনি", "غالي، فيه خصم؟", "গালি, ফিহ খাসম?", "দাম বেশি, ডিসকাউন্ট আছে?", "Expensive, is there a discount?"),
            l("দোকানি", "آخر سعر خمسة وأربعين", "আখির সি'র খামসা ওয়া আরবাইন", "শেষ দাম ৪৫", "Final price is 45"),
            l("আপনি", "عندكم شبكة؟", "ইন্দাকুম শাবাকা?", "কার্ডে পেমেন্ট করা যাবে?", "Do you accept card?"),
            l("দোকানি", "إيه، شبكة وكاش", "ইহ, শাবাকা ওয়া ক্যাশ", "হ্যাঁ, কার্ড ও ক্যাশ দুটোই", "Yes, card and cash")
        )),
        Pack("🚕", "ট্যাক্সি / গাড়ি", "লোকেশন, ভাড়া, থামুন, ডানে-বামে", listOf(
            l("আপনি", "أبي أروح هذا الموقع", "আবি আরুহ হাদা আল-মাওকি'", "আমি এই লোকেশনে যেতে চাই", "I want to go to this location"),
            l("ড্রাইভার", "تمام، اركب", "তামাম, ইরকাব", "ঠিক আছে, উঠুন", "Okay, get in"),
            l("আপনি", "كم ياخذ وقت؟", "কাম ইয়াখুথ ওয়াক্ত?", "কত সময় লাগবে?", "How long will it take?"),
            l("ড্রাইভার", "تقريباً عشرين دقيقة", "তাকরিবান ইশরিন দাকিকা", "প্রায় ২০ মিনিট", "About twenty minutes"),
            l("আপনি", "يمين من هنا", "ইয়ামিন মিন হিনা", "এখান থেকে ডানে", "Right from here"),
            l("আপনি", "وقف هنا لو سمحت", "ওয়াক্কিফ হিনা লাও সামাহত", "এখানে থামুন, দয়া করে", "Stop here, please")
        )),
        Pack("🍽", "রেস্টুরেন্ট", "অর্ডার, ঝাল, পানি, বিল", listOf(
            l("আপনি", "أبي رز ودجاج", "আবি রুজ ওয়া দাজাজ", "আমি ভাত ও মুরগি চাই", "I want rice and chicken"),
            l("ওয়েটার", "حار ولا عادي؟", "হার ওয়ালা আদি?", "ঝাল নাকি সাধারণ?", "Spicy or regular?"),
            l("আপনি", "بدون حار لو سمحت", "বিদুন হার লাও সামাহত", "ঝাল ছাড়া, দয়া করে", "Not spicy, please"),
            l("আপনি", "وأبي موية باردة", "ওয়া আবি মোইয়্যা বারিদা", "আর ঠান্ডা পানি চাই", "And I want cold water"),
            l("ওয়েটার", "سفري ولا محلي؟", "সাফারি ওয়ালা মাহাল্লি?", "টেকঅ্যাওয়ে নাকি এখানে খাবেন?", "Takeaway or dine in?"),
            l("আপনি", "محلي، والحساب لو سمحت", "মাহাল্লি, ওয়াল-হিসাব লাও সামাহত", "এখানে খাব, আর বিলটা দিন", "Dine in, and the bill please")
        )),
        Pack("🏠", "রুম / মেস", "ভাড়া, বাজার, বিল ভাগ, বাকি টাকা", listOf(
            l("আপনি", "كم الإيجار هذا الشهر؟", "কাম আল-ইজার হাদা আশ-শাহর?", "এই মাসের ভাড়া কত?", "How much is the rent this month?"),
            l("রুমমেট", "الإيجار ألف وخمس مية", "আল-ইজার আলফ ওয়া খামস মিয়া", "ভাড়া ১৫০০", "The rent is 1500"),
            l("আপনি", "نقسمه بين أربعة", "নিকসিমাহ বাইন আরবা'আ", "চারজনের মধ্যে ভাগ করি", "Let's split it among four"),
            l("রুমমেট", "كل واحد عليه ثلاثمية وخمسة وسبعين", "কুল ওয়াহিদ আলাইহ থালাথমিয়া ওয়া খামসা ওয়া সাবঈন", "প্রত্যেকের ৩৭৫ করে", "Each person owes 375"),
            l("আপনি", "أنا دفعت ميتين", "আনা দাফা'ত মিতাইন", "আমি ২০০ দিয়েছি", "I paid 200"),
            l("রুমমেট", "باقي عليك مية وخمسة وسبعين", "বাকি আলাইক মিয়া ওয়া খামসা ওয়া সাবঈন", "আপনার ১৭৫ বাকি", "You still owe 175")
        )),
        Pack("📦", "ডেলিভারি / কুরিয়ার", "লোকেশন, গেট, ফোন, প্যাকেজ", listOf(
            l("ডেলিভারি", "السلام عليكم، معك المندوب", "আস-সালামু আলাইকুম, মা'আক আল-মানদুব", "আমি ডেলিভারি প্রতিনিধি", "Hello, this is the delivery driver"),
            l("আপনি", "وعليكم السلام، وينك؟", "ওয়া আলাইকুম আস-সালাম, ওয়াইনাক?", "আপনি কোথায়?", "Where are you?"),
            l("ডেলিভারি", "أنا عند البوابة", "আনা ইন্দ আল-বাওয়াবা", "আমি গেটের কাছে", "I'm at the gate"),
            l("আপনি", "ادخل من البوابة الثانية", "উদখুল মিন আল-বাওয়াবা আথ-থানিয়া", "দ্বিতীয় গেট দিয়ে ঢুকুন", "Enter through the second gate"),
            l("আপনি", "اتصل علي إذا وصلت", "ইত্তাসিল আলাই ইথা ওয়াসালত", "পৌঁছালে আমাকে ফোন করুন", "Call me when you arrive"),
            l("ডেলিভারি", "أبشر", "আবশির", "ঠিক আছে / হয়ে যাবে", "Sure / will do")
        )),
        Pack("🏥", "হাসপাতাল / ফার্মেসি", "ব্যথা, ওষুধ, ডাক্তার, জরুরি", listOf(
            l("আপনি", "أنا تعبان", "আনা তা'বান", "আমার শরীর খারাপ", "I'm unwell"),
            l("স্টাফ", "وش فيك؟", "ওয়েশ ফিক?", "আপনার কী হয়েছে?", "What's wrong?"),
            l("আপনি", "عندي ألم في الرأس", "ইন্দি আলাম ফি আর-রাস", "আমার মাথায় ব্যথা", "I have a headache"),
            l("স্টাফ", "من متى؟", "মিন মাতা?", "কখন থেকে?", "Since when?"),
            l("আপনি", "من أمس", "মিন আমস", "গতকাল থেকে", "Since yesterday"),
            l("আপনি", "وين الصيدلية؟", "ওয়াইন আস-সাইদালিয়্যা?", "ফার্মেসি কোথায়?", "Where is the pharmacy?")
        )),
        Pack("🚨", "জরুরি / পুলিশ", "সাহায্য, হারানো, দুর্ঘটনা", listOf(
            l("আপনি", "ساعدني لو سمحت", "সা'ইদনি লাও সামাহত", "আমাকে সাহায্য করুন", "Please help me"),
            l("অন্যজন", "وش صار؟", "ওয়েশ সার?", "কী হয়েছে?", "What happened?"),
            l("আপনি", "ضاع جوالي", "দা' জাওয়ালি", "আমার মোবাইল হারিয়েছে", "I lost my phone"),
            l("আপনি", "أبي الشرطة", "আবি আশ-শুরতা", "আমার পুলিশ দরকার", "I need the police"),
            l("আপনি", "صار حادث", "সার হাদিস", "দুর্ঘটনা হয়েছে", "There was an accident"),
            l("অন্যজন", "اتصل بالإسعاف", "ইত্তাসিল বিল-ইস'আফ", "অ্যাম্বুলেন্সে ফোন করুন", "Call an ambulance")
        )),
        Pack("📱", "মোবাইল / ইন্টারনেট", "সিম, ডাটা, Wi‑Fi, রিচার্জ", listOf(
            l("আপনি", "أبي شريحة جديدة", "আবি শারিহা জাদিদা", "আমি নতুন সিম চাই", "I want a new SIM"),
            l("স্টাফ", "مسبق الدفع ولا فاتورة؟", "মুসবাক আদ-দাফ' ওয়ালা ফাতুরা?", "প্রিপেইড নাকি পোস্টপেইড?", "Prepaid or postpaid?"),
            l("আপনি", "مسبق الدفع", "মুসবাক আদ-দাফ'", "প্রিপেইড", "Prepaid"),
            l("আপনি", "كم باقة الإنترنت؟", "কাম বাকাত আল-ইন্টারনেট?", "ইন্টারনেট প্যাক কত?", "How much is the internet package?"),
            l("আপনি", "النت ما يشتغل", "আন-নেত মা ইয়িশতাগিল", "ইন্টারনেট কাজ করছে না", "The internet isn't working"),
            l("স্টাফ", "طف الجوال وشغله", "তাফ আল-জাওয়াল ওয়া শাগ্গিলাহ", "ফোন বন্ধ করে আবার চালু করুন", "Restart the phone")
        )),
        Pack("🕌", "ভদ্রতা / দৈনন্দিন সৌদি ব্যবহার", "ধন্যবাদ, দোয়া, অনুমতি, সম্মান", listOf(
            l("আপনি", "جزاك الله خير", "জাযাক আল্লাহ খাইর", "আল্লাহ আপনাকে উত্তম প্রতিদান দিন", "May God reward you with goodness"),
            l("অন্যজন", "وإياك", "ওয়া ইয়্যাক", "আপনাকেও", "And you too"),
            l("আপনি", "الله يعطيك العافية", "আল্লাহ ইয়াতীক আল-আফিয়া", "আল্লাহ আপনাকে সুস্থতা দিন / ধন্যবাদ", "May God give you wellness / thank you"),
            l("অন্যজন", "الله يعافيك", "আল্লাহ ইয়াআফিক", "আল্লাহ আপনাকেও সুস্থ রাখুন", "May God keep you well too"),
            l("আপনি", "سمحت؟", "সামাহত?", "মাফ করবেন / একটু শুনবেন?", "Excuse me?"),
            l("অন্যজন", "تفضل", "তাফাদ্দাল", "বলুন / আসুন / নিন", "Go ahead / please")
        ))
    )

    private fun l(speaker: String, arabic: String, banglaPron: String, bangla: String, english: String) = Line(speaker, arabic, banglaPron, bangla, english)

    private fun label(name: String, value: String, color: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4))
        addView(text(name, 9.5f, "#7283A8", true)); addView(text(value, 12.8f, color, true).apply { setPadding(0, dp(2), 0, 0) })
    }
    private fun card(bg: String, border: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = stroke(bg, border, 19); elevation = dp(4).toFloat()
    }
    private fun text(v: String, size: Float, color: String, bold: Boolean = false) = TextView(this).apply {
        text = v; textSize = size; setTextColor(Color.parseColor(color)); if (bold) setTypeface(typeface, Typeface.BOLD); includeFontPadding = false
    }
    private fun button(v: String, bg: String, size: Float, action: () -> Unit) = Button(this).apply {
        text = v; isAllCaps = false; textSize = size; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD); background = stroke(bg, lighten(bg), 14); setOnClickListener { action() }
    }
    private fun space(h: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gradient(a: String, b: String) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor(a), Color.parseColor(b)))
    private fun stroke(bg: String, border: String, radius: Int) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(Color.parseColor(bg)); setStroke(dp(1), Color.parseColor(border)) }
    private fun lighten(hex: String): String {
        val c = Color.parseColor(hex); fun f(v: Int) = (v + (255 - v) * 0.18f).toInt().coerceIn(0,255)
        return String.format("#%02X%02X%02X", f(Color.red(c)), f(Color.green(c)), f(Color.blue(c)))
    }
}
