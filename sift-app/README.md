# ◈ SIFT — Private On-Device Memory Search

> **Your phone remembers everything. Now you can ask it.**
> 100% on-device · Open-source · AES-256 encrypted · Zero cloud dependency

[![CI](https://github.com/yourusername/sift-android/actions/workflows/ci.yml/badge.svg)](https://github.com/yourusername/sift-android/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/API-29%2B-brightgreen.svg)](https://android-arsenal.com/api?level=29)

---

## What is SIFT?

SIFT is an on-device AI memory layer for Android. It captures your phone activity (app usage, files opened, calls, notifications), stores everything encrypted on your device, and lets you search it using natural language.

**Example queries:**
- *"Show me that PDF I opened 3 days ago after Rahul's call"*
- *"What apps did I use yesterday afternoon?"*
- *"Find files from my meeting with Priya last week"*

## Architecture

```
User Query
    ↓
Intent Parser (Gemma 2B Q4 via Ollama / HuggingFace / LM Studio)
    ↓
Graph Filter (SQLite + Room — time, person, type constraints)
    ↓
Vector Re-ranking (FAISS + MiniLM-L6-v2 embeddings)
    ↓
Results (Jetpack Compose UI)
```

All processing happens on-device. The only network call is to your local Ollama server (or optionally HuggingFace free API).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin + Coroutines |
| UI | Jetpack Compose + Material3 |
| DI | Hilt |
| Database | Room + SQLCipher (AES-256) |
| Vector DB | FAISS (in-process, pure Kotlin v1) |
| Embeddings | MiniLM-L6-v2 ONNX (INT8) |
| LLM | Gemma 2B Q4 via Ollama (on-device) |
| Background | WorkManager |
| Analytics | PostHog (self-hosted) |
| Crash | Sentry |
| CI/CD | GitHub Actions → Play Store |

## Quick Start

### 1. Clone and set up LLM backend

```bash
git clone https://github.com/yourusername/sift-android
cd sift-android

# Install Ollama (on your dev machine / phone)
# https://ollama.com
ollama pull gemma2:2b
OLLAMA_ORIGINS=* ollama serve
```

### 2. Download ONNX model

```bash
# Download MiniLM INT8 ONNX model
mkdir -p app/src/main/assets/models
wget -O app/src/main/assets/models/minilm_int8.onnx \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx
```

### 3. Build and run

```bash
./gradlew assembleDebug
# Install on connected device or emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Enable Accessibility Service

On device: Settings → Accessibility → Installed apps → SIFT → Enable

## Configuration

SIFT supports three LLM backends, configurable in-app:

| Backend | URL | Best For |
|---------|-----|----------|
| **Ollama** (recommended) | `http://10.0.2.2:11434` | True on-device, no API cost |
| **HuggingFace** | `https://api-inference.huggingface.co` | Cloud fallback, free tier |
| **LM Studio** | `http://10.0.2.2:1234` | GUI app on-device |

> **Note:** `10.0.2.2` is the Android emulator alias for `localhost` on your dev machine.
> On a real device, use your machine's LAN IP (e.g., `http://192.168.1.x:11434`).

## Privacy

SIFT is **privacy by architecture**, not just policy:

- All captured data is encrypted with AES-256 (Android Keystore — hardware-backed on modern devices)
- The encryption key never leaves the Keystore
- Zero data transmitted to any cloud service
- Accessibility Service captures: app package, window title, notification text — **never** passwords, credit card numbers, or sensitive input fields
- DB excluded from Android cloud backup

## Building for Release

### Set up signing

```bash
# Generate keystore (do once, store securely)
keytool -genkey -v -keystore release.jks -alias sift_key \
  -keyalg RSA -keysize 2048 -validity 10000

# Encode for GitHub Secrets
base64 release.jks | pbcopy   # macOS
base64 release.jks | xclip    # Linux
```

### GitHub Secrets required

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Base64-encoded release.jks |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `sift_key` |
| `KEY_PASSWORD` | Key password |
| `SENTRY_DSN` | Your Sentry DSN (from sentry.io) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Google Play service account JSON |

### Create release

```bash
# Tag a release → triggers automatic AAB build + Play Store upload
git tag v1.0.0
git push origin v1.0.0
# Then create a GitHub Release from this tag
```

## Roadmap

- [ ] **v1.0** — Core memory capture + search (Ollama)
- [ ] **v1.1** — Screenshot indexing (CLIP-lite vision encoder)
- [ ] **v1.2** — SIFT Pro subscription (Play Billing)
- [ ] **v1.3** — Call transcript indexing
- [ ] **v2.0** — SIFT for Teams (shared workspace)
- [ ] **v2.1** — iOS port (optional cloud mode)
- [ ] **v3.0** — OEM SDK (Qualcomm / Samsung integration)

## Contributing

Pull requests welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

MIT License — see [LICENSE](LICENSE)

---

**Built with ◈ SIFT** · [Website](https://sift.app) · [Twitter](https://twitter.com/getsift) · [Discord](https://discord.gg/sift)
