# HELLDYE gateway contract

Contract version: **1** (`/v1`).
Gateway version: **0.1.0**.
Source of truth: `gateway/src/main.rs`. This document describes the implementation in this repository,
not a target design. Where the product contract needs behavior the gateway does not have yet, the gap is
called out explicitly.

The Android client talks only to these routes. Pi-Bolt runs on the host; the gateway owns the process,
the session list, and the event stream.

---

## 1. Transport and auth

- HTTP/1.1 with JSON bodies and `text/event-stream` for events. No WebSocket, no gRPC, no CORS headers.
- TLS is expected in front of the gateway (Tailscale Serve or a reverse proxy). The gateway itself
  serves plain HTTP.
- Every `/v1` route requires `Authorization: Bearer <token>`, including loopback. `GET /health` is
  the only unauthenticated route.
- The token comes from `HELLDYE_TOKEN` or `HELLDYE_TOKEN_FILE`. There is no `--token` flag. A token
  file must be mode `0600` on Unix and must be outside every workspace root; any other mode or an
  in-workspace file aborts startup. The gateway refuses to start without a token at all.
- Missing or wrong token: `401` with an empty body. There is no `WWW-Authenticate` header.
  Comparison is constant-time.
- Request bodies are limited to 12 MiB. Base64 images count against that limit.
- Unknown paths: `404`. A known path with the wrong method: `405`.
- Malformed JSON: axum's `Json` rejection (`400` or `422`, plain-text body).

The gateway also refuses to start without `HELLDYE_WORKSPACES` (see §7). The token is never logged,
never a command-line argument, and never enters the Pi child environment.

## 2. Routes

`{id}` is a session UUID string. The client sends the full path (`/v1/...`).

| Method | Path | Request | Success response |
|---|---|---|---|
| `GET` | `/health` | — | `200`, body `ok` |
| `GET` | `/v1/info` | — | `200` `{version, piVersion, capabilities}` |
| `GET` | `/v1/sessions` | — | `200`, array of `SessionInfo`, newest `created` first |
| `POST` | `/v1/sessions` | `{cwd?, ephemeral?}` | `200` `SessionInfo` |
| `POST` | `/v1/sessions/import` | — | `200` `{imported: [SessionInfo]}`; scans `HELLDYE_PI_SESSION_DIR` |
| `GET` | `/v1/sessions/{id}` | — | `200` `SessionInfo` |
| `DELETE` | `/v1/sessions/{id}` | — | `200` `{removed:true, transcriptKept:true}` |
| `GET` | `/v1/sessions/{id}/events` | `?replay=true|false`, `Last-Event-ID` | `200` SSE |
| `GET` | `/v1/sessions/{id}/messages` | `?limit=1..500&before=INDEX` | `200` transcript page + `eventId`, `epoch`, `started`, `total`, `hasMore` |
| `POST` | `/v1/sessions/{id}/prompt` | `{message, context?, images?}` | `200` `{accepted:true}` |
| `POST` | `/v1/sessions/{id}/abort` | `{}` | `200` `{accepted:true}` |
| `POST` | `/v1/sessions/{id}/compact` | `{}` | `200` `{accepted:true}` |
| `POST` | `/v1/sessions/{id}/name` | `{name}` | `200` `SessionInfo` |
| `GET` | `/v1/sessions/{id}/models` | — | `200` Pi `get_available_models` data |
| `GET` | `/v1/sessions/{id}/commands` | — | `200` Pi `get_commands` data |
| `GET` | `/v1/sessions/{id}/state` | — | `200` gateway turn state (+ Pi `get_state` data when running) |
| `POST` | `/v1/sessions/{id}/model` | `{provider, modelId}` | `200` Pi `set_model` data |
| `GET` | `/v1/sessions/{id}/stats` | — | `200` Pi `get_session_stats` data |
| `GET` | `/v1/sessions/{id}/thinking` | — | `200` Pi `get_available_thinking_levels` data |
| `POST` | `/v1/sessions/{id}/thinking` | `{level}` | `200` Pi `set_thinking_level` data |
| `POST` | `/v1/sessions/{id}/extension_ui_response` | `{id, value?, confirmed?, cancelled?}` | `200` `{accepted:true}` |

### 2.1 Session shape

```json
{
  "id": "uuid", "name": "Session 4F2A", "cwd": "/absolute/path",
  "status": "running|idle|stopped", "started": true, "busy": true,
  "onHost": false, "created": 1760000000
}
```

- `started` is process state. `false` means no Pi child is attached; reading the session does not start
  one.
- `busy` is turn state, true from prompt acceptance until `agent_settled`. `status` is `stopped` when
  not started, `running` while busy, and `idle` when started but not busy.
- `name` is generated as `Session <first 8 id characters, uppercase>`. It is never derived from a
  prompt. A rename is the only way to set a custom name, and it records that the name is explicit.
- A state file from an older gateway has no explicit-name marker, so every stored name is replaced
  with the neutral form on load; the old prompt text is never displayed or passed to Pi.
- `onHost` is true for records imported from `HELLDYE_PI_SESSION_DIR`. They are read-only metadata
  until a prompt starts them; removing one only removes the gateway record.
- `created` is Unix seconds (imported records use the session file timestamp).

### 2.1.1 Info

`GET /v1/info` requires auth and returns only:

```json
{
  "version": "0.1.0",
  "protocolVersion": 1,
  "minimumProtocolVersion": 1,
  "piVersion": null,
  "epoch": "gateway-instance-uuid",
  "capabilities": {
    "sessions": true, "images": true, "structuredContext": true, "workspaceAllowlist": true,
    "ephemeralSessions": true, "import": true, "fork": false, "clone": false,
    "tree": false, "export": false
  }
}
```

It never exposes a binary path, workspace path, cwd, or environment value. `piVersion` is `null`
until capability negotiation lands. `epoch` changes on every gateway start; a client must discard a
stored cursor when the epoch differs (see §3.1).

### 2.2 Create

- `cwd` must be an absolute path inside one of the `HELLDYE_WORKSPACES` roots. A missing or empty
  `cwd` uses the first allowed root, never the gateway process directory.
- `..`, relative paths, paths outside every root, and paths whose symlink target escapes the root are
  rejected with `400`.
- `name` is not accepted on create; use `POST /name` to rename.
- Creating a session does not start Pi and does not run a turn.
- `ephemeral: true` creates a one-shot screen-query session (see §6.1). It is not written to the state
  file, is not returned by `GET /sessions`, and is spawned as `pi --mode rpc --no-session` with the
  read-only tool profile. Omitting the field or sending `false` creates a normal persisted session.

### 2.3 Pi start behavior

Read paths never start Pi:

- `/messages` on a stopped session returns `{started:false, messages:[], eventId, epoch}`; the Phase 5
  host-transcript reader will serve its messages without a child.
- `/state` on a stopped session returns `{started:false, busy:false, status:"stopped",
  lastEventId, epoch}`.
- `/events` on a stopped session emits one `not_started` SSE event and closes.

`models`, `commands`, `stats`, `thinking`, `abort`, `compact`, and `extension_ui_response` still call
`ensure_started` because they need a live Pi. The app opens the event stream only after a prompt or an
explicit resume.

An idle Pi child is stopped after `HELLDYE_IDLE_SECS` (default 1800, `0` disables) without a busy turn
and without SSE subscribers. The session record is kept; the next prompt starts Pi again.

### 2.4 Prompt

```json
{
  "message": "What is wrong with this layout?",
  "context": {
    "files": [{ "name": "notes.md", "content": "..." }]
  },
  "images": [{ "data": "<base64>", "mimeType": "image/jpeg" }]
}
```

- `context` is optional structured data. The gateway serializes it as JSON inside a labeled
  `BEGIN CONTEXT DATA` / `END CONTEXT DATA` block, prepends a rule that the block is untrusted data
  and not instructions, and then appends `message`. The client must not pre-wrap content in
  `<file>` tags. JSON escaping keeps `</file>`, fake system messages, and URLs inside the data block.
- `images` carries zero to four images. Each must be JPEG or PNG, at most 1,568 px on the long edge,
  at most 1.5 MB decoded, and at most 6 MB decoded in total. The gateway validates MIME type, base64,
  magic bytes, dimensions, and size before forwarding.
- For an ephemeral session (§6.1) the same prompt shape is used; the image and answer are not persisted
  by the gateway and Pi runs with `--no-session`.
- When images are present, the gateway prefixes an `IMAGE DATA RULE` block (or includes the image
  warning in the context rule) that labels the image as untrusted data, not instructions.
- `message` may be empty only when at least one image is present; otherwise `400 message is empty`.
- While a turn is streaming, the gateway adds `streamingBehavior: "steer"` to the Pi command.
- The response means "accepted by the gateway for delivery to Pi", not "turn finished". Output arrives on
  SSE.

### 2.5 Messages, pagination, and cursor

`GET /messages` returns the newest page by default (`limit` 500, `before` omitted):

```json
{ "started": true, "messages": [ ... ], "total": 512, "hasMore": true, "eventId": 42, "epoch": "uuid" }
```

- `limit` is clamped to `1..=500`. `before` is an exclusive index into Pi's full message list; pass the
  start index of the previous page to walk backwards. The app keeps list keys stable while prepending.
- `eventId` is the per-session event id at the moment Pi answered. Open the event stream with
  `Last-Event-ID: 42` to receive events after the transcript snapshot.
- `started:false` means Pi is stopped; the gateway never spawns it for this route.

### 2.5.1 Host import

`POST /v1/sessions/import` is explicit; the gateway never scans automatically. It reads up to 500
`*.jsonl` files under `HELLDYE_PI_SESSION_DIR` (max depth 3, files up to 2 MB), extracts only the first
top-level `cwd` and the latest `timestamp`/`updated`/`mtime` from each line set, ignores malformed and
trailing partial lines, and validates the `cwd` against `HELLDYE_WORKSPACES`. Valid files become
`onHost: true` records with a neutral name; existing ids are skipped, so a removed import does not
reappear until imported again. No Pi process is started and no transcript is read into the response.
If the env var is unset or not a directory the route returns `400`.

### 2.6 Rename

`name` is trimmed; empty is `400 name is empty`. This is the only route that changes a name and it
marks the name as explicit. If Pi is running, the gateway also sends `set_session_name` and returns
`502` if Pi rejects it. The record is persisted after a successful rename.

### 2.7 Session removal

`DELETE /v1/sessions/{id}` removes the gateway record, atomically rewrites the state file, drops Pi's
stdin, and returns `{"removed":true,"transcriptKept":true}`. The Pi child exits when its stdin closes;
the gateway sends no signal and does not wait. The Pi session file stays on the host. There is no purge
route: see §6. The app must call this "Remove from list", never "delete the transcript".

## 3. SSE

```
GET /v1/sessions/{id}/events
Accept: text/event-stream
Last-Event-ID: 42
```

- `id:` is a per-session `u64`, starting at 1. Synthetic gateway events (`gateway_epoch`, `not_started`,
  `resync`) carry no id, so they never advance the client cursor.
- `event:` is Pi's `type` field from the JSON line (for example `agent_start`, `agent_settled`, a
  message or tool event), or `raw` when the line is not JSON.
- `data:` is the raw JSON line Pi wrote, unmodified, for Pi events.
- `gateway_epoch` is sent first on every stream: `{"type":"gateway_epoch","epoch":"uuid"}`.
- `gateway_exit` is gateway-generated with `{"type":"gateway_exit","reason":"pi exited"}` when the Pi
  child exits.
- `not_started` is emitted alone when the session has no Pi child; the stream then closes and Pi is not
  spawned.
- `resync` is emitted if the broadcast ring drops events for a slow subscriber:
  `{"type":"resync","reason":"lagged","lastEventId":"41"}`. The stream then closes. The client must
  `GET /messages` and reconnect after the returned `eventId`; it must never continue past a silent gap.
- A keep-alive comment is sent every 15 seconds.

Replay:

- Default `replay=true` uses `Last-Event-ID`; a missing or unparseable header means 0.
- A cursor greater than the current id resets to 0 (replay the whole buffer).
- `?replay=false` starts at the current id; only new events are sent.
- The buffer is an in-memory ring of 4,096 events per session. Older events are gone; `/messages` is the
  durable path.
- Subscribers that fall behind the broadcast channel receive `resync`, not a silent skip.

### 3.1 `eventId` and epochs

The gateway instance id is the epoch, exposed as `epoch` on `/v1/info`, `/v1/sessions/{id}/state`,
`/messages`, and the first SSE event (`gateway_epoch`).

- Event ids increase monotonically per session for the life of the gateway process, including across a
  Pi restart.
- A gateway process restart gets a new epoch and per-session ids start at 1 again.
- A client that sees a different epoch, a lower id than its cursor, or a `404` must discard the cursor:
  reload `/messages`, replace the transcript, and stream after the returned `eventId`. Never
  concatenate events from two epochs.

## 4. Busy and streaming

`busy` is on the wire in `SessionInfo` and `/state`; it is true from prompt acceptance until
`agent_settled`.

- `agent_start` also sets the streaming flag; `agent_settled` clears it.
- The gateway uses the same flag to decide `streamingBehavior: "steer"`.
- `abort` sets `stopping` until `agent_settled`; `/state` exposes it.
- `gateway_exit` means the Pi child is gone; `status` becomes `stopped`.

Send is allowed while busy; the gateway marks a prompt sent during a turn as a steer.

## 5. Errors

Error bodies are plain text, not JSON. Empty bodies are possible.

| Status | When |
|---|---|
| `400` | Invalid `cwd`, `cwd` outside the workspace allowlist, `cwd` with `..` or a relative path, empty `message` with no image, empty `name`, malformed JSON fields. |
| `401` | Missing or wrong token. |
| `404` | Session id not found, unknown path. |
| `405` | Known path, wrong method. |
| `413` | Request body over 12 MiB, more than four images, an image over 1.5 MB or 6 MB total, or an image whose long edge exceeds 1,568 px. |
| `415` | Unsupported image MIME type, invalid base64, or bytes that do not match the declared image type. |
| `422` | JSON body parsed but a required field has the wrong type. |
| `502` | Pi failed to start, stdin is closed, a Pi command failed, or a Pi command timed out (10–15 s). |

The app surfaces `HTTP <code>: <body>`; do not parse the body for branching.

## 6. Retention

- The gateway writes no prompt, image, or transcript to its own disk path. Pi owns transcripts.
- `sessions.json` (`HELLDYE_STATE_FILE` or `~/.local/share/helldye/sessions.json`) stores only
  `id`, `name`, `cwd`, `created`, whether the name is explicit, the last-known model/thinking, and
  whether the record was imported (`on_host`). It is rewritten in full on create,
  rename, delete, and state migration. The write is atomic (temp file, fsync, rename, directory fsync)
  and serialized, so a crash leaves either the previous file or the new one.
- An ephemeral screen-query session never enters `sessions.json` and never appears in the session
  list. See §6.1.
- On load, a record whose `cwd` is outside the current workspace allowlist is dropped, and a record
  without an explicit-name marker gets the neutral `Session <short id>` name.
- The event ring is memory only and dies with the process.
- Pi persists each session under `--session-id` in Pi's session storage on the host. Prompts and images
  are part of that transcript.
- **Remove from list** is the phone action: it removes the gateway record only.
- **Purge on host** is manual: stop the gateway and delete the Pi session file for that id. The gateway
  has no purge route.

### 6.1 Ephemeral sessions

Implemented for screen queries. `POST /v1/sessions` with `{"cwd": "...", "ephemeral": true}` creates an
in-memory session record:

- it is never written to `sessions.json` and never returned by `GET /v1/sessions`;
- it is spawned as `pi --mode rpc --no-session --tools read,grep,find,ls` (the read-only profile is
  fixed for this path, even when `HELLDYE_TOOLS` enables actions for normal sessions);
- `POST /prompt` and `GET /events` work as usual, but `GET /messages` is not part of the screen-query
  flow: Pi keeps no session file, so there is no transcript to read back;
- the record is deleted when Pi emits `agent_settled` (the answer has been streamed), when the client
  sends `DELETE /v1/sessions/{id}`, or after 120 seconds with no event. The last case only bounds an
  abandoned screen query; the normal idle reaper (`HELLDYE_IDLE_SECS`) separately stops — but keeps the
  record of — idle persistent sessions;
- no prompt, image, or answer from an ephemeral session is persisted by the gateway, and the gateway
  never writes the image to disk. The image is base64 in memory and goes to the Pi child's stdin only.

A text-only prompt still uses a normal persisted session. The app creates an ephemeral session only
when a screenshot is attached to the submit.

Until this section existed, every session was started with `--session-id` and a screenshot could land
in the Pi transcript. That is no longer true for the screen-query path. A future persistent
screen-attachment feature needs an explicit retention decision first.

## 7. Workspace and tools

- `HELLDYE_WORKSPACES` is a required colon-separated allowlist of absolute directories. Each root is
  canonicalized at startup; a missing or non-directory root aborts startup.
- A session `cwd` is canonicalized per create and fixed for the session's life. It must be inside a
  root; `..`, relative paths, absolute paths outside every root, and symlink escapes are rejected.
  The default is the first allowed root, not the gateway process directory.
- The tool allowlist comes from `--tools` or `HELLDYE_TOOLS`; the default is `read,grep,find,ls`.
  `*` enables every tool, including extension tools. It is applied when the Pi child starts, so a
  change requires restarting the gateway or the session. Actions remain off until explicitly enabled.
- Pi is spawned with `current_dir` set to the session `cwd` and an empty environment plus an explicit
  allowlist (`PATH`, `HOME`, locale, timezone, XDG directories, provider API keys). `HELLDYE_TOKEN`,
  `HELLDYE_TOKEN_FILE`, CI variables, and every other inherited value are absent from the child.
- Same-user isolation is not a sandbox: Pi can still read files the gateway user can read. Use a
  separate host user, container, or VM for strong isolation.
- Pi stderr is drained but not logged unless `HELLDYE_DEBUG=1`. Debug lines are bounded and prompt-like
  or image payloads are dropped/redacted. No prompt or image body is logged at any level.

## 8. One writer

- The gateway takes an advisory lock for its state directory (`gateway.lock`, a Unix socket next to
  `sessions.json`) and refuses to start while another live gateway owns it. A stale socket from a dead
  process is replaced after a failed connect.
- `/state` serves the last-known model and thinking level for a stopped session from
  `sessions.json`, so the composer is truthful without spawning Pi.
- The gateway is the only writer to a live Pi session. It spawns one child per session with
  `--session-id` and owns its stdin and stdout.
- Do not run a second writer against the same session (for example the Pi TUI) while the gateway is
  live. Stop the gateway first.
- Any number of SSE subscribers may read one session; they all receive the same events.
- HTTP clients share the gateway's stdin. Two clients prompting the same session are serialized by the
  gateway; "one writer" constrains processes, not phone connections.
- A client detach does not abort a turn. A gateway restart loses the in-memory ring; reattach with
  `/messages` + `eventId`. The gateway does not guarantee that a Pi child survives its own restart.
