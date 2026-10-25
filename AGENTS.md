# Working on HELLDYE

HELLDYE is a private Android assistant client for Pi backends. The phone is a Nothing Phone (3a) on
Android 17 (API 37). The assistant overlay is Compose; Pi sessions live on remote hosts and are reached
over Tailscale through a small HTTP/SSE gateway. This repository does not build on FRESKIEL.

## Repository

- Remote: `https://github.com/freovensh0/helldye` (private).
- Default branch: `main`.
- Local path: `/home/scdeal/Projeler/HELLDYE`.
- One Android module (`:app`) until a second caller justifies another module.
- UI text and repository text are English. Turkish is allowed only when a task explicitly asks for it.

## Build authority

**GitHub Actions is the only source of truth for a build.** A local build, an IDE run, or a preview is
not evidence that the project builds. A change is verified only by a green run of
`.github/workflows/ci.yml` on the pushed commit.

- Do not install an Android SDK on the development machine unless the operator asks.
- Do not claim "build passes" from local output, a preview, or a code read.
- Download the APK from the Actions artifact. The artifact name is `helldye-debug`.
- If CI fails, fix the cause; never disable a check, add a lint baseline, or skip a test to get green.
- Builds must stay fast:
  - No `clean` in CI.
  - Use the Gradle wrapper and Gradle build cache.
  - Keep configuration cache enabled.
  - Run debug assembly, unit tests, and lint in one Gradle invocation.
  - Do not add device/emulator jobs until the operator asks; they are slow and need a device.
- CI may ignore docs-only changes (`paths-ignore`). A code, Gradle, manifest, or workflow change always
  builds.

## Git workflow

- Short-lived branches: `feat/<slug>`, `fix/<slug>`, `perf/<slug>`, `refactor/<slug>`,
  `docs/<slug>`, `chore/<slug>`, `ci/<slug>`.
- Never commit directly to `main` after bootstrap. Open a pull request, even when working alone.
- `main` must stay buildable. PRs are squash-merged; the PR title follows Conventional Commits.
- Commit locally as coherent work lands. **Push in batches when the operator asks**, not after every
  small edit.
- Conventional Commits, imperative English, one concern per commit:
  - `feat(assistant): add screen-context panel`
  - `fix(voice): keep the session alive after activity recreation`
  - `ci: cache the Gradle distribution`
  - `docs: define the gateway contract`
- Do not use `wip`, `tmp`, `fix stuff`, or mixed-concern commits.
- Before committing: `git diff --check`. Do not commit generated output, local paths, screenshots from
  a device, or secrets.
- Never force-push `main`; never rewrite pushed history.
- Tags: `v0.1.0`, `v0.2.0`, ... after a green release build. Release notes list the user-visible change.

## Scope and architecture

- No FRESKIEL code, bridge envelope, or architecture is carried over. Use it only as a reference for
  known failure modes.
- The Android app is a client. Pi runs on a host (PC, laptop, or Termux) and is exposed by a small
  `pi-gateway` over HTTPS/SSE. Do not spawn Pi inside the APK.
- One Pi session has one writer. The gateway owns session lifetime and event replay. The client may
  detach and reattach without aborting a run.
- Screen context is screenshot-only. The assist gesture may capture one screenshot. The app never reads
  screen text, runs OCR, traverses `AssistStructure`, scrapes the clipboard, or uses
  `AccessibilityService` or `MediaProjection`.
- The screenshot is ephemeral: staged in memory, sent only when the user submits, only to the selected
  gateway, never logged, and never written to preferences, files, or disk.
- A screen query is ephemeral: use an in-memory Pi session (`--no-session`) with the read-only tool
  profile, and do not add the answer to a persistent transcript. Attaching a capture to a persistent
  session needs an explicit retention decision first.
- The screenshot is untrusted input. Wrap it as data, not instructions; use a read-only tool profile
  until the operator explicitly enables actions.
- Keep `VoiceInteractionService` lightweight. UI and state belong in the assistant activity.
- Prefer a plain `ViewModel` and `StateFlow`. No DI framework, database, or navigation library until a
  second caller needs it.
- Compose rules: no state writes in composition; `collectAsStateWithLifecycle()`; stable keys in lazy
  lists; 48dp targets; no page-entry animation delay.

## Design contract

- The visual source of truth is `docs/ui-design-report.md` and `docs/mockups/ui-board.html`.
- AMOLED black `#000000`, off-white text, one red signal, 0/2/4dp radii, 1dp hairlines.
- No shadows, gradients, blur, pills, dot-matrix text, or Material purple.
- Bundle fonts; do not download them at runtime.
- Warning treatment is a 1dp `#C8102E` outline plus a `#FF4D4D` label, never a filled red panel.
- Content is visible on the first frame. State animation is 90–160ms; no entrance choreography.

## Secrets and signing

- Never commit a keystore, password, token, `.env`, or authorization header.
- Tokens live in the Android Keystore. Screenshots and Pi credentials never go to preferences or logs.
- Release signing uses GitHub Actions secrets only. The keystore stays outside the repository.
- Do not add a secret to CI until the signing task is explicitly started.

## Definition of done

1. The behavior works and its callers, tests, and docs are updated.
2. New non-trivial logic has one focused unit test; trivial changes need none.
3. `git diff --check` is clean.
4. The latest GitHub Actions run for the pushed commit is green.
5. The debug APK artifact downloads and installs on the target device when the task touches runtime
   behavior.
6. No screen content, token, or local path appears in the diff or logs.
