<div align="center">

# 🔥 Codex Mobile

### 📱 OpenAI Codex CLI — In Your Pocket — On Android 📱

[![Android](https://img.shields.io/badge/Android-24+-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org)
[![Node.js](https://img.shields.io/badge/Node.js-21-339933?logo=nodedotjs&logoColor=white&style=for-the-badge)](https://nodejs.org)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white&style=for-the-badge)](https://vuejs.org)
[![Status](https://img.shields.io/badge/Status-🔥%20WORKS-brightgreen?style=for-the-badge)](#)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

<br />

> **They built an AI coding agent for the terminal.**
> **We put an entire Linux environment inside an Android app and ran it there.**
> **One APK. No root. Full Codex.**

<br />

```
╔═══════════════════════════════════════════════╗
║   ██████╗ ██████╗ ██████╗ ███████╗██╗  ██╗   ║
║  ██╔════╝██╔═══██╗██╔══██╗██╔════╝╚██╗██╔╝   ║
║  ██║     ██║   ██║██║  ██║█████╗   ╚███╔╝    ║
║  ██║     ██║   ██║██║  ██║██╔══╝   ██╔██╗    ║
║  ╚██████╗╚██████╔╝██████╔╝███████╗██╔╝ ██╗   ║
║   ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝   ║
║         M O B I L E   E D I T I O N           ║
╚═══════════════════════════════════════════════╝
```

</div>

---

## 🤯 What Is This?

OpenAI shipped [Codex CLI](https://github.com/openai/codex) — a terminal-based AI coding agent that can read, write, and execute code. **But it only runs on Linux and macOS.**

We said: *what if it ran on your phone?*

This app bundles a **full Termux Linux environment**, **Node.js 21**, the **Codex CLI**, and a **Vue.js web interface** — all inside a single Android APK. No root required. No terminal knowledge needed. Open the app, authenticate with OpenAI, and start coding from your pocket.

**Yes, that's a real Linux userland. Yes, that's the real Codex binary. Yes, it runs on your phone.** 🧠

---

## 📱 How It Works

<div align="center">
<table>
<tr>
<td align="center" width="50%">
<br /><b>🏗️ Architecture</b><br />
<sub>Android WebView → Vue.js frontend → Express bridge → <code>codex app-server</code> (native ARM64 binary) → OpenAI API</sub>
</td>
<td align="center" width="50%">
<br /><b>⚡ First Launch</b><br />
<sub>Extracts Termux bootstrap → installs Node.js via apt → deploys Codex CLI → downloads native binary → authenticates via OAuth → ready in ~2 minutes</sub>
</td>
</tr>
</table>
</div>

---

## 🌍 What Can You Do With This?

| | Use Case | Description |
|---|---|---|
| 💻 | **Code on the go** | Write, debug, and refactor code from your phone or tablet |
| 🤖 | **AI pair programming** | Full Codex agent with tool use, file I/O, and shell access |
| 📂 | **Manage projects** | Multiple threads, model selection, reasoning effort control |
| 🔓 | **Full access mode** | `danger-full-access` sandbox — no approval prompts, maximum speed |
| 🌐 | **OAuth login** | Browser-based OpenAI authentication, no manual API keys needed |
| 📡 | **Background running** | Foreground service keeps the server alive when screen is off |
| 🔌 | **Offline-capable setup** | Bootstrap and binaries cached after first install |
| 🧩 | **Real Linux env** | Termux-compatible userland with apt, Node.js, npm, and more |

---

## ⚡ Quick Start

```bash
# 🔧 Clone and build
git clone https://github.com/friuns2/codex-web-local.git
cd codex-web-local

# 📦 Download Termux bootstrap
cd android && bash scripts/download-bootstrap.sh && cd ..

# 🏗️ Build Vue frontend + server bundle
bash android/scripts/build-server-bundle.sh

# 🚀 Build APK
cd android && ./gradlew assembleDebug
# APK at: android/app/build/outputs/apk/debug/app-debug.apk ✈️
```

Or grab the latest APK from [**Releases**](https://github.com/friuns2/codex-web-local/releases) 📥

---

## 📁 Project Structure

```
codex-web-local/
├── 🌐 src/                        # Vue.js frontend + Express server
│   ├── components/                # UI components (composer, sidebar, threads)
│   ├── api/                       # RPC client → codex app-server bridge
│   ├── server/                    # Express HTTP server + SSE events
│   └── cli/                       # CLI entry point
├── 📱 android/                    # Android app (Kotlin)
│   ├── app/src/main/java/         # Kotlin source
│   │   ├── MainActivity.kt        # WebView + setup orchestration
│   │   ├── CodexServerManager.kt  # Node.js/Codex process management
│   │   ├── BootstrapInstaller.kt  # Termux environment extraction
│   │   └── CodexForegroundService.kt  # Background service
│   ├── app/src/main/assets/       # proxy.js, bootstrap zip, server bundle
│   └── scripts/                   # Build helper scripts
├── 📖 documentation/              # App-server JSON-RPC schemas
└── 🔧 vite.config.ts             # Frontend build config
```

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Android App                        │
│                                                       │
│  ┌─────────┐    ┌──────────────┐    ┌──────────────┐ │
│  │ WebView │───▶│  Vue.js SPA  │───▶│ Express      │ │
│  │         │    │  (frontend)  │    │ HTTP Server  │ │
│  └─────────┘    └──────────────┘    └──────┬───────┘ │
│                                            │ JSON-RPC │
│  ┌─────────────────────────────────────────▼───────┐ │
│  │           codex app-server (native ARM64)        │ │
│  │           approval: never | sandbox: full-access │ │
│  └────────────────────┬────────────────────────────┘ │
│                       │ HTTPS via CONNECT proxy      │
│  ┌────────────────────▼────────────────────────────┐ │
│  │         Node.js HTTP CONNECT Proxy              │ │
│  │         (DNS + TLS for musl binary)             │ │
│  └────────────────────┬────────────────────────────┘ │
│                       │                               │
│  ┌────────────────────▼────────────────────────────┐ │
│  │      Termux Bootstrap (Linux userland)          │ │
│  │      /data/data/com.codex.mobile/files/usr/     │ │
│  └─────────────────────────────────────────────────┘ │
└──────────────────────────┬───────────────────────────┘
                           │
                    ┌──────▼──────┐
                    │ OpenAI API  │
                    └─────────────┘
```

---

## 🔧 Key Technical Challenges Solved

> **Running a statically-linked musl Rust binary on Android? Yeah, that was fun.**

1. 🔌 **DNS/TLS for native binary** — The Codex Rust binary is compiled for `musl` Linux and can't use Android's DNS resolver. Solution: a Node.js HTTP CONNECT proxy that routes all traffic through Android's native network stack.

2. 🔒 **W^X execution policy** — Android 10+ blocks executing binaries from app data. Solution: `targetSdk = 28` (same approach as Termux F-Droid).

3. 📦 **Platform binary mismatch** — npm refuses to install `@openai/codex-linux-arm64` on Android (`os: android ≠ linux`). Solution: download the tarball directly via Node.js and extract it manually.

4. 🔑 **OAuth in embedded environment** — `codex login` spawns a local callback server. The app parses the OAuth URL from stdout and opens the system browser.

5. 🗂️ **Termux path remapping** — All hardcoded `/data/data/com.termux` paths in apt/dpkg configs are rewritten to the app's private directory at install time.

---

## 🎯 Requirements

- 📱 Android 7.0+ (API 24+) — ARM64 device
- 🌐 Internet connection (for OpenAI API + first-time setup)
- 🔑 OpenAI account with Codex access
- 💾 ~200MB storage for bootstrap + Node.js + Codex

---

## 🐛 Troubleshooting

| Problem | Solution |
|---|---|
| 🔴 "Failed to install Node.js" | Check internet connection; apt needs to download ~40MB |
| 🔴 OpenSSL config error | The app handles this — if you see it in logs, it's from a stale process |
| 🔴 Message disappears after sending | Force-stop and relaunch — server bundle may need re-extraction |
| 🔴 "Health check failed" | Verify OpenAI account has Codex access; check proxy is running |
| 🔴 App killed in background | Grant battery optimization exemption (prompted at startup) |

---

## 🤝 Contributing

PRs welcome! Key areas:

- 🎨 UI improvements for mobile form factor
- 📱 Multi-architecture support (x86_64 for emulators)
- 🔧 Startup time optimization
- 📋 File browser / workspace management

---

## ⭐ Star This Repo

If you believe **AI coding agents should run everywhere** — not just on laptops with terminal access — smash that star button. ⭐

Your stars fuel the mass delusion that shipping a Linux distro inside an Android app is totally normal and fine. 🚀

---

<div align="center">

**Built by shoving an entire Linux userland into a WebView** 🔬

*"Can it run Codex?" — yes, even your phone can now* 😏

</div>
