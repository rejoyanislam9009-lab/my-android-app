package com.guide.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    private lateinit var store: GuideStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuideStore(this)
        setContentView(buildScreen())
    }

    private fun buildScreen(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(64), dp(24), dp(32))
            background = gradient("#0B1020", "#151B36")
        }

        root.addView(TextView(this).apply {
            text = "G"
            gravity = Gravity.CENTER
            textSize = 34f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded("#7C5CFC", 28f)
        }, LinearLayout.LayoutParams(dp(72), dp(72)))

        root.addView(space(24))
        root.addView(TextView(this).apply {
            text = if (store.hasProfile()) "Welcome back" else "Welcome to Guide"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (store.hasProfile()) "Your day, organized beautifully." else "Build better days with routines, meals and reminders."
            textSize = 15f
            setTextColor(Color.parseColor("#AEB7D5"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(28))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded("#18203C", 22f)
        }

        val name = EditText(this).apply {
            hint = "Your name"
            setHintTextColor(Color.parseColor("#7782A7"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded("#10172D", 14f)
            if (store.hasProfile()) {
                setText(store.profileName())
                isEnabled = false
            }
        }
        card.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        card.addView(space(14))

        val pin = EditText(this).apply {
            hint = "4-digit PIN"
            setHintTextColor(Color.parseColor("#7782A7"))
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded("#10172D", 14f)
        }
        card.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        card.addView(space(18))

        val action = Button(this).apply {
            text = if (store.hasProfile()) "Open dashboard" else "Create my Guide"
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.WHITE)
            background = rounded("#7C5CFC", 14f)
            setOnClickListener {
                val enteredPin = pin.text.toString()
                if (enteredPin.length != 4 || enteredPin.any { !it.isDigit() }) {
                    Toast.makeText(this@LoginActivity, "Use a 4-digit PIN", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (store.hasProfile()) {
                    if (!store.verifyPin(enteredPin)) {
                        Toast.makeText(this@LoginActivity, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                } else {
                    val enteredName = name.text.toString().trim()
                    if (enteredName.length < 2) {
                        Toast.makeText(this@LoginActivity, "Enter your name", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    store.saveProfile(enteredName, enteredPin)
                    store.seedDefaultsIfNeeded()
                    ReminderScheduler.scheduleAll(this@LoginActivity, store)
                }
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        }
        card.addView(action, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(space(22))
        root.addView(TextView(this).apply {
            text = "🔒 Offline-first • No paid API required"
            textSize = 13f
            setTextColor(Color.parseColor("#7F8BB1"))
            gravity = Gravity.CENTER
        })
        return root
    }

    private fun rounded(hex: String, radiusDp: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun space(height: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
