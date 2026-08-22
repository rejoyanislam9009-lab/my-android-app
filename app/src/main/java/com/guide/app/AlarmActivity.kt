package com.guide.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : AppCompatActivity() {
    private var alarmKey: String = "guide_alarm"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        alarmKey = intent.getStringExtra("key") ?: "guide_alarm"
        setContentView(buildUi(intent))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stopAndFinish()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        alarmKey = intent.getStringExtra("key") ?: "guide_alarm"
        setContentView(buildUi(intent))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_POWER) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_CAMERA,
                KeyEvent.KEYCODE_HEADSETHOOK,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    stopAndFinish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun buildUi(source: Intent): LinearLayout {
        val title = source.getStringExtra("title") ?: "Guide অ্যালার্ম"
        val body = source.getStringExtra("body") ?: "আপনার নির্ধারিত সময় হয়েছে"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(72), dp(28), dp(36))
            background = gradient("#07101F", "#141C3D")
        }

        root.addView(TextView(this).apply {
            text = "⏰"
            textSize = 58f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(120), dp(120)))

        root.addView(TextView(this).apply {
            text = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
            textSize = 43f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = title
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(8))
        })
        root.addView(TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(Color.parseColor("#AAB6D7"))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "ভলিউম বা Back বাটন চাপলেও অ্যালার্ম বন্ধ হবে"
            textSize = 13f
            setTextColor(Color.parseColor("#8290B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(30))
        })

        root.addView(Button(this).apply {
            text = "অ্যালার্ম বন্ধ করুন"
            isAllCaps = false
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded("#6B55F7", 18)
            setOnClickListener { stopAndFinish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)))

        root.addView(Button(this).apply {
            text = "Guide খুলুন"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded("#202D50", 16)
            setOnClickListener {
                stopAlarm()
                startActivity(Intent(this@AlarmActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                finish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(12) })
        return root
    }

    private fun stopAndFinish() {
        stopAlarm()
        finish()
    }

    private fun stopAlarm() {
        AlarmSoundPlayer.stop()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(alarmKey.hashCode())
        sendBroadcast(Intent(this, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_STOP_ALARM
            putExtra("key", alarmKey)
        })
    }

    private fun rounded(hex: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
