from pathlib import Path


def req(text: str, old: str, new: str, name: str, count: int = 1) -> str:
    if old not in text:
        raise SystemExit(f'pattern not found: {name}')
    return text.replace(old, new, count)

# ---------------------------------------------------------------------------
# Guide v3.17
# - Show the signed-in Firebase/Google profile photo in the top-bar avatar.
# - Reuse the same account photo in the sidebar brand avatar.
# - Keep the profile initial as a graceful fallback when no photo URL exists.
# - Native async image loading; no extra image-loader dependency required.
# ---------------------------------------------------------------------------
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideFirebaseAvatarV317' not in ms:
    ms = req(
        ms,
        'import android.graphics.Color\nimport android.graphics.Typeface\n',
        'import android.graphics.Bitmap\nimport android.graphics.BitmapFactory\nimport android.graphics.Color\nimport android.graphics.Typeface\n',
        'bitmap imports'
    )
    ms = req(
        ms,
        'import android.widget.FrameLayout\nimport android.widget.LinearLayout\n',
        'import android.widget.FrameLayout\nimport android.widget.ImageView\nimport android.widget.LinearLayout\n',
        'image view import'
    )
    ms = req(
        ms,
        'import androidx.core.content.ContextCompat\n',
        'import androidx.core.content.ContextCompat\nimport com.google.firebase.auth.FirebaseAuth\n',
        'firebase auth import'
    )
    ms = req(
        ms,
        'import java.util.Locale\n',
        'import java.net.URL\nimport java.util.Locale\n',
        'url import'
    )

    ms = req(
        ms,
        '    private var pendingRingtoneResult: ((String) -> Unit)? = null\n',
        '    private var pendingRingtoneResult: ((String) -> Unit)? = null\n    // GuideFirebaseAvatarV317\n    private var cachedProfilePhotoUrl: String? = null\n    private var cachedProfilePhoto: Bitmap? = null\n',
        'avatar cache fields'
    )

    old_top_avatar = '''        val initial = store.profileName().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
        bar.addView(text(initial, 16f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = premiumGradientStroke("#6A56F4", "#66FFFFFF", 1, 15)
            elevation = dp(8).toFloat()
            translationZ = dp(2).toFloat()
            applyDepthPress(8)
            setOnClickListener { openDrawer() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))'''
    new_top_avatar = '''        bar.addView(profileAvatarView(42, 15).apply {
            elevation = dp(8).toFloat()
            translationZ = dp(2).toFloat()
            applyDepthPress(8)
            setOnClickListener { openDrawer() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))'''
    ms = req(ms, old_top_avatar, new_top_avatar, 'top bar Firebase avatar')

    old_sidebar_avatar = '''        brand.addView(text("G", 25f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
            background = premiumGradientStroke("#6350E8", "#B99BFF", 1, 20)
            elevation = dp(10).toFloat()
            translationZ = dp(2).toFloat()
        }, LinearLayout.LayoutParams(dp(58), dp(58)))'''
    new_sidebar_avatar = '''        brand.addView(profileAvatarView(58, 20).apply {
            elevation = dp(10).toFloat()
            translationZ = dp(2).toFloat()
        }, LinearLayout.LayoutParams(dp(58), dp(58)))'''
    ms = req(ms, old_sidebar_avatar, new_sidebar_avatar, 'sidebar Firebase avatar')

    helper_anchor = '''    private fun buildTopBar(): View {\n'''
    avatar_helpers = r'''    private fun profileAvatarView(sizeDp: Int, radiusDp: Int): FrameLayout {
        val initial = store.profileName().trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
        val outer = FrameLayout(this).apply {
            background = premiumGradientStroke("#6350E8", "#B99BFF", 1, radiusDp)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val placeholder = text(initial, if (sizeDp >= 54) 25f else 16f, "#FFFFFF", bold = true).apply {
            gravity = Gravity.CENTER
        }
        outer.addView(
            placeholder,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded("#17213F", maxOf(1, radiusDp - 2))
            clipToOutline = true
            visibility = View.INVISIBLE
            contentDescription = "Account profile photo"
        }
        outer.addView(
            photo,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        loadFirebaseProfilePhoto(photo, placeholder)
        return outer
    }

    private fun loadFirebaseProfilePhoto(target: ImageView, placeholder: TextView) {
        val photoUrl = FirebaseAuth.getInstance().currentUser?.photoUrl?.toString().orEmpty()
        if (photoUrl.isBlank()) return

        val cached = cachedProfilePhoto
        if (cached != null && cachedProfilePhotoUrl == photoUrl) {
            target.setImageBitmap(cached)
            target.visibility = View.VISIBLE
            placeholder.visibility = View.GONE
            return
        }

        Thread {
            val bitmap = runCatching {
                val connection = URL(photoUrl).openConnection().apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    useCaches = true
                }
                connection.getInputStream().use(BitmapFactory::decodeStream)
            }.getOrNull()

            if (bitmap != null) {
                cachedProfilePhotoUrl = photoUrl
                cachedProfilePhoto = bitmap
                target.post {
                    if (!isFinishing && !isDestroyed) {
                        target.setImageBitmap(bitmap)
                        target.visibility = View.VISIBLE
                        placeholder.visibility = View.GONE
                    }
                }
            }
        }.start()
    }

'''
    ms = req(ms, helper_anchor, avatar_helpers + helper_anchor, 'avatar helper methods')

    mp.write_text(ms)
    print('v3.17 Firebase/Google profile avatars applied')
else:
    print('v3.17 avatar patch already applied')

bp = Path('app/build.gradle.kts')
bs = bp.read_text()
bs = bs.replace('versionCode = 29', 'versionCode = 30', 1)
bs = bs.replace('versionName = "3.16.0"', 'versionName = "3.17.0"', 1)
bp.write_text(bs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
cs = cp.read_text().replace('"appVersion" to "3.16.0"', '"appVersion" to "3.17.0"', 1)
cp.write_text(cs)
print('v3.17 version metadata applied')
