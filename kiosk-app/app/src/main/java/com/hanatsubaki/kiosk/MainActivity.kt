package com.hanatsubaki.kiosk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout

class MainActivity : Activity() {

    companion object {
        /** State "0": the menu. Content is updated on the server, not in the APK. */
        const val HOME_URL = "https://android-monitor.onrender.com/"
        private const val BAR_HEIGHT_DP = 56
        private const val COMBO_HOLD_MS = 1200L
        private const val REAL_LAUNCHER = "com.android.launcher3"
    }

    private lateinit var webView: WebView
    private lateinit var backBtn: Button
    private lateinit var homeBtn: Button
    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName

    private var resetOnLoad = false

    // Combo gesture: long-press 戻る + ホーム together -> leave the kiosk.
    private var backDown = false
    private var homeDown = false
    private var comboFired = false
    private val comboHandler = Handler(Looper.getMainLooper())
    private val comboRunnable = Runnable {
        if (backDown && homeDown) {
            comboFired = true
            exitToLauncher()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, KioskAdminReceiver::class.java)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyDeviceOwnerPolicies()
        ensureCameraPermission()

        val density = resources.displayMetrics.density

        webView = WebView(this).apply {
            val s = settings
            s.javaScriptEnabled = true
            s.domStorageEnabled = true
            s.databaseEnabled = true
            s.loadWithOverviewMode = true
            s.useWideViewPort = true
            s.mediaPlaybackRequiresUserGesture = false
            s.cacheMode = WebSettings.LOAD_DEFAULT
            s.setSupportZoom(false)
            s.builtInZoomControls = false
            overScrollMode = View.OVER_SCROLL_NEVER
            webChromeClient = object : WebChromeClient() {
                // Let the web page (記念撮影) use the camera without a system prompt.
                override fun onPermissionRequest(request: PermissionRequest) {
                    val granted = request.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }.toTypedArray()
                    runOnUiThread {
                        if (granted.isNotEmpty() && hasCameraPermission()) {
                            request.grant(granted)
                        } else {
                            request.deny()
                        }
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (resetOnLoad) {
                        resetOnLoad = false
                        view.clearHistory() // collapse the stack back to state "0"
                    }
                    updateBackButton()
                }
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    updateBackButton()
                }
            }
        }

        backBtn = makeBarButton("‹  戻る")
        homeBtn = makeBarButton("⌂  メニュー")

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1B1B1B"))
            val h = (BAR_HEIGHT_DP * density).toInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            val divider = View(this@MainActivity).apply {
                setBackgroundColor(Color.parseColor("#333333"))
                layoutParams = LinearLayout.LayoutParams((1 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
            }
            addView(backBtn)
            addView(divider)
            addView(homeBtn)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                webView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(bar)
        }

        setContentView(rootLayout)

        backBtn.setOnClickListener { if (!comboFired) onBack() }
        homeBtn.setOnClickListener { if (!comboFired) onHome() }
        wireComboTouch(backBtn, isBack = true)
        wireComboTouch(homeBtn, isBack = false)

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
        updateBackButton()
        hideSystemUi()
    }

    private fun makeBarButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    /** Pressing system HOME (this app is the launcher) routes here -> back to state "0". */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        onHome()
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        enterLockTask()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onBackPressed() {
        onBack()
    }

    private fun onBack() {
        if (webView.canGoBack()) webView.goBack()
        // At state "0" -> do nothing (never leave the menu).
    }

    private fun onHome() {
        resetOnLoad = true
        webView.loadUrl(HOME_URL)
    }

    private fun updateBackButton() {
        val canBack = webView.canGoBack()
        backBtn.isEnabled = canBack
        backBtn.alpha = if (canBack) 1f else 0.3f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireComboTouch(v: View, isBack: Boolean) {
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isBack) backDown = true else homeDown = true
                    if (backDown && homeDown) {
                        comboFired = false
                        comboHandler.postDelayed(comboRunnable, COMBO_HOLD_MS)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isBack) backDown = false else homeDown = false
                    comboHandler.removeCallbacks(comboRunnable)
                }
            }
            false // let normal click handling proceed
        }
    }

    private fun hasCameraPermission(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun ensureCameraPermission() {
        if (hasCameraPermission()) return
        if (dpm.isDeviceOwnerApp(packageName)) {
            runCatching {
                dpm.setPermissionGrantState(
                    admin, packageName, Manifest.permission.CAMERA,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            }
        } else {
            runCatching { requestPermissions(arrayOf(Manifest.permission.CAMERA), 1) }
        }
    }

    private fun applyDeviceOwnerPolicies() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        runCatching { dpm.setLockTaskPackages(admin, arrayOf(packageName)) }
        runCatching { dpm.setStatusBarDisabled(admin, true) }
        // NONE: block home, recents, notifications, power/global-actions menu; no keyguard on wake.
        runCatching {
            dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
        }
        runCatching {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, filter, ComponentName(this, MainActivity::class.java)
            )
        }
    }

    private fun enterLockTask() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { startLockTask() }
        }
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    /** Combo gesture / admin exit: drop the kiosk lock and go to the real Android launcher. */
    private fun exitToLauncher() {
        runCatching { stopLockTask() }
        if (dpm.isDeviceOwnerApp(packageName)) {
            runCatching { dpm.setStatusBarDisabled(admin, false) }
            runCatching { dpm.clearPackagePersistentPreferredActivities(admin, packageName) }
        }
        val explicit = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            setPackage(REAL_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val generic = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(explicit) }.onFailure {
            runCatching { startActivity(generic) }
        }
    }
}
