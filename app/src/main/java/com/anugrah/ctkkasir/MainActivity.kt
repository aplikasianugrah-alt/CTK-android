package com.anugrah.ctkkasir

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedDispatcher
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar

class MainActivity : Activity() {

    companion object {
        private const val APP_URL = "https://ctk.aplikasianugrah.workers.dev/"
        private const val APP_HOST = "ctk.aplikasianugrah.workers.dev"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            findViewById<View>(R.id.root_container).setOnApplyWindowInsetsListener { root, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)
        errorView = findViewById(R.id.error_view)
        configureWebView()

        findViewById<Button>(R.id.retry_button).setOnClickListener {
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(APP_URL)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { handleBackNavigation() }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL)
        } else if (webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(APP_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        )

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            userAgentString = "$userAgentString CTKKasirAndroid/1.0"
        }

        webView.addJavascriptInterface(NativeCashierBridge(), "NativeCashier")
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (isTrustedAppUrl(uri.toString()) || uri.scheme in setOf("about", "blob")) {
                    return false
                }
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (isTrustedAppUrl(url)) view.evaluateJavascript(CASHIER_MODE_SCRIPT, null)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) showConnectionError()
            }
        }
    }

    private fun isTrustedAppUrl(url: String?): Boolean {
        val uri = Uri.parse(url ?: return false)
        return uri.scheme == "https" && uri.host.equals(APP_HOST, ignoreCase = true)
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme !in setOf("https", "http", "mailto", "tel", "whatsapp")) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun showConnectionError() {
        progressBar.visibility = View.GONE
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private inner class NativeCashierBridge {
        @JavascriptInterface
        fun printReceipt() {
            runOnUiThread {
                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                val adapter = webView.createPrintDocumentAdapter("Struk CTK Kasir")
                printManager.print(
                    "Struk CTK Kasir",
                    adapter,
                    PrintAttributes.Builder().build()
                )
            }
        }
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("NativeCashier")
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private val CASHIER_MODE_SCRIPT = """
        (() => {
          const normalize = value => (value || '').replace(/\s+/g, ' ').trim().toLowerCase();
          const isCashier = () => normalize(document.querySelector('#pageTitle')?.textContent).includes('kasir');
          const cashierButton = () => [...document.querySelectorAll('#mainNav button, nav button')]
            .find(button => normalize(button.textContent).startsWith('kasir'));

          if (!document.querySelector('#ctk-android-cashier-style')) {
            const style = document.createElement('style');
            style.id = 'ctk-android-cashier-style';
            style.textContent = `
              #sidebar, #sidebarEdgeBtn, #menuBtn { display: none !important; }
              body, .app, .app-shell { padding-left: 0 !important; grid-template-columns: minmax(0, 1fr) !important; }
              main, .main, #mainContent, .content-wrapper {
                grid-column: 1 !important; margin-left: 0 !important;
                width: 100% !important; max-width: none !important; min-width: 0 !important;
              }
              @media (max-width: 760px) {
                .content, main { padding-left: 10px !important; padding-right: 10px !important; }
                header { padding-left: 12px !important; padding-right: 12px !important; }
              }
            `;
            document.head.appendChild(style);
          }

          if (!window.__ctkAndroidCashierInstalled) {
            window.__ctkAndroidCashierInstalled = true;
            let navigationPending = false;

            window.__ctkOpenCashier = () => {
              if (isCashier() || navigationPending) return;
              const button = cashierButton();
              if (!button) return;
              navigationPending = true;
              button.click();
              setTimeout(() => { navigationPending = false; }, 700);
            };

            document.addEventListener('click', event => {
              const button = event.target.closest('#mainNav button, nav button');
              if (!button || normalize(button.textContent).startsWith('kasir')) return;
              event.preventDefault();
              event.stopImmediatePropagation();
              window.__ctkOpenCashier();
            }, true);

            if (window.NativeCashier) {
              window.print = () => window.NativeCashier.printReceipt();
            }

            let scheduled = false;
            new MutationObserver(() => {
              if (scheduled) return;
              scheduled = true;
              requestAnimationFrame(() => {
                scheduled = false;
                window.__ctkOpenCashier();
              });
            }).observe(document.documentElement, { childList: true, subtree: true });
          }

          window.__ctkOpenCashier?.();
        })();
    """.trimIndent()
}
