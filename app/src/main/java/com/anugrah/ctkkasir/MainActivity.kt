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
import android.webkit.ValueCallback
import android.widget.Button
import android.widget.ProgressBar

class MainActivity : Activity() {

    companion object {
        private const val APP_URL = "https://aksesoris.anugrahsatu.com/"
        private const val APP_HOST = "aksesoris.anugrahsatu.com"
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: View
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

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

            @Suppress("DEPRECATION")
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                return try {
                    val intent = fileChooserParams.createIntent().apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                    true
                } catch (_: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    false
                }
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileChooserCallback?.onReceiveValue(result)
            fileChooserCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
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
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
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

          if ((window.__ctkAndroidCashierInstalled || window.__ctkAndroidCashierV2Installed ||
              window.__ctkAndroidCashierV3Installed || window.__ctkAndroidCashierV4Installed) &&
              !window.__ctkAndroidCashierV5Installed) {
            location.reload();
            return;
          }

          let style = document.querySelector('#ctk-android-cashier-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'ctk-android-cashier-style';
            document.head.appendChild(style);
          }
          style.textContent = `
            #menuBtn {
              display: inline-flex !important;
              align-items: center !important;
              justify-content: center !important;
            }
            .ctk-category-wrap {
              display: flex;
              gap: 8px;
              overflow-x: auto;
              padding: 12px 0 8px;
              scrollbar-width: none;
              -webkit-overflow-scrolling: touch;
            }
            .ctk-category-wrap::-webkit-scrollbar { display: none; }
            .ctk-category-chip {
              flex: 0 0 auto;
              border: 1px solid #d8d2ca;
              border-radius: 999px;
              background: #fff;
              color: #493a33;
              min-height: 38px;
              padding: 8px 14px;
              font: inherit;
              font-weight: 700;
              cursor: pointer;
            }
            .ctk-category-chip.active {
              border-color: #2f6c50;
              background: #2f6c50;
              color: #fff;
            }
            .product-tile.ctk-hidden { display: none !important; }
            .product-stock-pill {
              background: #087a5b !important;
              color: #ffffff !important;
              border: 1px solid rgba(255, 255, 255, .55) !important;
              padding: 6px 9px !important;
              font-size: 10px !important;
              font-weight: 900 !important;
              line-height: 1.1 !important;
              letter-spacing: .01em !important;
              text-shadow: 0 1px 2px rgba(0, 0, 0, .3) !important;
              box-shadow: 0 4px 12px rgba(4, 70, 52, .26) !important;
              white-space: nowrap !important;
            }
            .product-stock-pill.low-stock {
              background: #a95d00 !important;
              color: #ffffff !important;
              box-shadow: 0 4px 12px rgba(114, 61, 0, .28) !important;
            }
            .product-stock-pill.empty-stock {
              background: #b52f35 !important;
              color: #ffffff !important;
              box-shadow: 0 4px 12px rgba(114, 21, 27, .28) !important;
            }
            .ctk-pagination {
              display: flex;
              align-items: center;
              justify-content: center;
              flex-wrap: wrap;
              gap: 6px;
              padding: 18px 0 4px;
            }
            .ctk-page-button {
              min-width: 38px;
              min-height: 38px;
              border: 1px solid #d8d2ca;
              border-radius: 10px;
              background: #fff;
              color: #493a33;
              font: inherit;
              font-weight: 700;
              cursor: pointer;
            }
            .ctk-page-button.active {
              border-color: #2f6c50;
              background: #2f6c50;
              color: #fff;
            }
            .ctk-page-button:disabled { cursor: default; opacity: .45; }
            .ctk-page-gap { padding: 0 3px; color: #756b65; }

            html { -webkit-text-size-adjust: 100%; }
            body { overflow-x: hidden !important; }
            .app-shell, .main, .page-content, .toolbar, .toolbar-left,
            .toolbar-right, .card, .table-wrap { min-width: 0 !important; }
            .table-wrap {
              max-width: 100% !important;
              overscroll-behavior-inline: contain;
              -webkit-overflow-scrolling: touch;
            }
            .data-table th, .data-table td {
              word-break: normal !important;
              overflow-wrap: normal !important;
              hyphens: none !important;
            }

            @media (min-width: 701px) and (max-width: 1200px) {
              .page-content {
                width: 100% !important;
                padding: 24px 20px 116px !important;
              }
              .stats-grid, .sales-summary-grid, .mini-metrics, .purchase-summary {
                grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
                gap: 14px !important;
              }
              .content-grid, .settings-workspace, .pos-layout {
                grid-template-columns: minmax(0, 1fr) !important;
              }
              .toolbar, .toolbar-left, .toolbar-right, .card-head,
              .sales-ledger-head, .dashboard-title {
                flex-wrap: wrap !important;
              }
              .table-wrap { overflow-x: auto !important; }
              .data-table {
                min-width: 820px !important;
                width: max-content !important;
                table-layout: auto !important;
              }
              .data-table th, .data-table td {
                min-width: 110px;
                white-space: normal !important;
                line-height: 1.45 !important;
              }
              .report-chart .line-chart svg {
                min-height: 260px !important;
                max-height: 360px !important;
              }
              .purchase-actions { flex-wrap: wrap !important; }
              .purchase-actions .btn { flex: 1 1 170px !important; }
            }

            @media (max-width: 700px) {
              body { font-size: 15px !important; }
              .main { width: 100% !important; }
              .topbar {
                min-height: 68px !important;
                height: auto !important;
                padding: 10px 12px !important;
                gap: 10px !important;
              }
              .topbar h2 { font-size: 21px !important; line-height: 1.2 !important; }
              .topbar p { display: none !important; }
              .top-actions { margin-left: auto !important; }
              .page-content {
                width: 100% !important;
                padding: 16px 12px calc(112px + env(safe-area-inset-bottom)) !important;
              }
              .toolbar:not(.dashboard-filter), .dashboard-filter {
                display: flex !important;
                flex-direction: column !important;
                align-items: stretch !important;
                padding: 16px !important;
                gap: 12px !important;
              }
              .toolbar-left, .toolbar-right, .dashboard-filter .toolbar-right,
              .sales-hero, .sales-hero-actions, .sales-period,
              .sales-ledger-head, .sales-filters, .dashboard-title,
              .card-head, .purchase-card-head {
                display: flex !important;
                flex-direction: column !important;
                align-items: stretch !important;
                width: 100% !important;
                gap: 10px !important;
              }
              .toolbar-right > *, .toolbar-right .filter-input,
              .toolbar-right .search, .toolbar-right .search input,
              .sales-filters > *, .sales-filters .search input,
              .dashboard-filter .filter-input, .sales-period .filter-input {
                width: 100% !important;
                min-width: 0 !important;
              }
              input, select, textarea, .filter-input {
                min-height: 46px !important;
                font-size: 16px !important;
                line-height: 1.35 !important;
              }
              .btn { min-height: 44px !important; white-space: normal !important; }
              .stats-grid, .sales-summary-grid, .mini-metrics,
              .purchase-summary, .content-grid, .settings-workspace,
              .pos-layout {
                grid-template-columns: minmax(0, 1fr) !important;
                gap: 12px !important;
              }
              .stat-card {
                min-height: 122px !important;
                padding: 18px !important;
              }
              .stat-card strong { margin-top: 17px !important; font-size: 24px !important; }
              .card, .stat-card { max-width: 100% !important; }
              .form-grid { grid-template-columns: minmax(0, 1fr) !important; }
              .form-grid > *, .form-grid .full { grid-column: 1 !important; }
              .form-actions {
                display: grid !important;
                grid-template-columns: minmax(0, 1fr) !important;
                gap: 8px !important;
              }
              .form-actions .btn { width: 100% !important; }
              .modal { padding: 8px !important; align-items: end !important; }
              .modal-card {
                width: 100% !important;
                max-height: calc(100dvh - 16px) !important;
                padding: 20px 16px !important;
                border-radius: 20px 20px 12px 12px !important;
              }

              .table-wrap {
                max-width: 100% !important;
                overflow-x: auto !important;
                margin: 0 !important;
                border-radius: 0 !important;
              }
              .table-wrap.ctk-responsive-table-wrap { overflow: visible !important; }
              .data-table {
                display: block !important;
                width: 100% !important;
                min-width: 0 !important;
                table-layout: auto !important;
                border-collapse: separate !important;
              }
              .data-table thead {
                position: absolute !important;
                width: 1px !important;
                height: 1px !important;
                overflow: hidden !important;
                clip: rect(0 0 0 0) !important;
                white-space: nowrap !important;
              }
              .data-table tbody {
                display: grid !important;
                gap: 12px !important;
                width: 100% !important;
              }
              .data-table tbody tr {
                display: block !important;
                width: 100% !important;
                padding: 5px 14px !important;
                border: 1px solid rgba(76, 99, 145, .18) !important;
                border-radius: 14px !important;
                background: rgba(255, 255, 255, .48) !important;
                box-shadow: 0 5px 16px rgba(18, 38, 73, .06) !important;
              }
              .data-table tbody td {
                display: grid !important;
                grid-template-columns: minmax(96px, 35%) minmax(0, 1fr) !important;
                align-items: start !important;
                gap: 12px !important;
                width: 100% !important;
                min-width: 0 !important;
                padding: 10px 0 !important;
                border: 0 !important;
                border-bottom: 1px solid rgba(76, 99, 145, .12) !important;
                font-size: 14px !important;
                line-height: 1.45 !important;
                text-align: left !important;
                white-space: normal !important;
                overflow-wrap: break-word !important;
              }
              .data-table tbody td:last-child { border-bottom: 0 !important; }
              .data-table tbody td::before {
                content: attr(data-label);
                color: #536782;
                font-size: 10px;
                font-weight: 900;
                letter-spacing: .08em;
                line-height: 1.35;
                text-transform: uppercase;
              }
              .data-table tbody td[data-label=""] {
                grid-template-columns: minmax(0, 1fr) !important;
              }
              .data-table tbody td[data-label=""]::before { display: none !important; }
              .data-table tbody td:empty { display: none !important; }
              .data-table tbody td > * { min-width: 0 !important; max-width: 100% !important; }
              .data-table .badge {
                width: max-content !important;
                max-width: 100% !important;
                white-space: normal !important;
                text-align: center !important;
              }
              .data-table .user-actions {
                display: grid !important;
                grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
                gap: 8px !important;
              }
              .data-table .user-actions .btn { width: 100% !important; }
              .data-table tfoot { display: block !important; padding-top: 10px !important; }

              .report-chart { overflow: hidden !important; padding: 16px !important; }
              .report-chart .line-chart { overflow-x: auto !important; }
              .report-chart .line-chart svg {
                width: 100% !important;
                height: 230px !important;
                min-height: 230px !important;
                max-height: 230px !important;
              }
              .chart-legend { margin: 10px 0 0 !important; justify-content: flex-start !important; }
              .report-list { gap: 12px !important; align-items: flex-start !important; }

              .purchase-card { width: 100% !important; overflow: hidden !important; }
              .purchase-card-head { padding: 16px !important; }
              .purchase-status {
                display: grid !important;
                grid-template-columns: minmax(0, 1fr) !important;
                width: 100% !important;
                gap: 8px !important;
                text-align: left !important;
              }
              .purchase-status > * {
                max-width: 100% !important;
                margin: 0 !important;
                white-space: normal !important;
              }
              .purchase-actions {
                display: grid !important;
                grid-template-columns: minmax(0, 1fr) !important;
                padding: 12px 16px 16px !important;
                gap: 8px !important;
              }
              .purchase-actions .btn { width: 100% !important; min-width: 0 !important; }

              .ctk-category-chip { min-height: 38px; padding: 8px 13px; }
              .ctk-pagination { padding-bottom: 10px !important; }
              .bottom-nav {
                min-height: 76px !important;
                height: calc(76px + env(safe-area-inset-bottom)) !important;
              }
              .bottom-nav button { min-width: 58px !important; min-height: 52px !important; }
              .bottom-nav small { font-size: 10px !important; }
            }

            @media (max-width: 390px) {
              .page-content { padding-left: 9px !important; padding-right: 9px !important; }
              .data-table tbody td {
                grid-template-columns: minmax(82px, 34%) minmax(0, 1fr) !important;
                gap: 9px !important;
                font-size: 13px !important;
              }
              .data-table tbody td::before { font-size: 9px !important; }
            }
          `;

          const categoryRules = [
            ['Cincin', /cincin|ring/],
            ['Bando', /bando|headband|bandana/],
            ['Anting', /anting|earring/],
            ['Gelang', /gelang|bracelet/],
            ['Kalung', /kalung|necklace/],
            ['Jepit', /jepit|hair clip|clip rambut/],
            ['Ikat Rambut', /ikat rambut|kuncir|scrunchie|hair tie/],
            ['Bros', /bros|brooch/],
            ['Tas', /\btas\b|bag/],
            ['Dompet', /dompet|wallet/],
            ['Gantungan Kunci', /gantungan|g kunci|keychain/],
            ['Jam', /\bjam\b|watch/],
            ['Kacamata', /kacamata|sunglass/]
          ];
          const categoryOf = name => {
            const value = normalize(name);
            return categoryRules.find(entry => entry[1].test(value))?.[0] || 'Lainnya';
          };
          const catalogState = window.__ctkCatalogState || {
            category: 'Semua', page: 1, perPage: 12, itemsSignature: ''
          };
          window.__ctkCatalogState = catalogState;

          const createButton = (text, className, onClick) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = className;
            button.textContent = text;
            button.addEventListener('click', onClick);
            return button;
          };

          const drawCategoryBar = categories => {
            const toolbar = document.querySelector('.pos-catalog-toolbar');
            if (!toolbar) return;
            let bar = document.querySelector('#ctkCategoryBar');
            if (!bar) {
              bar = document.createElement('div');
              bar.id = 'ctkCategoryBar';
              bar.className = 'ctk-category-wrap';
              bar.setAttribute('aria-label', 'Kategori produk');
              toolbar.insertAdjacentElement('afterend', bar);
            }
            const signature = categories.join('|') + '::' + catalogState.category;
            if (bar.dataset.signature === signature) return;
            bar.dataset.signature = signature;
            bar.replaceChildren();
            categories.forEach(category => {
              const button = createButton(category, 'ctk-category-chip', () => {
                catalogState.category = category;
                catalogState.page = 1;
                window.__ctkApplyCatalog?.();
              });
              button.classList.toggle('active', category === catalogState.category);
              button.setAttribute('aria-pressed', String(category === catalogState.category));
              bar.appendChild(button);
            });
          };

          const pageNumbers = total => {
            if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1);
            const current = catalogState.page;
            const values = [1];
            if (current > 3) values.push('…');
            for (let page = Math.max(2, current - 1); page <= Math.min(total - 1, current + 1); page++) {
              values.push(page);
            }
            if (current < total - 2) values.push('…');
            values.push(total);
            return values;
          };

          const drawPagination = (grid, totalItems, totalPages) => {
            let pager = document.querySelector('#ctkPagination');
            if (!pager) {
              pager = document.createElement('nav');
              pager.id = 'ctkPagination';
              pager.className = 'ctk-pagination';
              pager.setAttribute('aria-label', 'Halaman katalog');
              grid.insertAdjacentElement('afterend', pager);
            }
            const signature = [totalItems, totalPages, catalogState.page].join('|');
            if (pager.dataset.signature === signature) return;
            pager.dataset.signature = signature;
            pager.replaceChildren();
            if (totalPages <= 1) {
              pager.hidden = true;
              return;
            }
            pager.hidden = false;
            const previous = createButton('‹', 'ctk-page-button', () => {
              catalogState.page = Math.max(1, catalogState.page - 1);
              window.__ctkApplyCatalog?.();
              grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
            previous.disabled = catalogState.page === 1;
            previous.setAttribute('aria-label', 'Halaman sebelumnya');
            pager.appendChild(previous);
            pageNumbers(totalPages).forEach(value => {
              if (value === '…') {
                const gap = document.createElement('span');
                gap.className = 'ctk-page-gap';
                gap.textContent = value;
                pager.appendChild(gap);
                return;
              }
              const button = createButton(String(value), 'ctk-page-button', () => {
                catalogState.page = value;
                window.__ctkApplyCatalog?.();
                grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
              });
              button.classList.toggle('active', value === catalogState.page);
              button.setAttribute('aria-current', value === catalogState.page ? 'page' : 'false');
              pager.appendChild(button);
            });
            const next = createButton('›', 'ctk-page-button', () => {
              catalogState.page = Math.min(totalPages, catalogState.page + 1);
              window.__ctkApplyCatalog?.();
              grid.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
            next.disabled = catalogState.page === totalPages;
            next.setAttribute('aria-label', 'Halaman berikutnya');
            pager.appendChild(next);
          };

          window.__ctkApplyCatalog = () => {
            const grid = document.querySelector('#productGrid');
            if (!grid) return;
            const tiles = [...grid.querySelectorAll('.product-tile')];
            tiles.forEach(tile => {
              const stock = tile.querySelector('.product-stock-pill');
              if (!stock) return;
              const current = normalize(stock.textContent);
              const amount = current.match(/\d+/)?.[0];
              stock.textContent = stock.classList.contains('empty-stock') || current.includes('habis')
                ? 'Stok habis'
                : amount ? 'Stok: ' + amount : stock.textContent;
            });
            const namedTiles = tiles.map(tile => {
              const name = tile.querySelector('.product-tile-info b')?.textContent || '';
              return { tile, name, category: categoryOf(name) };
            });
            const itemsSignature = namedTiles.map(item => item.name).join('|');
            if (itemsSignature !== catalogState.itemsSignature) {
              catalogState.itemsSignature = itemsSignature;
              catalogState.page = 1;
            }
            const order = categoryRules.map(entry => entry[0]).concat('Lainnya');
            const available = new Set(namedTiles.map(item => item.category));
            const categories = ['Semua'].concat(order.filter(category => available.has(category)));
            if (!categories.includes(catalogState.category)) catalogState.category = 'Semua';
            drawCategoryBar(categories);

            const filtered = catalogState.category === 'Semua'
              ? namedTiles
              : namedTiles.filter(item => item.category === catalogState.category);
            const totalPages = Math.max(1, Math.ceil(filtered.length / catalogState.perPage));
            catalogState.page = Math.min(Math.max(1, catalogState.page), totalPages);
            const start = (catalogState.page - 1) * catalogState.perPage;
            const visible = new Set(filtered.slice(start, start + catalogState.perPage).map(item => item.tile));
            tiles.forEach(tile => tile.classList.toggle('ctk-hidden', !visible.has(tile)));

            const resultCount = document.querySelector('#posResultCount');
            if (resultCount) {
              resultCount.textContent = filtered.length + ' hasil · Halaman ' + catalogState.page + '/' + totalPages;
            }
            drawPagination(grid, filtered.length, totalPages);

            const search = document.querySelector('#posSearch');
            if (search && !search.dataset.ctkPaginationBound) {
              search.dataset.ctkPaginationBound = '1';
              search.addEventListener('input', () => { catalogState.page = 1; }, { capture: true });
            }
          };

          window.__ctkApplyResponsiveTables = () => {
            document.querySelectorAll('table.data-table').forEach(table => {
              table.closest('.table-wrap')?.classList.add('ctk-responsive-table-wrap');
              const headers = [...table.querySelectorAll('thead th')]
                .map(header => normalize(header.textContent));
              table.querySelectorAll('tbody tr').forEach(row => {
                let headerIndex = 0;
                [...row.children].forEach(cell => {
                  const fallback = cell.querySelector('button, .user-actions') ? 'Aksi' : '';
                  const label = headers[headerIndex] || fallback;
                  cell.dataset.label = label;
                  headerIndex += Math.max(1, Number(cell.colSpan || 1));
                });
              });
            });
          };

          const transactionRefresh = window.__ctkTransactionRefresh || {
            pending: false,
            skipCashier: false
          };
          window.__ctkTransactionRefresh = transactionRefresh;

          window.__ctkApplyTransactionRefresh = () => {
            const receipt = document.querySelector('#modal .receipt');
            const success = receipt && normalize(receipt.textContent).includes('pembayaran berhasil');
            if (success && !receipt.dataset.ctkTransactionHandled) {
              receipt.dataset.ctkTransactionHandled = '1';
              transactionRefresh.pending = true;
              transactionRefresh.skipCashier = false;

              const clearCart = document.querySelector('#clearCart:not([disabled])');
              clearCart?.click();

              const viewSale = receipt.querySelector('#viewSale');
              viewSale?.addEventListener('click', () => {
                transactionRefresh.skipCashier = true;
              }, { capture: true, once: true });
            }

            const modal = document.querySelector('#modal');
            if (modal && !modal.dataset.ctkRefreshObserver) {
              modal.dataset.ctkRefreshObserver = '1';
              new MutationObserver(() => {
                if (!transactionRefresh.pending || !modal.classList.contains('hidden')) return;
                const skip = transactionRefresh.skipCashier;
                transactionRefresh.pending = false;
                transactionRefresh.skipCashier = false;
                if (!skip) {
                  setTimeout(() => cashierButton()?.click(), 350);
                }
              }).observe(modal, { attributes: true, attributeFilter: ['class'] });
            }
          };

          const depositRefresh = window.__ctkDepositRefresh || {
            pending: false,
            ready: false,
            originalButton: null
          };
          window.__ctkDepositRefresh = depositRefresh;

          window.__ctkApplyFreshDeposit = () => {
            if (!depositRefresh.pending) return;
            const freshButton = document.querySelector('#addDeposit');
            if (!freshButton || freshButton === depositRefresh.originalButton) return;
            depositRefresh.pending = false;
            depositRefresh.ready = true;
            depositRefresh.originalButton = null;
            requestAnimationFrame(() => freshButton.click());
          };

          if (!window.__ctkAndroidCashierV5Installed) {
            window.__ctkAndroidCashierV2Installed = true;
            window.__ctkAndroidCashierV3Installed = true;
            window.__ctkAndroidCashierV4Installed = true;
            window.__ctkAndroidCashierV5Installed = true;
            let cashierOpened = false;

            window.__ctkOpenCashier = () => {
              if (cashierOpened) return;
              const appView = document.querySelector('#appView');
              if (!appView || appView.classList.contains('hidden')) return;
              if (isCashier()) {
                cashierOpened = true;
                return;
              }
              const button = cashierButton();
              if (!button) return;
              cashierOpened = true;
              button.click();
            };

            if (window.NativeCashier) {
              window.print = () => window.NativeCashier.printReceipt();
            }

            document.addEventListener('click', event => {
              const addDeposit = event.target.closest('#addDeposit');
              if (!addDeposit) return;
              if (depositRefresh.ready) {
                depositRefresh.ready = false;
                return;
              }
              if (depositRefresh.pending) {
                event.preventDefault();
                event.stopImmediatePropagation();
                return;
              }

              const depositsNav = document.querySelector(
                '#mainNav [data-page="deposits"], .bottom-nav [data-page="deposits"]'
              );
              if (!depositsNav) return;

              event.preventDefault();
              event.stopImmediatePropagation();
              depositRefresh.pending = true;
              depositRefresh.originalButton = addDeposit;
              addDeposit.disabled = true;
              addDeposit.textContent = '↻ Memuat cash terbaru…';
              depositsNav.click();
            }, true);

            let scheduled = false;
            new MutationObserver(() => {
              if (scheduled) return;
              scheduled = true;
              requestAnimationFrame(() => {
                scheduled = false;
                window.__ctkOpenCashier();
                window.__ctkApplyCatalog();
                window.__ctkApplyResponsiveTables();
                window.__ctkApplyTransactionRefresh();
                window.__ctkApplyFreshDeposit();
              });
            }).observe(document.documentElement, { childList: true, subtree: true });
          }

          window.__ctkOpenCashier?.();
          window.__ctkApplyCatalog?.();
          window.__ctkApplyResponsiveTables?.();
          window.__ctkApplyTransactionRefresh?.();
          window.__ctkApplyFreshDeposit?.();
        })();
    """.trimIndent()
}
