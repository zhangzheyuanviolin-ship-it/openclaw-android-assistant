<div align="center">

# 🤖 Codex App Android

### 📱 OpenAI Codex CLI — Running Natively on Your Android Phone 📱

[![Android](https://img.shields.io/badge/Android-7.0+-3DDC84?logo=android&logoColor=white&style=for-the-badge)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org)
[![Node.js](https://img.shields.io/badge/Node.js-24-339933?logo=nodedotjs&logoColor=white&style=for-the-badge)](https://nodejs.org)
[![Vue](https://img.shields.io/badge/Vue.js-3-4FC08D?logo=vuedotjs&logoColor=white&style=for-the-badge)](https://vuejs.org)
[![Codex](https://img.shields.io/badge/Codex_CLI-0.104.0-412991?logo=openai&logoColor=white&style=for-the-badge)](https://github.com/openai/codex)
[![Status](https://img.shields.io/badge/Status-🔥%20WORKS-brightgreen?style=for-the-badge)](https://github.com/friuns2/codex-app-android)
[![Stars](https://img.shields.io/github/stars/friuns2/codex-app-android?style=for-the-badge&logo=github&color=gold)](https://github.com/friuns2/codex-app-android/stargazers)

<br />

> **A self-contained Android APK that bundles an entire Linux environment,**
> **installs the OpenAI Codex CLI, and gives you a full coding agent UI**
> **right on your phone. No root. No Termux. One APK.**

<br />

```
╔═══════════════════════════════════════════════╗
║   ██████╗ ██████╗ ██████╗ ███████╗██╗  ██╗   ║
║  ██╔════╝██╔═══██╗██╔══██╗██╔════╝╚██╗██╔╝   ║
║  ██║     ██║   ██║██║  ██║█████╗   ╚███╔╝    ║
║  ██║     ██║   ██║██║  ██║██╔══╝   ██╔██╗    ║
║  ╚██████╗╚██████╔╝██████╔╝███████╗██╔╝ ██╗   ║
║   ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝╚═╝  ╚═╝   ║
║     A N D R O I D  ·  M O B I L E  ·  A P K   ║
╚═══════════════════════════════════════════════╝
```

</div>

---

## 🤯 What Is This?

OpenAI shipped Codex CLI — a **terminal-based AI coding agent** that reads your codebase, writes code, runs commands, and iterates. It's incredible. But it only runs on macOS and Linux.

**We put it on Android.** Yes, the full native Rust binary. Yes, with a real web UI. Yes, on your phone.

This project packages a **complete Linux userland** (borrowed from Termux's bootstrap), installs **Node.js 24**, the **Codex CLI v0.104.0** with its native `aarch64` binary, wires up a **Vue.js web frontend**, and serves it all through an **Android WebView** — in a single APK that installs like any other app.

**One APK. Zero dependencies. Full AI coding agent in your pocket.** 🧠

---

## 🌍 What Can You Do With This?

| | Use Case | Description |
|---|---|---|
| 💬 | **Chat with Codex** | Full conversational coding agent with streaming responses |
| 📝 | **Write code on the go** | Generate, refactor, and debug code from your phone |
| 🔧 | **Execute commands** | Codex runs shell commands in the embedded Linux environment |
| 🧠 | **Reasoning visibility** | Watch the model think in real-time with reasoning summaries |
| 📂 | **Multi-thread sessions** | Multiple parallel conversations, each with its own context |
| 🔓 | **Full auto-approval** | No permission popups — `danger-full-access` mode by default |
| 🌙 | **Background execution** | Foreground service keeps Codex alive when you switch apps |
| 🔑 | **OAuth login** | Authenticate with your OpenAI account via browser — no API key pasting |
| 🌐 | **DNS proxy bridge** | Native musl binary routes through Node.js CONNECT proxy for DNS/TLS |
| 📱 | **Offline-ready bootstrap** | Linux environment extracted from APK — works without internet after setup |

---

## ⚡ Quick Start

```bash
# 🔨 Clone the repo
git clone https://github.com/friuns2/codex-app-android.git
cd codex-app-android

# 📦 Install deps & build frontend
npm install && npm run build

# 🐧 Download Termux bootstrap (one-time, ~50MB)
cd android && bash scripts/download-bootstrap.sh

# 📱 Bundle, build APK, install, launch
bash scripts/build-server-bundle.sh && ./gradlew assembleDebug \
  && adb install -r app/build/outputs/apk/debug/app-debug.apk \
  && adb shell am start -n com.codex.mobile/.MainActivity
# 🚀 You're flying!
```

---

## 🏗️ Architecture

> **Four layers. One APK. Zero compromises.**

```
┌──────────────────────────────────────────────────────────┐
│                     📱 Android APK                       │
│                                                          │
│  ┌────────────┐  ┌─────────────────────────────────────┐ │
│  │ 🖥️ WebView │  │  📦 APK Assets                     │ │
│  │  (Vue.js)  │  │  bootstrap-aarch64.zip              │ │
│  └─────┬──────┘  │  server-bundle/ (Vue + Express)     │ │
│        │         │  proxy.js (CONNECT proxy)           │ │
│        │         └─────────────────────────────────────┘ │
│  ┌─────▼──────────────────────────────────────────────┐  │
│  │              🔧 CodexServerManager                 │  │
│  │  Bootstrap → Node.js → Codex CLI → Platform Binary │  │
│  │  Config → Proxy → Auth → Health Check → Server     │  │
│  └─────┬──────────────────────────────────────────────┘  │
│        │                                                 │
│  ┌─────▼──────────────────────────────────────────────┐  │
│  │              🐧 Embedded Linux ($PREFIX)           │  │
│  │                                                    │  │
│  │  node (v24)  ─── Express server (:18923)           │  │
│  │                      │                             │  │
│  │                      ├── JSON-RPC over stdio       │  │
│  │                      ▼                             │  │
│  │  codex app-server (native Rust/musl aarch64)       │  │
│  │       │                                            │  │
│  │       ├── HTTPS_PROXY ──▶ proxy.js (:18924)        │  │
│  │       │                      │                     │  │
│  │       │                      ▼                     │  │
│  │       │                 api.openai.com             │  │
│  │       │                                            │  │
│  │       └── SSE notifications ──▶ WebView            │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### 🔄 Request Lifecycle

```
User taps Send
  → 🖥️ Vue ThreadComposer → useDesktopState → codexGateway
    → 📡 POST /codex-api/rpc { method: "turn/start" }
      → 🔌 Express bridge → JSON-RPC stdin → codex app-server
        → 🦀 Native Rust binary → HTTPS via proxy → OpenAI API
        → 📤 stdout JSON-RPC notifications (streaming)
      → 📺 SSE EventSource → live typing in WebView
```

---

## 📁 Project Structure

```
🤖 codex-app-android/
├── 📱 android/
│   ├── 🔧 app/build.gradle.kts           # targetSdk=28 (W^X bypass)
│   └── 📂 src/main/
│       ├── 📋 AndroidManifest.xml         # Permissions & service declaration
│       ├── 📦 assets/
│       │   ├── 🌐 proxy.js               # Node.js CONNECT proxy
│       │   └── 🎁 server-bundle/         # Pre-built Vue + Express + deps
│       └── ☕ java/com/codex/mobile/
│           ├── 🐧 BootstrapInstaller.kt   # Linux environment setup
│           ├── 🔔 CodexForegroundService.kt # Background persistence
│           ├── ⚙️ CodexServerManager.kt    # Install, auth, proxy, server
│           └── 🖥️ MainActivity.kt         # WebView + setup orchestration
├── 🌐 src/                                # codex-web-local (TypeScript + Vue)
│   ├── 📡 api/                            # RPC client, gateway, SSE
│   ├── 🧩 components/                     # Vue components (composer, threads)
│   ├── 🔗 composables/                    # useDesktopState (reactive state)
│   ├── 🔌 server/                         # Express + codex app-server bridge
│   └── 🚀 cli/                            # CLI entry point
├── 🔧 android/scripts/
│   ├── 📥 download-bootstrap.sh           # Fetch Termux bootstrap
│   └── 📦 build-server-bundle.sh          # Bundle frontend into APK
└── 📖 PROJECT_SPEC.md                     # Full technical specification
```

---

## 🧩 How It Works

> **They said you can't run a statically-linked Rust binary on Android without root. We did it anyway.**

### 🐧 Embedded Linux Environment

The APK bundles Termux's `bootstrap-aarch64.zip` — a minimal Linux userland with `sh`, `apt-get`, `dpkg-deb`, SSL certificates, and core libraries. On first launch, it's extracted to the app's private storage. All hardcoded `/data/data/com.termux/` paths are rewritten to our package path.

### 🦀 Native Codex Binary

The Codex CLI ships a **73MB native Rust binary** compiled for `aarch64-unknown-linux-musl`. npm refuses to install it on Android (`os: "linux"` vs `process.platform: "android"`), so we download the tarball directly from the npm registry using Node.js and extract it manually.

### 🌐 DNS/TLS Proxy Bridge

The musl-linked binary reads `/etc/resolv.conf` for DNS — which doesn't exist on Android. Our Node.js **CONNECT proxy** on port `18924` solves this: Node.js uses Android's native Bionic DNS resolver, and the native binary routes all HTTPS through `HTTPS_PROXY`.

### 🔌 JSON-RPC Stdio Bridge

The Express server spawns `codex app-server` and communicates via **newline-delimited JSON-RPC 2.0 over stdin/stdout**. Notifications stream back via **Server-Sent Events** to the Vue frontend, enabling real-time typing, reasoning visibility, and turn progress.

---

## 🛡️ Security & Permissions

| Permission | Why |
|---|---|
| 🌐 `INTERNET` | API calls to OpenAI |
| 🔔 `FOREGROUND_SERVICE` | Keep server alive in background |
| 🔋 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent Doze from killing processes |
| 🔒 `WAKE_LOCK` | Maintain CPU during long tasks |

The app runs with `targetSdk = 28` to bypass Android 10+'s **W^X (Write XOR Execute)** SELinux policy — same approach used by Termux on F-Droid.

---

## 🎯 Requirements

- 📱 **Android 7.0+** (API 24) — ARM64 device
- 🌐 **Internet connection** — for first-run setup + API calls
- 🔑 **OpenAI account** — authenticated via OAuth browser flow
- 💾 **~500MB storage** — for Linux environment + Node.js + Codex binary

---

## 🐛 Troubleshooting

| Problem | Solution |
|---|---|
| 🚫 App crashes on launch | Check `adb logcat` for `CodexServerManager` errors |
| 🔒 "Permission denied" executing binaries | Ensure `targetSdk = 28` in `build.gradle.kts` |
| 🌐 "No address associated with hostname" | Check device has internet; proxy may not be running |
| 🔑 Login page doesn't open | Ensure a default browser is set on the device |
| 📦 Old UI after APK update | Server bundle re-extracts every launch — force-stop and reopen |
| 🔋 App killed in background | Grant battery optimization exemption in Android settings |
| 💥 `codex exec` fails with "not inside trusted directory" | Uses `--skip-git-repo-check` flag automatically |

---

## 📊 Tech Stack

| Layer | Technology | Version |
|---|---|---|
| 🤖 AI Engine | OpenAI Codex CLI (`@openai/codex`) | 0.104.0 |
| 🦀 Native Binary | Rust (musl, aarch64) | - |
| 🟢 Runtime | Node.js (via Termux) | 24.13.0 |
| 🌐 Frontend | Vue.js 3 + Vite + TailwindCSS | 3.x |
| 🔌 Backend | Express.js + JSON-RPC bridge | - |
| 📱 Android | Kotlin + WebView | 2.1.0 |
| 🐧 Linux | Termux bootstrap (aarch64) | - |

---

## ⭐ Star This Repo

If you believe **an AI coding agent should run in your pocket** — not just on a laptop with a terminal — **smash that star button.** ⭐

This is what happens when you refuse to accept "it's desktop-only." 

[![Star History](https://img.shields.io/github/stars/friuns2/codex-app-android?style=for-the-badge&logo=github&color=gold)](https://github.com/friuns2/codex-app-android/stargazers)

---

<div align="center">

**Built by shoving an entire Linux distro into an APK and refusing to give up** 🔬

*They said "just use SSH to your server." We said "no."* 😏

</div>
