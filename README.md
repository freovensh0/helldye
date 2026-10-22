# HELLDYE

Android default-assistant client for Pi coding sessions. The phone shows a black Compose surface over
the current app; Pi keeps running on its host and is reached through a small HTTP/SSE gateway.

The project is a clean build. It does not extend FRESKIEL.

## Status

Bootstrap shell:

- default-assistant role onboarding;
- `VoiceInteractionService` and session service;
- assist gesture reads visible text only (no screenshot), capped at 4,000 characters;
- Compose assistant activity with a local fake answer;
- Pi gateway and voice capture are not connected yet.

The first build and every later build run on GitHub Actions. There is no local Android SDK on the
development machine.

## Build

- CI: <https://github.com/freovensh0/helldye/actions>
- Workflow: `.github/workflows/ci.yml`
- Artifact: `helldye-debug` (`app-debug.apk`)
- Local build is optional diagnostics only, not build evidence.

## Docs

- `docs/assistant-app-research.md` — platform APIs, role flow, screen context, MVP.
- `docs/ui-design-report.md` — visual tokens, Compose theme, screen specs.
- `docs/mockups/ui-board.html` — approved visual board.
- `AGENTS.md` — repository, commit, push, and build rules.

## Stack

Kotlin, Jetpack Compose, Material 3, minSdk 29, target/compile SDK 37. The Android Gradle scaffold and
Gradle wrapper come from the Android Architecture Templates base branch (Apache-2.0); the app code is
written for HELLDYE. Fonts are bundled under the SIL Open Font License.
