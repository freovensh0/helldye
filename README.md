# HELLDYE

Android default-assistant client for Pi coding sessions. The phone shows a transparent Compose
overlay over the current app; Pi-Bolt keeps running on its host and is reached through a small
HTTP/SSE gateway.

The project is a clean build. It does not extend FRESKIEL.

## Status

- default-assistant role onboarding;
- `VoiceInteractionService` and session service;
- assist gesture reads visible text only (no screenshot), capped at 4,000 characters;
- transparent assistant overlay with a bottom composer, photo/screen/file attachments, streaming
  replies, and a full-session view;
- Pi gateway with one Pi-Bolt process per session, SSE replay, abort, multi-session support, and a
  read-only tool allowlist by default;
- gateway URL/token/workspace settings in the app;
- voice capture and real screenshots are not connected yet.

Pi-Bolt is the intended backend on both the PC and Termux. The Android app does not bundle Node or
run a Pi process itself.

## Build

The Android build and every later build run on GitHub Actions. There is no local Android SDK on the
development machine.

- CI: <https://github.com/freovensh0/helldye/actions>
- Workflow: `.github/workflows/ci.yml`
- Artifact: `helldye-debug` (`app-debug.apk`)
- Local build is optional diagnostics only, not build evidence.

Gateway:

```sh
cd gateway
cargo build --release
HELLDYE_TOKEN=change-me ./target/release/helldye-gateway --listen 127.0.0.1:8787 --pi /path/to/pi-bolt
```

See `gateway/README.md` for Tailscale setup and the HTTP/SSE contract.

## Pairing

1. Install the APK.
2. Set HELLDYE as the default assistant.
3. Open **Pi gateway → CONFIGURE** and enter the gateway URL and bearer token.
4. Trigger the assistant from the system gesture. The overlay shows `SCREEN ON` when assist text
   was received.

## Docs

- `docs/assistant-app-research.md` — platform APIs, role flow, screen context, MVP.
- `docs/ui-design-report.md` — visual tokens, Compose theme, screen specs.
- `docs/mockups/ui-board.html` — approved visual board.
- `gateway/README.md` — Pi-Bolt gateway contract and operation.
- `AGENTS.md` — repository, commit, push, and build rules.

## Stack

Kotlin, Jetpack Compose, Material 3, minSdk 29, target/compile SDK 37. The Android Gradle scaffold and
Gradle wrapper come from the Android Architecture Templates base branch (Apache-2.0); the app code is
written for HELLDYE. Fonts are bundled under the SIL Open Font License.

The gateway is Rust (`axum`/`tokio`) and has no Android dependency.
