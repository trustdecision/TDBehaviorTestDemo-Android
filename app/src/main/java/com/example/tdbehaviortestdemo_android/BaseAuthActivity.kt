package com.TrustDecision.TDBehaviorTestDemo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private class PrivacyWebView(context: Context) : WebView(context) {
    var onPrivacyScrollChanged: (() -> Unit)? = null

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        onPrivacyScrollChanged?.invoke()
    }
}

abstract class BaseAuthActivity : Activity() {
    private var privacyReviewed = false
    private var privacyDialog: Dialog? = null
    private var resultDialog: AlertDialog? = null
    private var resumed = false
    private var pendingResult: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SdkClient.initializeAndStart(applicationContext)
    }

    /** 让页面容器持有初始焦点，避免进入页面时第一个输入框自动聚焦、弹出键盘。 */
    protected fun preventInitialInputFocus() {
        findViewById<View>(android.R.id.content).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    /** 点击当前输入框范围之外时，清除焦点并收起软键盘。 */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val focusedView = currentFocus
            if (focusedView is EditText) {
                val bounds = Rect()
                focusedView.getGlobalVisibleRect(bounds)
                if (!bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    focusedView.clearFocus()
                    findViewById<View>(android.R.id.content).requestFocus()
                    val inputMethodManager =
                        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    protected fun configurePrivacyAgreement(checkBox: CheckBox) {
        checkBox.setOnClickListener {
            if (checkBox.isChecked && !privacyReviewed) {
                checkBox.isChecked = false
                Toast.makeText(
                    this,
                    "Read the Privacy Policy and scroll to the bottom before agreeing.",
                    Toast.LENGTH_LONG
                ).show()
                showPrivacyPolicy(checkBox)
            }
        }
    }

    /** 重置自动化场景，避免进程仍存活时沿用上一次隐私确认状态。 */
    protected fun resetPrivacyAgreement(checkBox: CheckBox) {
        privacyDialog?.dismiss()
        privacyDialog = null
        privacyReviewed = false
        checkBox.isChecked = false
    }

    protected fun showSdkStatus(statusView: TextView) {
        statusView.text = SdkClient.sdkStatus()
    }

    protected fun submitRiskCheck(
        button: Button,
        successTitle: String,
        vararg relatedButtons: Button
    ) {
        val originalText = button.text
        button.isEnabled = false
        relatedButtons.forEach { it.isEnabled = false }
        button.text = getString(R.string.checking_risk)
        SdkClient.collectAndQuery { result ->
            if (isFinishing || isDestroyed) return@collectAndQuery
            button.isEnabled = true
            relatedButtons.forEach { it.isEnabled = true }
            button.text = originalText
            result.fold(
                onSuccess = { deliverResult(successTitle, it) },
                onFailure = {
                    deliverResult(
                        "Unable to complete risk check",
                        it.message ?: it.javaClass.simpleName
                    )
                }
            )
        }
    }

    private fun deliverResult(title: String, content: String) {
        if (resumed) {
            showScrollableResult(title, content)
        } else {
            pendingResult = title to content
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPrivacyPolicy(checkBox: CheckBox) {
        if (privacyDialog?.isShowing == true) return

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // 与 iOS TDPrivacyViewController 保持相同层级：
        // 58dp 顶栏、状态提示、可滚动网页、底部 52dp 蓝色 Agree 按钮。
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58)
            )
        }
        val title = TextView(this).apply {
            text = getString(R.string.privacy_title)
            setTextColor(getColor(R.color.td_text))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        val close = Button(this).apply {
            text = getString(R.string.close)
            isAllCaps = false
            setTextColor(getColor(R.color.td_blue))
            setBackgroundColor(Color.TRANSPARENT)
            minWidth = 0
            minHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply { marginStart = dp(8) }
        }
        val status = TextView(this).apply {
            text = getString(R.string.privacy_loading)
            setTextColor(getColor(R.color.td_muted))
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        }
        val webView = PrivacyWebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.WHITE)
        }
        val agree = Button(this).apply {
            text = getString(R.string.agree)
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundResource(R.drawable.bg_privacy_agree_button)
            isEnabled = false
            alpha = 0.45f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                marginStart = dp(20)
                marginEnd = dp(20)
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        }

        header.addView(title)
        header.addView(close)
        root.addView(header)
        root.addView(status)
        root.addView(webView)
        root.addView(agree)

        var pageLoaded = false
        var pageLoadFailed = false
        val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar).apply {
            setContentView(root)
            setCancelable(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        }
        privacyDialog = dialog

        fun updateReachedBottom() {
            if (pageLoaded && !webView.canScrollVertically(1)) {
                status.text = getString(R.string.privacy_reached_end)
                agree.isEnabled = true
                agree.alpha = 1f
            }
        }

        // 通过 WebView.onScrollChanged 回调，避免依赖 API 23 才加入的
        // View.setOnScrollChangeListener，兼容 Android 5。
        webView.onPrivacyScrollChanged = { updateReachedBottom() }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // WebView 加载错误页后仍可能回调 onPageFinished，不能因此开放 Agree。
                if (pageLoadFailed) return
                pageLoaded = true
                status.text = getString(R.string.privacy_scroll_hint)
                view.postDelayed({ updateReachedBottom() }, 300)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    pageLoadFailed = true
                    pageLoaded = false
                    status.text = getString(
                        R.string.privacy_load_failed_detail,
                        error.description
                    )
                    agree.isEnabled = false
                    agree.alpha = 0.45f
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (request.isForMainFrame) {
                    pageLoadFailed = true
                    pageLoaded = false
                    status.text = getString(
                        R.string.privacy_load_failed_detail,
                        "HTTP ${errorResponse.statusCode}"
                    )
                    agree.isEnabled = false
                    agree.alpha = 0.45f
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                pageLoadFailed = true
                pageLoaded = false
                status.text = getString(
                    R.string.privacy_load_failed_detail,
                    error.toString()
                )
                agree.isEnabled = false
                agree.alpha = 0.45f
            }
        }

        close.setOnClickListener { dialog.dismiss() }
        agree.setOnClickListener {
            privacyReviewed = true
            checkBox.isChecked = true
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            agree.isEnabled = false
            agree.alpha = 0.45f
            pageLoadFailed = false
            webView.loadUrl(DemoConfig.PRIVACY_POLICY_URL)
        }
        dialog.setOnDismissListener {
            webView.stopLoading()
            webView.destroy()
            privacyDialog = null
        }
        dialog.show()
    }

    private fun showScrollableResult(title: String, content: String) {
        val density = resources.displayMetrics.density
        val resultText = TextView(this).apply {
            text = content
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(getColor(R.color.td_text))
            setTextIsSelectable(true)
            setPadding((18 * density).toInt(), (12 * density).toInt(),
                (18 * density).toInt(), (20 * density).toInt())
        }
        val scrollView = ScrollView(this).apply {
            addView(resultText)
        }
        resultDialog?.dismiss()
        resultDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.done, null)
            .create()
            .also { dialog ->
                dialog.setOnDismissListener {
                    if (resultDialog === dialog) resultDialog = null
                }
                dialog.show()
            }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        pendingResult?.let { (title, content) ->
            pendingResult = null
            showScrollableResult(title, content)
        }
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        privacyDialog?.dismiss()
        privacyDialog = null
        resultDialog?.dismiss()
        resultDialog = null
        super.onDestroy()
    }
}
