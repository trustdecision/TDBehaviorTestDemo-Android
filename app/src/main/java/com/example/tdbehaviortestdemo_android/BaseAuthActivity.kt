package com.TrustDecision.TDBehaviorTestDemo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.net.http.SslError
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

abstract class BaseAuthActivity : Activity() {
    private var privacyReviewed = false
    private var privacyDialog: AlertDialog? = null
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
        val webViewHeight = (resources.displayMetrics.heightPixels * 0.58f).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(),
                (16 * density).toInt(), 0)
        }
        val status = TextView(this).apply {
            text = getString(R.string.privacy_loading)
            setTextColor(getColor(R.color.td_muted))
            textSize = 13f
            setPadding(0, 0, 0, (8 * density).toInt())
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        val webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                webViewHeight
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.WHITE)
        }
        container.addView(status)
        container.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (3 * density).toInt()
        ))
        container.addView(webView)

        var pageLoaded = false
        var pageLoadFailed = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.privacy_title)
            .setView(container)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.agree) { _, _ ->
                privacyReviewed = true
                checkBox.isChecked = true
            }
            .create()
        privacyDialog = dialog

        fun updateReachedBottom() {
            if (pageLoaded && !webView.canScrollVertically(1)) {
                status.text = getString(R.string.privacy_reached_end)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
            }
        }

        webView.setOnScrollChangeListener { _, _, _, _, _ -> updateReachedBottom() }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // WebView 加载错误页后仍可能回调 onPageFinished，不能因此开放 Agree。
                if (pageLoadFailed) return
                pageLoaded = true
                progress.visibility = View.GONE
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
                    progress.visibility = View.GONE
                    status.text = getString(
                        R.string.privacy_load_failed_detail,
                        error.description
                    )
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
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
                    progress.visibility = View.GONE
                    status.text = getString(
                        R.string.privacy_http_error,
                        errorResponse.statusCode
                    )
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
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
                progress.visibility = View.GONE
                status.text = getString(R.string.privacy_ssl_error)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
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
