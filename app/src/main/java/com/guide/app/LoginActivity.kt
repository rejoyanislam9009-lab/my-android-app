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
import android.widget.ScrollView
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

    private fun buildScreen(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(54), dp(24), dp(40))
            background = gradient("#090E1C", "#121A34")
        }

        root.addView(TextView(this).apply {
            text = "G"
            gravity = Gravity.CENTER
            textSize = 35f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = gradientCard("#7457FF", "#5B8CFF", 26)
            elevation = dp(10).toFloat()
        }, LinearLayout.LayoutParams(dp(82), dp(82)))

        root.addView(space(26))
        root.addView(TextView(this).apply {
            text = if (store.hasProfile()) "Welcome back" else "Welcome to Guide"
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (store.hasProfile()) "Continue where you left off." else "Plan your day. Track your progress. Stay on time."
            textSize = 15f
            setTextColor(Color.parseColor("#A7B2D4"))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(30))
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded("#17203D", 24)
            elevation = dp(5).toFloat()
        }

        card.addView(TextView(this).apply {
            text = if (store.hasProfile()) "SECURE ACCESS" else "CREATE YOUR PROFILE"
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#8D9BC5"))
            setPadding(dp(2), 0, 0, dp(12))
        })

        val name = EditText(this).apply {
            hint = "Your name"
            setHintTextColor(Color.parseColor("#7582A9"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(dp(18), 0, dp(18), 0)
            background = rounded("#0F1730", 15)
            if (store.hasProfile()) {
                setText(store.profileName())
                isEnabled = false
                alpha = 0.78f
            }
        }
        card.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        card.addView(space(14))

        val pin = EditText(this).apply {
            hint = "4-digit PIN"
            setHintTextColor(Color.parseColor("#7582A9"))
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(dp(18), 0, dp(18), 0)
            background = rounded("#0F1730", 15)
        }
        card.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))
        card.addView(space(18))

        card.addView(Button(this).apply {
            text = if (store.hasProfile()) "Open Guide" else "Create Guide"
            isAllCaps = false
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = gradientCard("#7457FF", "#5F7CFF", 15)
            setOnClickListener {
                val enteredPin = pin.text.toString()
                if (enteredPin.length != 4 || enteredPin.any { !it.isDigit() }) {
                    Toast.makeText(this@LoginActivity, "Enter a valid 4-digit PIN", Toast.LENGTH_SHORT).show()
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
                    ReminderScheduler.ensureChannel(this@LoginActivity)
                    ReminderScheduler.scheduleAll(this@LoginActivity, store)
                }
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(space(22))
        root.addView(TextView(this).apply {
            text = "Private workspace • Protected by your PIN"
            textSize = 12f
            setTextColor(Color.parseColor("#7380A7"))
            gravity = Gravity.CENTER
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
    }

    private fun rounded(hex: String, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(hex))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun gradientCard(start: String, end: String, radiusDp: Int) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(start), Color.parseColor(end))
    ).apply { cornerRadius = dp(radiusDp).toFloat() }

    private fun gradient(top: String, bottom: String) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor(top), Color.parseColor(bottom))
    )

    private fun space(height: Int) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
