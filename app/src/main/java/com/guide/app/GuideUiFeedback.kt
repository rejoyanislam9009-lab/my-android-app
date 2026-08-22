package com.guide.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Premium in-app feedback used by Guide actions.
 * Local/offline features remain available without internet; only explicit
 * cloud/network actions should call requireInternet().
 */
object GuideUiFeedback {
    enum class Kind { SUCCESS, INFO, WARNING, ERROR }

    fun success(context: Context, message: String, title: String = "সফল হয়েছে") =
        show(context, title, message, Kind.SUCCESS)

    fun info(context: Context, message: String, title: String = "Guide") =
        show(context, title, message, Kind.INFO)

    fun warning(context: Context, message: String, title: String = "খেয়াল করুন") =
        show(context, title, message, Kind.WARNING)

    fun error(context: Context, message: String, title: String = "কাজটি সম্পন্ন হয়নি") =
        show(context, title, message, Kind.ERROR)

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    fun requireInternet(context: Context, actionName: String = "এই কাজটি"): Boolean {
        if (isOnline(context)) return true
        show(
            context,
            "ইন্টারনেট সংযোগ নেই",
            "$actionName করতে Wi-Fi বা Mobile Data চালু করে আবার চেষ্টা করুন।",
            Kind.ERROR
        )
        return false
    }

    fun show(context: Context, title: String, message: String, kind: Kind = Kind.INFO) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val (icon, start, end, accent) = when (kind) {
            Kind.SUCCESS -> arrayOf("✓", "#12352E", "#183F37", "#72D8B2")
            Kind.INFO -> arrayOf("i", "#162743", "#1B3156", "#8FB5FF")
            Kind.WARNING -> arrayOf("!", "#3B2B16", "#4B351A", "#F1C27A")
            Kind.ERROR -> arrayOf("!", "#3B1E27", "#4A242E", "#FF9FA7")
        }

        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(start), Color.parseColor(end))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor(accent))
        }

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = bg
            elevation = dp(8).toFloat()
        }
        val badge = TextView(context).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(accent))
            }
        }
        box.addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        labels.addView(TextView(context).apply {
            text = title
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        labels.addView(TextView(context).apply {
            text = message
            textSize = 11.5f
            setTextColor(Color.parseColor("#D6DCEF"))
            setPadding(0, dp(2), 0, 0)
        })
        box.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        Toast(context).apply {
            duration = Toast.LENGTH_LONG
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, dp(78))
            view = box
            show()
        }
    }
}
