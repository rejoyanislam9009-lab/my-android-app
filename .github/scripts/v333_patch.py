from pathlib import Path
import re


def require(text: str, needle: str, name: str) -> None:
    if needle not in text:
        raise SystemExit(f'pattern not found: {name}')


# Guide v3.33
# - Respect status/navigation bar safe areas on edge-to-edge devices.
# - Let the top bar wrap instead of clipping at a fixed height.
# - Compact horizontal spacing/font sizes on narrow phones.
# - Keep tap animations responsive and avoid continuous decorative animation on
#   low-RAM / very narrow devices, without removing any app feature.
mp = Path('app/src/main/java/com/guide/app/MainActivity.kt')
ms = mp.read_text()

if 'GuideResponsivePerfV333' not in ms:
    # 1) The old 66dp fixed top bar can clip when status-bar/font metrics differ.
    old_top_lp = '        shell.addView(buildTopBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66)))'
    new_top_lp = '''        shell.addView(
            buildTopBar(),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )'''
    require(ms, old_top_lp, 'fixed top bar height')
    ms = ms.replace(old_top_lp, new_top_lp, 1)

    # 2) Adaptive top bar padding/minimum height for compact phones and large font scale.
    old_bar_pad = '            setPadding(dp(12), dp(9), dp(14), dp(9))'
    new_bar_pad = '''            val compact = resources.configuration.screenWidthDp <= 360
            minimumHeight = dp(if (compact) 62 else 66)
            setPadding(dp(if (compact) 8 else 12), dp(8), dp(if (compact) 9 else 14), dp(8))'''
    require(ms, old_bar_pad, 'top bar padding')
    ms = ms.replace(old_bar_pad, new_bar_pad, 1)

    old_title = '        labels.addView(text(screenTitle(), 17f, "#FFFFFF", bold = true))'
    new_title = '        labels.addView(text(screenTitle(), if (resources.configuration.screenWidthDp <= 360) 15.5f else 17f, "#FFFFFF", bold = true).apply { maxLines = 1 })'
    require(ms, old_title, 'top bar screen title')
    ms = ms.replace(old_title, new_title, 1)

    # 3) Safe-area insets for shell and drawer. This fixes the hamburger/profile
    # controls sitting under the status bar on devices with edge-to-edge layouts.
    drawer_anchor = '        frame.addView(drawer, FrameLayout.LayoutParams(drawerWidthPx(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))\n        return frame'
    safe_block = '''        frame.addView(drawer, FrameLayout.LayoutParams(drawerWidthPx(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))

        // GuideResponsivePerfV333 • real system-bar safe area on every phone.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            shell.setPadding(0, bars.top, 0, bars.bottom)
            drawer.setPadding(
                dp(14),
                bars.top + dp(12),
                dp(14),
                bars.bottom + dp(10)
            )
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(frame)
        return frame'''
    require(ms, drawer_anchor, 'drawer/frame return')
    ms = ms.replace(drawer_anchor, safe_block, 1)

    # 4) Compact page side padding. Cards keep the same content/features but no
    # longer crowd or clip on 320-360dp wide phones.
    old_page = '''    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(30))
    }'''
    new_page = '''    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val compact = resources.configuration.screenWidthDp <= 360
        val side = dp(if (compact) 12 else 18)
        setPadding(side, dp(if (compact) 14 else 18), side, dp(30))
    }'''
    require(ms, old_page, 'page padding')
    ms = ms.replace(old_page, new_page, 1)

    # 5) Lightweight mode for narrow/low-RAM devices. Continuous rainbow redraws
    # are decorative only; on lite devices keep the same premium border static.
    helper_anchor = '    private fun buildTopBar(): View {'
    require(ms, helper_anchor, 'buildTopBar helper anchor')
    lite_helper = '''    private fun shouldUseLiteUiV333(): Boolean {
        val lowRam = runCatching {
            (getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
        }.getOrDefault(false)
        return lowRam || resources.configuration.screenWidthDp <= 360
    }

'''
    ms = ms.replace(helper_anchor, lite_helper + helper_anchor, 1)

    old_backdrop = '''        if (isDashboard) {
            frame.addView(
                GuideDashboardBackdropView(this),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }'''
    new_backdrop = '''        if (isDashboard && !shouldUseLiteUiV333()) {
            frame.addView(
                GuideDashboardBackdropView(this),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }'''
    require(ms, old_backdrop, 'dashboard backdrop')
    ms = ms.replace(old_backdrop, new_backdrop, 1)

    border_anchor = '''        target.background = border
        target.elevation = dp(8).toFloat()

        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {'''
    border_new = '''        target.background = border
        target.elevation = dp(8).toFloat()

        if (shouldUseLiteUiV333()) {
            border.setPhase(0.10f)
            return
        }

        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {'''
    require(ms, border_anchor, 'rainbow animator anchor')
    ms = ms.replace(border_anchor, border_new, 1)

    # 6) Shorter press feedback makes controls feel immediate while preserving
    # the premium pressed-state cue.
    ms = ms.replace('.setDuration(75).start()', '.setDuration(if (shouldUseLiteUiV333()) 30 else 55).start()')
    ms = ms.replace('.setDuration(120).start()', '.setDuration(if (shouldUseLiteUiV333()) 55 else 85).start()')

    # 7) Avoid rebuilding the entire screen when the user taps the already-open
    # destination in the drawer.
    old_nav = '''    private fun navigate(tab: String, detail: String? = null) {
        currentTab = tab
        detailPage = detail
        closeDrawer(true)
        render()
    }'''
    new_nav = '''    private fun navigate(tab: String, detail: String? = null) {
        if (currentTab == tab && detailPage == detail) {
            closeDrawer(true)
            return
        }
        currentTab = tab
        detailPage = detail
        closeDrawer(true)
        render()
    }'''
    require(ms, old_nav, 'navigate')
    ms = ms.replace(old_nav, new_nav, 1)

    mp.write_text(ms)
    print('v3.33 responsive safe-area + performance patch applied')
else:
    print('v3.33 MainActivity patch already applied')


# Version metadata.
gp = Path('app/build.gradle.kts')
gs = gp.read_text()
gs = re.sub(r'versionCode = \d+', 'versionCode = 46', gs, count=1)
gs = re.sub(r'versionName = "[^"]+"', 'versionName = "3.33.0"', gs, count=1)
gp.write_text(gs)

cp = Path('app/src/main/java/com/guide/app/CloudSyncManager.kt')
if cp.exists():
    cs = cp.read_text()
    cs = re.sub(r'"appVersion" to "[^"]+"', '"appVersion" to "3.33.0"', cs, count=1)
    cp.write_text(cs)
print('v3.33 version metadata applied')
