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

          if ((window.__ctkAndroidCashierInstalled || window.__ctkAndroidCashierV2Installed) &&
              !window.__ctkAndroidCashierV3Installed) {
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
            @media (max-width: 760px) {
              .page-content { padding-left: 10px !important; padding-right: 10px !important; }
              .topbar { padding-left: 12px !important; padding-right: 12px !important; }
              .ctk-category-chip { min-height: 36px; padding: 7px 12px; }
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

          if (!window.__ctkAndroidCashierV3Installed) {
            window.__ctkAndroidCashierV2Installed = true;
            window.__ctkAndroidCashierV3Installed = true;
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
                window.__ctkApplyTransactionRefresh();
                window.__ctkApplyFreshDeposit();
              });
            }).observe(document.documentElement, { childList: true, subtree: true });
          }

          window.__ctkOpenCashier?.();
          window.__ctkApplyCatalog?.();
          window.__ctkApplyTransactionRefresh?.();
          window.__ctkApplyFreshDeposit?.();
        })();
    """.trimIndent()
}
