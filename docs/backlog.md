# HELLDYE — Remaining Work

Status: 2026-10-25. Completed phases live in git history. Fork and clone are **out of scope** by
operator decision; capabilities stay `false`.

## 1. Open verification

- **Assist screen capture**: after the `FLAG_SECURE` removal and `usesAssistData=true` change, verify
  the gesture from a normal (non-HELLDYE) app shows `SCREEN TEMP` with a preview. If it still reports
  `BLOCKED`, collect `adb shell dumpsys voiceinteraction` and `logcat -s VoiceInteractionServiceManager`
  to confirm whether Nothing OS refuses third-party assist screenshots; do not guess further.
- **Device matrix**: locked device gate, recents (no thumbnail), airplane mode, rotation during
  streaming, process death during send, Termux install, secure/DRM source app.
- **Remote-host paths**: `@/sdcard/...` only works when Pi runs on the same device (Termux). A remote
  host cannot read phone paths; the UI must keep saying so.

## 2. Path references (new attachment model)

- Cloud-only providers (Google Photos, Drive) expose no local path; currently resolved by
  `RELATIVE_PATH`/`DISPLAY_NAME` which can point at a file that is not downloaded. Detect and warn.
- Paths containing spaces or newlines are inserted verbatim after `@`; confirm Pi's mention syntax and
  escape/quote if needed.
- Camera photos are inserted into `Pictures/Helldye` through MediaStore; verify the path is readable by
  the Termux gateway and clean orphaned entries when the host cannot read them.
- Pasted text still uploads as text context; decide whether paste should also become a temp-file path.

## 3. Coding surface (Phase 6 remainder)

- Worktree sheet: list/create UI over `/worktrees`, with the fixed branch/path validation and a clean
  confirmation.
- Per-session "Allow edits" control + warning banner (gateway endpoint exists; requires
  `HELLDYE_ACTION_TOOLS`), disabled while running.
- Long-press a diff line to quote `file:line` into the composer (G-02). No editor, no GitHub.

## 4. Mobile parity (Phase 7 remainder)

- **M-03** multi-host profiles (≤5): name/URL/token/workspace, Keystore token per profile, switch
  cancels streams, per-host cursor, removal.
- **M-04** content-free completion/failure notification while backgrounded; `POST_NOTIFICATIONS` on
  opt-in; short-lived active-turn foreground service with the least-privileged declared type, stopped
  at settle; document platform refusal fallback.
- **M-06** voice input partial text, permission denial, no-match, offline; prefer on-device recognition;
  language row in Settings. No advertised `RecognitionService` beyond the required stub.
- **M-08** rotation/multi-window/duplicate-submit lifecycle tests.

## 5. Hardening and release (Phase 8)

- **H-01** gateway load/soak: fake Pi, 50 SSE subscribers, 100k events, one slow reader, reconnects,
  restart; assert no gap after resync and bounded memory.
- **H-02** deterministic negative/fuzz corpus: malformed JSON, bad cursors, oversized images, invalid
  session ids, path traversal, truncated Pi output; CI job.
- **H-04** app state-machine tests: restore/reattach, lag resync, failed-send rollback, auto-scroll,
  screenshot staging, draft restore, delete copy, rapid switches.
- **H-05** accessibility: Compose semantics labels/roles/targets, TalkBack pass, 2.0× font scale,
  contrast, reduced motion; fix blockers.
- **H-06** shared JSON contract fixtures for session/state/message/error payloads; fail on drift.
- **H-07** install `helldye-debug`, run the device checklist, record commit + run URL.
- **H-08** dependency/license review; keep OFL font licenses; no analytics or network SDKs.
- **Release checklist**: green CI on the pushed commit, APK installs, no screen content/token/local
  path in diff or logs.

## 6. Known limits (not planned work)

- Fork/clone remain capability-`false` (Pi RPC changes the live session identity; needs a gateway
  record design).
- Imported stopped sessions show the host transcript read-only; resuming them starts Pi on the first
  prompt.
- No OCR, screen-text fallback, accessibility scraping, or gallery.
