package com.codex.mobile

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CodexMainActivity"
        private const val PREFS_FILE = "codex_prefs"
        private const val KEY_API_KEY = "openai_api_key"
    }

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var serverManager: CodexServerManager
    private lateinit var securePrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        statusText = findViewById(R.id.statusText)
        statusDetail = findViewById(R.id.statusDetail)
        progressBar = findViewById(R.id.progressBar)

        serverManager = CodexServerManager(this)
        securePrefs = createSecurePrefs()

        setupWebView()
        startSetupFlow()
    }

    override fun onDestroy() {
        super.onDestroy()
        serverManager.stopServer()
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            setSupportZoom(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                url: String,
            ): Boolean = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d(TAG, "[WebView] ${msg.sourceId()}:${msg.lineNumber()} ${msg.message()}")
                return true
            }
        }
    }

    private fun startSetupFlow() {
        showLoading(true)
        setStatus("Initializing…")

        Thread {
            try {
                runSetup()
            } catch (e: Exception) {
                Log.e(TAG, "Setup failed", e)
                runOnUiThread {
                    showError(e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    private fun runSetup() {
        // Step 1: Extract bootstrap
        if (!BootstrapInstaller.isBootstrapInstalled(this)) {
            updateStatus("Extracting environment…")
            BootstrapInstaller.install(this) { msg -> updateStatus(msg) }
        }
        updateStatus("Environment ready")

        // Step 2: Install Node.js if needed
        if (!serverManager.isNodeInstalled()) {
            updateStatus("Installing Node.js (first run)…", "This may take a few minutes")
            val nodeOk = serverManager.installNode { msg -> updateDetail(msg) }
            if (!nodeOk) {
                throw RuntimeException("Failed to install Node.js")
            }
        }
        updateStatus("Node.js ready")

        // Step 3: Install Codex CLI if needed
        if (!serverManager.isCodexInstalled() || !serverManager.isServerBundleInstalled()) {
            updateStatus("Installing Codex…", "This may take a few minutes")

            // Try bundled assets first, then fall back to npm
            if (!serverManager.installServerBundle { msg -> updateDetail(msg) }) {
                val codexOk = serverManager.installCodex { msg -> updateDetail(msg) }
                if (!codexOk) {
                    throw RuntimeException("Failed to install Codex")
                }
            }
        }
        updateStatus("Codex ready")

        // Step 4: Get API key
        val apiKey = getOrRequestApiKey()
        if (apiKey.isBlank()) {
            throw RuntimeException("No API key provided")
        }

        // Step 5: Start server
        updateStatus("Starting server…")
        val started = serverManager.startServer(apiKey)
        if (!started) {
            throw RuntimeException("Failed to start server")
        }

        // Step 6: Wait for server to be ready
        updateStatus("Waiting for server…")
        val ready = serverManager.waitForServer(timeoutMs = 90_000)
        if (!ready) {
            throw RuntimeException("Server did not start in time")
        }

        // Step 7: Load WebView
        runOnUiThread {
            showLoading(false)
            webView.visibility = View.VISIBLE
            webView.loadUrl("http://127.0.0.1:${CodexServerManager.SERVER_PORT}/")
        }
    }

    private fun getOrRequestApiKey(): String {
        val stored = securePrefs.getString(KEY_API_KEY, null)
        if (!stored.isNullOrBlank()) {
            return stored
        }

        // Block current thread and show dialog on UI thread
        var result = ""
        val lock = Object()

        runOnUiThread {
            showApiKeyDialog { key ->
                result = key
                synchronized(lock) {
                    lock.notifyAll()
                }
            }
        }

        synchronized(lock) {
            lock.wait(300_000) // 5 min max wait for user input
        }

        if (result.isNotBlank()) {
            securePrefs.edit().putString(KEY_API_KEY, result).apply()
        }

        return result
    }

    private fun showApiKeyDialog(onResult: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.api_key_hint)
            setSingleLine(true)
        }

        val padding = (24 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.api_key_title)
            .setMessage(R.string.api_key_message)
            .setView(container)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ ->
                onResult(input.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                onResult("")
            }
            .show()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(R.string.retry) { _, _ ->
                startSetupFlow()
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setStatus(text: String, detail: String? = null) {
        statusText.text = text
        if (detail != null) {
            statusDetail.text = detail
            statusDetail.visibility = View.VISIBLE
        } else {
            statusDetail.visibility = View.GONE
        }
    }

    private fun updateStatus(text: String, detail: String? = null) {
        runOnUiThread { setStatus(text, detail) }
    }

    private fun updateDetail(text: String) {
        runOnUiThread {
            statusDetail.text = text
            statusDetail.visibility = View.VISIBLE
        }
    }

    private fun createSecurePrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                this,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e)
            getSharedPreferences(PREFS_FILE, MODE_PRIVATE)
        }
    }
}
