# HELLDYE

Android default-assistant client for Pi coding sessions. The phone shows an opaque Compose overlay
over the current app; Pi-Bolt keeps running on its host and is reached through a small HTTP/SSE
gateway.

The project is a clean build. It does not extend FRESKIEL.

## Status

- default-assistant role onboarding;
- `VoiceInteractionService` and session service;
- assist gesture captures a screenshot on request only; the app never reads screen text, OCRs pixels, or
  scrapes the clipboard;
- opaque assist overlay (no blur, no dim) with opaque messages and a bottom composer;
- opaque in-app session view; the overlay expand button opens the same Pi session there;
- secure windows and a device-credential unlock gate before any assistant content on the lockscreen;
- photo, camera, screenshot, clipboard, and text-file attachments;
- slash commands (`/model`, `/thinking`, `/new`, `/abort`, `/compact`, `/help`) plus Pi prompt
  templates and skills;
- model and thinking pickers backed by Pi's available models and session state;
- Pi-style usage line under the composer: model, thinking, tokens, cost, cache hit, and context;
- markdown rendering: headings, lists, todo items, tables, links, bold/italic/strike, inline code,
  and fenced code blocks; streaming thinking blocks; collapsible tool cards with arguments,
  results, and durations;
- stop button, queued steering messages while a turn runs, and `/compact`;
- session picker: create, switch, rename, and remove persisted sessions; opening a session restores
  its Pi transcript and resumes the live event stream from that point, including after a gateway or
  Pi restart;
- voice input through the system speech recognizer;
- extension UI: in-app notices, status entries, widgets, editor prefill, and
  `select`/`confirm`/`input`/`editor` dialogs;
- Pi gateway with one Pi-Bolt process per session, SSE replay, abort, multi-session support, and a
  read-only tool allowlist by default;
- gateway URL/token/workspace settings in the app;
- still missing: multiple gateway hosts, fork/export/share, PDF/DOCX and assistant media previews.

Pi-Bolt is the intended backend on both the PC and Termux. The Android app does not bundle Node or
run a Pi process itself.

Screen context is screenshot-only. The app never reads screen text, traverses `AssistStructure`, runs
OCR, or uses `AccessibilityService` or `MediaProjection`. A capture is staged in memory, sent only
when you submit, and never written to preferences, files, or logs; removing it or closing the overlay
drops it. Screen queries run in an ephemeral Pi session (`--no-session`) and are not added to a
persistent transcript.

Removing a session in the app is **remove from list**: it deletes the gateway record only. The Pi
transcript stays on the host. **Purge on host** means stopping the gateway and deleting the Pi session
file yourself; the phone has no purge action and never claims a session was deleted.

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
- bearer token → `~/.config/helldye/token` (kept across updates, mode `0600`);
- workspace allowlist → `HELLDYE_WORKSPACES`, defaulting to `~/helldye-workspace`.

Set `HELLDYE_WORKSPACES` to your project directories before starting the gateway; the app can only
open a session inside that colon-separated allowlist. The token is required for every start and is
never passed as a command-line argument.

It prints the gateway URL, token, and the installed build SHA. Enter the URL and token in HELLDYE
settings, tap the APK to install, and manage the gateway with `hellgate start`, `stop`, `restart`, or
`status`. If the app reports `HTTP 401`, run `hellgate token` and paste that exact value.

If Android says “app not installed”, uninstall the previous HELLDYE once: CI keeps a stable debug key,
so later builds update over it. File managers may show the APK's uncompressed size; the file is
about 12 MB. `pi-bolt` must already be on `PATH` (or pass `PI_BOLT=/path/to/pi-bolt`). Rerun the same
command to update both parts.

Pi starts read-only (`read,grep,find,ls`). To give the agent more:

```bash
hellgate tools all          # all tools, including extension tools
hellgate tools read-only    # back to read,grep,find,ls
hellgate tools select       # interactive picker
hellgate tools read,bash    # custom list
hellgate restart
```

This gives the agent shell and write access on the gateway host. `hellgate tools reset` clears the
override and returns to the read-only default.

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
HELLDYE_TOKEN=change-me \
HELLDYE_WORKSPACES=/home/you/projects \
./target/release/helldye-gateway --listen 127.0.0.1:8787 --pi /path/to/pi-bolt
```

A bearer token is required for every start; `HELLDYE_TOKEN_FILE` is the preferred form on a host.
`HELLDYE_WORKSPACES` is a colon-separated allowlist and is also required. See `gateway/README.md`
for Tailscale setup and `docs/gateway-contract.md` for the HTTP/SSE contract.

## Pairing

1. Install the APK.
2. Set HELLDYE as the default assistant.
3. Open **Pi gateway → CONFIGURE** and enter the gateway URL, bearer token, and a workspace inside
   the host's `HELLDYE_WORKSPACES` allowlist. Use HTTPS; cleartext is accepted only for loopback in a
   debug build.
4. Trigger the assistant from the system gesture. The overlay shows `SCREEN READY` when a screenshot
   was captured.

## Docs

- `docs/assistant-app-research.md` — platform API research and the role flow; background only, not
  product behavior.
- `docs/ui-design-report.md` — visual tokens, Compose theme, screen specs.
- `docs/mockups/ui-board.html` — approved visual board.
- `docs/gateway-contract.md` — versioned gateway endpoints, events, errors, and retention.
- `gateway/README.md` — Pi-Bolt gateway operation and Tailscale setup.
- `AGENTS.md` — repository, commit, push, and build rules.

## Stack

Kotlin, Jetpack Compose, Material 3, minSdk 29, target/compile SDK 37. The Android Gradle scaffold and
Gradle wrapper come from the Android Architecture Templates base branch (Apache-2.0); the app code is
written for HELLDYE. Fonts are bundled under the SIL Open Font License.

The gateway is Rust (`axum`/`tokio`) and has no Android dependency.
