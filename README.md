# HELLDYE

Android default-assistant client for Pi coding sessions. The phone shows a transparent Compose
overlay over the current app; Pi-Bolt keeps running on its host and is reached through a small
HTTP/SSE gateway.

The project is a clean build. It does not extend FRESKIEL.

## Status

- default-assistant role onboarding;
- `VoiceInteractionService` and session service;
- assist gesture captures a screenshot on request only; visible text is not read or sent automatically;
- transparent assist overlay with a blurred/dimmed background, opaque messages, and a bottom composer;
- opaque in-app session view; the overlay expand button opens the same Pi session there;
- photo, current-screen, and text-file attachments;
- slash commands (`/model`, `/new`, `/abort`, `/help`) plus Pi prompt templates and skills;
- model picker backed by Pi's available models and live session state;
- Pi gateway with one Pi-Bolt process per session, SSE replay, abort, multi-session support, and a
  read-only tool allowlist by default;
- gateway URL/token/workspace settings in the app;
- voice capture is not connected yet.

Pi-Bolt is the intended backend on both the PC and Termux. The Android app does not bundle Node or
run a Pi process itself.

## Install (Termux)

The repository is private, so the installer needs a GitHub token with **Contents: read** and
**Actions: read**. In Termux:

```bash
export GITHUB_TOKEN=<github-token>
bash -c 'curl -fsSL -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github.raw" "https://api.github.com/repos/freovensh0/helldye/contents/scripts/install-termux.sh?ref=main" | bash'
```

The script installs the latest successful `main` builds:

- gateway → `~/helldye-gateway/helldye-gateway`, restarted on `127.0.0.1:8787`;
- APK → `/sdcard/Download/Termux/HELLDYE.apk`;
- bearer token → `~/.config/helldye/token` (kept across updates).

It prints the gateway URL and token at the end. Enter both in HELLDYE settings, tap the APK to install,
and manage the gateway later with `hellgate start`, `stop`, `restart`, or `status`. `pi-bolt` must
already be on `PATH` (or pass `PI_BOLT=/path/to/pi-bolt`). Rerun the same command to update both parts.

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
4. Trigger the assistant from the system gesture. The overlay shows `SCREEN READY` when a screenshot
   was captured.

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
