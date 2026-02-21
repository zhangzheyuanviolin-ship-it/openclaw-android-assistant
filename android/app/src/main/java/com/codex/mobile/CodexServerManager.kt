package com.codex.mobile

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages the lifecycle of the Node.js codex-web-local server process running
 * inside the Termux bootstrap environment. Handles installation of Node.js,
 * Codex CLI, the platform-specific native binary, authentication via
 * `codex login`, and the codex-web-local web server.
 */
class CodexServerManager(private val context: Context) {

    companion object {
        private const val TAG = "CodexServerManager"
        const val SERVER_PORT = 18923
        private const val PROXY_PORT = 18924
        private const val CODEX_VERSION = "0.104.0"
    }

    private var serverProcess: Process? = null
    private var proxyProcess: Process? = null

    val isRunning: Boolean
        get() {
            val proc = serverProcess ?: return false
            return try {
                proc.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }

    // ── Shell helpers ──────────────────────────────────────────────────────

    /**
     * Run a shell command inside the Termux prefix environment.
     * Returns the exit code.
     */
    fun runInPrefix(
        command: String,
        onOutput: ((String) -> Unit)? = null,
    ): Int {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths)

        val shell = "${paths.prefixDir}/bin/sh"
        val pb = ProcessBuilder(shell, "-c", command)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line = reader.readLine()
        while (line != null) {
            Log.d(TAG, line)
            onOutput?.invoke(line)
            line = reader.readLine()
        }
        return proc.waitFor()
    }

    /**
     * Run a command and capture its stdout as a single trimmed string.
     */
    private fun runCapture(command: String): String {
        val sb = StringBuilder()
        runInPrefix(command) { sb.appendLine(it) }
        return sb.toString().trim()
    }

    // ── Install checks ─────────────────────────────────────────────────────

    fun isNodeInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "bin/node").exists()
    }

    fun isCodexInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(paths.prefixDir, "lib/node_modules/@openai/codex/bin/codex.js").exists()
    }

    fun isServerBundleInstalled(): Boolean = false

    /**
     * The native Rust binary that the JS launcher delegates to.
     * Required for `codex app-server`, `codex login`, `codex exec`, etc.
     */
    fun isPlatformBinaryInstalled(): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        return File(
            paths.prefixDir,
            "lib/node_modules/@openai/codex-linux-arm64/vendor/aarch64-unknown-linux-musl/codex/codex",
        ).exists()
    }

    // ── Installation ────────────────────────────────────────────────────────

    fun installNode(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir

        onProgress("Downloading Node.js packages…")

        val downloadCmd = """
            cd $prefix/tmp &&
            apt-get update --allow-insecure-repositories 2>&1;
            apt-get download --allow-unauthenticated c-ares libicu libsqlite nodejs-lts npm 2>&1
        """.trimIndent()

        val dlCode = runInPrefix(downloadCmd, onOutput = { onProgress(it) })
        if (dlCode != 0) {
            Log.e(TAG, "apt-get download failed with code $dlCode")
        }

        onProgress("Extracting Node.js packages…")
        val termuxPrefix = "/data/data/com.termux/files/usr"
        val extractCmd = """
            cd $prefix/tmp &&
            mkdir -p _stage &&
            for deb in *.deb; do
                echo "Extracting ${'$'}deb..." &&
                dpkg-deb -x "${'$'}deb" _stage/ 2>&1
            done &&
            if [ -d "_stage$termuxPrefix" ]; then
                cp -a _stage$termuxPrefix/* "$prefix/" 2>&1
            elif [ -d "_stage/usr" ]; then
                cp -a _stage/usr/* "$prefix/" 2>&1
            fi &&
            rm -rf _stage *.deb 2>/dev/null
            echo "done"
        """.trimIndent()

        val extractCode = runInPrefix(extractCmd, onOutput = { onProgress(it) })
        if (extractCode != 0) {
            Log.e(TAG, "dpkg-deb extract failed with code $extractCode")
            return false
        }

        onProgress("Fixing script paths…")
        val fixCmd = """
            chmod 700 "$prefix/bin/node" 2>/dev/null

            CODEX_JS="$prefix/lib/node_modules/@openai/codex/bin/codex.js"
            if [ -f "${'$'}CODEX_JS" ]; then
                rm -f "$prefix/bin/codex"
                cat > "$prefix/bin/codex" << 'WEOF'
#!/data/user/0/com.codex.mobile/files/usr/bin/sh
exec /data/user/0/com.codex.mobile/files/usr/bin/node /data/user/0/com.codex.mobile/files/usr/lib/node_modules/@openai/codex/bin/codex.js "${'$'}@"
WEOF
                chmod 700 "$prefix/bin/codex"
            fi

            NPM_CLI="$prefix/lib/node_modules/npm/bin/npm-cli.js"
            if [ -f "${'$'}NPM_CLI" ]; then
                rm -f "$prefix/bin/npm"
                cat > "$prefix/bin/npm" << 'WEOF'
#!/data/user/0/com.codex.mobile/files/usr/bin/sh
exec /data/user/0/com.codex.mobile/files/usr/bin/node /data/user/0/com.codex.mobile/files/usr/lib/node_modules/npm/bin/npm-cli.js "${'$'}@"
WEOF
                chmod 700 "$prefix/bin/npm"
            fi

            echo "Wrapper scripts created"
        """.trimIndent()
        runInPrefix(fixCmd, onOutput = { onProgress(it) })

        return isNodeInstalled()
    }

    fun installCodex(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir
        val npmCli = "$prefix/lib/node_modules/npm/bin/npm-cli.js"

        onProgress("Installing Codex CLI…")
        val codexCode = runInPrefix(
            "node $npmCli install -g @openai/codex 2>&1",
            onOutput = { onProgress(it) },
        )
        if (codexCode != 0) {
            Log.e(TAG, "npm install @openai/codex failed with code $codexCode")
            return false
        }

        onProgress("Installing codex-web-local…")
        val webCode = runInPrefix(
            "node $npmCli install -g codex-web-local 2>&1",
            onOutput = { onProgress(it) },
        )
        if (webCode != 0) {
            Log.e(TAG, "npm install codex-web-local failed with code $webCode")
            return false
        }

        return isCodexInstalled() && isServerBundleInstalled()
    }

    fun installServerBundle(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val targetDir = File(paths.prefixDir, "lib/node_modules/codex-web-local")

        try {
            val assetFiles = context.assets.list("server-bundle") ?: emptyArray()
            if (assetFiles.isNotEmpty()) {
                onProgress("Installing server bundle from APK…")
                targetDir.deleteRecursively()
                targetDir.mkdirs()
                extractAssetDir("server-bundle", targetDir)
                Log.i(TAG, "Server bundle extracted to $targetDir")
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "No bundled server-bundle asset, will use npm: ${e.message}")
        }

        return false
    }

    /**
     * Install the platform-specific native Codex binary.
     * npm refuses to install it on android (os mismatch), so we download
     * the tarball via Node.js and extract it manually.
     */
    fun installPlatformBinary(onProgress: (String) -> Unit): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val prefix = paths.prefixDir
        val targetPkg = "$prefix/lib/node_modules/@openai/codex-linux-arm64"

        onProgress("Downloading Codex native binary…")

        // Use Node.js (which has working TLS) to download the npm tarball
        val installCmd = """
            mkdir -p "$prefix/tmp/_codex_bin" && cd "$prefix/tmp/_codex_bin" &&
            node -e '
              const https = require("https");
              const fs = require("fs");
              const url = "https://registry.npmjs.org/@openai/codex/-/codex-$CODEX_VERSION-linux-arm64.tgz";
              const file = fs.createWriteStream("codex-bin.tgz");
              https.get(url, (res) => {
                if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
                  https.get(res.headers.location, (r2) => r2.pipe(file).on("finish", () => {
                    file.close(); console.log("Downloaded"); process.exit(0);
                  }));
                } else {
                  res.pipe(file).on("finish", () => {
                    file.close(); console.log("Downloaded"); process.exit(0);
                  });
                }
              }).on("error", (e) => { console.error(e.message); process.exit(1); });
            ' 2>&1 &&
            tar xzf codex-bin.tgz 2>&1 &&
            mkdir -p "$targetPkg/vendor/aarch64-unknown-linux-musl/codex" &&
            cp package/vendor/aarch64-unknown-linux-musl/codex/codex "$targetPkg/vendor/aarch64-unknown-linux-musl/codex/codex" &&
            cp package/package.json "$targetPkg/package.json" &&
            chmod 700 "$targetPkg/vendor/aarch64-unknown-linux-musl/codex/codex" &&
            rm -rf "$prefix/tmp/_codex_bin" &&
            echo "Platform binary installed"
        """.trimIndent()

        val code = runInPrefix(installCmd, onOutput = { onProgress(it) })
        if (code != 0) {
            Log.e(TAG, "Platform binary install failed with code $code")
            return false
        }

        return isPlatformBinaryInstalled()
    }

    // ── Proxy ────────────────────────────────────────────────────────────────

    /**
     * Start a Node.js CONNECT proxy so the static-musl codex binary can
     * resolve DNS and reach HTTPS endpoints. Node.js uses Android's native
     * resolver; the proxy forwards TCP connections transparently.
     */
    fun startProxy(): Boolean {
        if (proxyProcess != null) return true

        val paths = BootstrapInstaller.getPaths(context)
        val proxyScript = File(paths.homeDir, "proxy.js")

        // Always overwrite with the latest version from assets
        try {
            context.assets.open("proxy.js").use { input ->
                proxyScript.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract proxy.js asset: ${e.message}")
            return false
        }

        // Kill any orphaned proxy from a previous run
        val pidFile = File(paths.homeDir, ".proxy.pid")
        if (pidFile.exists()) {
            try {
                val oldPid = pidFile.readText().trim()
                ProcessBuilder("kill", oldPid).start().waitFor()
                Thread.sleep(500)
            } catch (_: Exception) {}
            pidFile.delete()
        }

        val env = buildEnvironment(paths)
        val shell = "${paths.prefixDir}/bin/sh"
        val cmd = "exec node ${proxyScript.absolutePath}"

        val pb = ProcessBuilder(shell, "-c", cmd)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        proxyProcess = proc

        Thread {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line = reader.readLine()
            while (line != null) {
                Log.d(TAG, "[proxy] $line")
                line = reader.readLine()
            }
            Log.i(TAG, "Proxy exited with code: ${proc.waitFor()}")
        }.start()

        Thread.sleep(800)
        Log.i(TAG, "CONNECT proxy started on 127.0.0.1:$PROXY_PORT")
        return true
    }

    fun stopProxy() {
        proxyProcess?.destroy()
        proxyProcess = null
    }

    // ── Authentication ──────────────────────────────────────────────────────

    private fun codexBinPath(): String {
        val paths = BootstrapInstaller.getPaths(context)
        return "${paths.prefixDir}/lib/node_modules/@openai/codex-linux-arm64" +
            "/vendor/aarch64-unknown-linux-musl/codex/codex"
    }

    fun isLoggedIn(): Boolean {
        val output = runCapture("${codexBinPath()} login status 2>&1")
        Log.i(TAG, "Login status: $output")
        return !output.contains("Not logged in", ignoreCase = true)
    }

    /**
     * Pipe an API key into `codex login --with-api-key` via stdin.
     */
    fun loginWithApiKey(apiKey: String): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths)

        val pb = ProcessBuilder(codexBinPath(), "login", "--with-api-key")
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        proc.outputStream.bufferedWriter().use { w ->
            w.write(apiKey)
            w.newLine()
            w.flush()
        }

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line = reader.readLine()
        while (line != null) {
            Log.d(TAG, "[login] $line")
            line = reader.readLine()
        }

        val exitCode = proc.waitFor()
        Log.i(TAG, "codex login --with-api-key exited with code $exitCode")
        return exitCode == 0
    }

    /**
     * Run `codex login` (URL-based OAuth flow) using the CONNECT proxy.
     * The native binary starts a local HTTP server for the OAuth callback,
     * prints an auth URL, and waits for the redirect. Parses the URL from
     * stdout and calls [onLoginUrl] so the Activity can open the browser.
     * Blocks until login completes or fails.
     */
    fun loginWithUrl(
        onLoginUrl: (url: String) -> Unit,
        onProgress: (String) -> Unit,
    ): Boolean {
        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["HTTPS_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["HTTP_PROXY"] = "http://127.0.0.1:$PROXY_PORT"

        val pb = ProcessBuilder(codexBinPath(), "login")
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))

        val urlRegex = Regex("""(https://auth\.openai\.com/\S+)""")
        var urlSent = false

        var line = reader.readLine()
        while (line != null) {
            val clean = line.replace(Regex("\\x1b\\[[0-9;]*m"), "").trim()
            Log.d(TAG, "[login] $clean")
            onProgress(clean)

            if (!urlSent) {
                urlRegex.find(clean)?.let {
                    onLoginUrl(it.value)
                    urlSent = true
                }
            }

            line = reader.readLine()
        }

        val exitCode = proc.waitFor()
        Log.i(TAG, "codex login exited with code $exitCode")
        return exitCode == 0
    }

    // ── Health check ────────────────────────────────────────────────────────

    /**
     * Send a minimal prompt ("hi") to Codex in non-interactive (exec) mode
     * via the CONNECT proxy. Confirms the API key is valid and the native
     * binary can reach OpenAI.
     */
    fun healthCheck(onProgress: (String) -> Unit): Boolean {
        onProgress("Sending test message…")

        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["HTTPS_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["HTTP_PROXY"] = "http://127.0.0.1:$PROXY_PORT"

        val shell = "${paths.prefixDir}/bin/sh"
        val cmd = "${codexBinPath()} exec --skip-git-repo-check \"say hi\" 2>&1"

        val pb = ProcessBuilder(shell, "-c", cmd)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val sb = StringBuilder()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line = reader.readLine()
        while (line != null) {
            val clean = line.replace(Regex("\\x1b\\[[0-9;]*m"), "").trim()
            Log.d(TAG, "[health] $clean")
            sb.appendLine(clean)
            onProgress(clean)
            line = reader.readLine()
        }

        val exitCode = proc.waitFor()
        val output = sb.toString().trim()
        Log.i(TAG, "Health check exit=$exitCode output=$output")

        if (exitCode != 0) {
            Log.e(TAG, "Health check failed with exit code $exitCode")
            return false
        }

        return output.isNotEmpty()
    }

    // ── Server lifecycle ────────────────────────────────────────────────────

    /**
     * Start the codex-web-local server. The CONNECT proxy must be running
     * and authentication must have been completed first.
     */
    fun startServer(): Boolean {
        if (isRunning) {
            Log.i(TAG, "Server already running")
            return true
        }

        val paths = BootstrapInstaller.getPaths(context)
        val env = buildEnvironment(paths).toMutableMap()
        env["HTTPS_PROXY"] = "http://127.0.0.1:$PROXY_PORT"
        env["HTTP_PROXY"] = "http://127.0.0.1:$PROXY_PORT"

        val serverScript = "${paths.prefixDir}/lib/node_modules/codex-web-local/dist-cli/index.js"
        if (!File(serverScript).exists()) {
            Log.e(TAG, "Server script not found: $serverScript")
            return false
        }

        val shell = "${paths.prefixDir}/bin/sh"
        val command = "exec node $serverScript --port $SERVER_PORT --no-password"

        Log.i(TAG, "Starting server: $command")

        val pb = ProcessBuilder(shell, "-c", command)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.directory(File(paths.homeDir))
        pb.redirectErrorStream(true)

        val proc = pb.start()
        serverProcess = proc

        Thread {
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line = reader.readLine()
            while (line != null) {
                Log.d(TAG, "[server] $line")
                line = reader.readLine()
            }
            Log.i(TAG, "Server process exited with code: ${proc.waitFor()}")
        }.start()

        return true
    }

    fun waitForServer(timeoutMs: Long = 60_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        val url = URL("http://127.0.0.1:$SERVER_PORT/")

        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..399) {
                    Log.i(TAG, "Server is ready (HTTP $code)")
                    return true
                }
            } catch (_: Exception) {
                // Not ready yet
            }
            Thread.sleep(500)
        }

        Log.e(TAG, "Server did not become ready within ${timeoutMs}ms")
        return false
    }

    fun stopServer() {
        val proc = serverProcess ?: return
        serverProcess = null

        try {
            proc.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying server process: ${e.message}")
        }

        try {
            proc.waitFor()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        stopProxy()
        Log.i(TAG, "Server stopped")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun extractAssetDir(assetPath: String, targetDir: File) {
        val list = context.assets.list(assetPath) ?: return
        targetDir.mkdirs()
        for (entry in list) {
            val subAsset = "$assetPath/$entry"
            val subTarget = File(targetDir, entry)
            val subList = context.assets.list(subAsset)
            if (subList != null && subList.isNotEmpty()) {
                subTarget.mkdirs()
                extractAssetDir(subAsset, subTarget)
            } else {
                context.assets.open(subAsset).use { input ->
                    subTarget.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun buildEnvironment(
        paths: BootstrapInstaller.Paths,
    ): Map<String, String> {
        return mapOf(
            "PREFIX" to paths.prefixDir,
            "HOME" to paths.homeDir,
            "PATH" to "${paths.prefixDir}/bin:${paths.prefixDir}/bin/applets:/system/bin",
            "LD_LIBRARY_PATH" to "${paths.prefixDir}/lib",
            "LD_PRELOAD" to "${paths.prefixDir}/lib/libtermux-exec.so",
            "TERMUX_PREFIX" to paths.prefixDir,
            "TERMUX__PREFIX" to paths.prefixDir,
            "LANG" to "en_US.UTF-8",
            "TMPDIR" to paths.tmpDir,
            "TERM" to "xterm-256color",
            "ANDROID_DATA" to "/data",
            "ANDROID_ROOT" to "/system",
            "APT_CONFIG" to "${paths.prefixDir}/etc/apt/apt.conf",
            "DPKG_ADMINDIR" to "${paths.prefixDir}/var/lib/dpkg",
            "SSL_CERT_FILE" to "${paths.prefixDir}/etc/tls/cert.pem",
            "SSL_CERT_DIR" to "/system/etc/security/cacerts",
            "CURL_CA_BUNDLE" to "${paths.prefixDir}/etc/tls/cert.pem",
            "GIT_SSL_CAINFO" to "${paths.prefixDir}/etc/tls/cert.pem",
            "OPENSSL_CONF" to "${paths.prefixDir}/etc/tls/openssl.cnf",
            "NODE_OPTIONS" to "--openssl-config=${paths.prefixDir}/etc/tls/openssl.cnf",
        )
    }
}
