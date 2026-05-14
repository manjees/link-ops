<p align="center">
  <h1 align="center">LinkOps</h1>
  <p align="center">
    <strong>Android Deep Link Debugging & Orchestration Tool</strong>
  </p>
  <p align="center">
    A desktop application for debugging Android App Links and Deep Links across devices, manifests, and on-device verification state.
  </p>
</p>

<p align="center">
  <a href="#features">Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#usage">Usage</a> •
  <a href="#contributing">Contributing</a> •
  <a href="#license">License</a>
</p>

---

## Features

### Device Management
- Auto-detect connected Android devices with OS version and SDK level
- Concurrent multi-device support with per-device serial routing
- Configurable polling cadence via **Settings**

### Manifest Analyzer
- List installed packages and inspect every `<intent-filter>` (App Links, Custom Schemes, HTTP)
- Fire deep links straight to the device with one click and watch the response in the integrated log panel
- Export captured manifest data for sharing

### Diagnostics
- Validate `assetlinks.json` hosted at `https://<domain>/.well-known/assetlinks.json`
- Surface common failure modes — missing file, invalid JSON, redirects, wrong Content-Type, network errors
- Inline guidance for fixing each issue

### Verification Deep Dive
- Per-domain App Links verification state (`verified`, `legacy_failure`, `state_no_response`, ...)
- SHA-256 certificate fingerprint comparison between device and `assetlinks.json`
- Root-cause analyzer with actionable suggestions

### Local Hosting Simulation
- One-click Ktor server (bound to `127.0.0.1`) that serves a generated `assetlinks.json`
- Extract the signing fingerprint from a connected device
- End-to-end workflow: start server → trigger `pm verify-app-links --re-verify` → poll for state changes → tear down

### Intent Payload Sniffer
- Capture Bundle data from intents fired on a device
- Compare payload snapshots side-by-side to diff what changed between runs

### Deep Link Topology Map
- Interactive tree visualization of an app's URI structure (scheme → host → path)

### Scheme Collision Detector
- Find packages competing for the same custom scheme so chooser dialogs can be debugged

### Batch Deep Link Testing
- Run a scenario file containing many URIs against a device
- Parameter substitution for templated URIs (`{{userId}}`, etc.)
- Import/export scenarios as JSON

### Real-time Logcat Streaming
- Stream filtered logcat for deep-link-related signals while testing

### Magic QR Code Generator
- Generate a QR code for any deep link to test from a physical phone without typing

### Deep Link Favorites
- Save frequently used deep links and fire them with one click

### Settings
- Theme (Light / Dark / System) applied instantly
- ADB binary override path
- Default host:port for Local Hosting
- Device detection polling interval

### Keyboard Shortcuts
- `Cmd/Ctrl + R` — refresh devices
- `Cmd/Ctrl + F` — focus search
- `Esc` — close panel
- `?` — show all shortcuts

## Installation

### Prerequisites
- Java 17 or higher
- Android device with USB debugging enabled (or an emulator)
- ADB is optional — LinkOps will download the official `platform-tools` automatically if it isn't already on your `PATH`

### From Source

```bash
git clone https://github.com/manjees/link-ops.git
cd link-ops
./gradlew :composeApp:run
```

For faster iteration during UI work:

```bash
./gradlew :composeApp:runHot
```

### Pre-built Binaries

Coming soon — see [Releases](https://github.com/manjees/link-ops/releases).

## Usage

### 1. Connect a device
Plug in your phone with USB debugging enabled. LinkOps polls ADB and surfaces the device on the Dashboard automatically.

### 2. Inspect a package's deep links
**Manifest** → pick the device → search for the package → review every intent-filter with its verification status. Use **Test** to fire a deep link.

### 3. Validate a domain's setup
**Diagnostics** → enter your domain. LinkOps fetches `assetlinks.json` and tells you exactly what's wrong.

### 4. Investigate a verification failure
**Deep Dive** → pick the package. You'll see the device-reported state alongside the remote `assetlinks.json` and a fingerprint match check. The analyzer suggests fixes for each failure mode.

### 5. Pre-flight before publishing the real `assetlinks.json`
**Local Host** → extract the fingerprint from the device, preview the generated JSON, start the server, run the verification workflow. The Ktor server is `127.0.0.1`-only (see [#29](https://github.com/manjees/link-ops/issues/29) for the planned external-tunnel option that enables full on-device verification).

## Tech Stack

- **Kotlin Multiplatform** (JVM target) — single codebase, native desktop
- **Compose Multiplatform Desktop** + **Material3** — declarative UI
- **Clean Architecture** — `data` / `domain` / `ui` / `infrastructure` / `di`
- **Ktor** — HTTP client (`assetlinks.json` fetch) + embedded server (Local Hosting)
- **kotlinx.coroutines** — structured concurrency, `StateFlow` everywhere
- **kotlinx.serialization** — settings, favorites, scenario import/export
- **ZXing** — QR code generation

## Project Structure

```
composeApp/src/jvmMain/kotlin/com/manjee/linkops/
├── data/           # Repositories (impl), parsers, mappers, generators, analyzers, strategies
├── domain/         # Models, repository interfaces, use cases (no Android/JVM specifics)
├── infrastructure/ # ADB, network (Ktor client), server (Ktor server), QR
├── ui/             # Compose screens, reusable components, theme, navigation
└── di/             # AppContainer — manual wiring
```

Dependency direction is strictly `ui → domain ← data ← infrastructure`. All ADB calls go through `AdbShellExecutor`, all HTTP through `AssetLinksClient`, all local serving through `AssetLinksServer`.

## Contributing

Pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for the full guide.

### Quick Start

```bash
git clone https://github.com/YOUR_USERNAME/link-ops.git
git checkout -b feature/your-feature
./gradlew :composeApp:jvmTest
git push origin feature/your-feature
```

## Roadmap

### Done
- [x] Manifest Analyzer
- [x] AssetLinks Diagnostics
- [x] Verification Deep Dive (fingerprint comparison + root cause)
- [x] Local Hosting Simulation
- [x] Intent Payload Sniffer
- [x] Deep Link Topology Map
- [x] Scheme Collision Detector
- [x] Batch Deep Link Testing
- [x] Real-time Logcat Streaming
- [x] Magic QR Code Generator
- [x] Deep Link Favorites
- [x] Settings (theme, ADB override, hosting defaults, polling)
- [x] Keyboard Shortcuts

### Next
- [ ] External HTTPS tunnel for Local Hosting ([#29](https://github.com/manjees/link-ops/issues/29)) — real on-device verification via ngrok / cloudflared
- [ ] iOS Universal Links support ([#30](https://github.com/manjees/link-ops/issues/30)) — Android/iOS pair debugging
- [ ] CLI / Headless mode + GitHub Action ([#31](https://github.com/manjees/link-ops/issues/31)) — CI/CD integration
- [ ] APK file analysis ([#32](https://github.com/manjees/link-ops/issues/32)) — verify builds without installing

### Later
- [ ] Multi-language UI (i18n)
- [ ] Pre-launch checklist
- [ ] Emulator matrix automation (Android 11–15)

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor](https://ktor.io/)
- [ZXing](https://github.com/zxing/zxing)

---

<p align="center">
  Made with ❤️ for Android Developers
</p>
