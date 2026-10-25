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
- Every route requires `Authorization: Bearer <token>` when a token is configured, including loopback.
  `GET /health` is always unauthenticated.
- The token comes from `HELLDYE_TOKEN`, optionally overridden by `--token`. If neither is set, auth is
  disabled.
- Missing or wrong token: `401` with an empty body. There is no `WWW-Authenticate` header.
- Request bodies are limited to 12 MiB. Base64 images count against that limit.
- Unknown paths: `404`. A known path with the wrong method: `405`.
- Malformed JSON: axum's `Json` rejection (`400` or `422`, plain-text body).

A token is optional today. Phase 1 makes it mandatory for every `/v1` route.

## 2. Routes

`{id}` is a session UUID string. The client sends the full path (`/v1/...`).

| Method | Path | Request | Success response |
|---|---|---|---|
| `GET` | `/health` | — | `200`, body `ok` |
| `GET` | `/v1/info` | — | `200` `{name, version, piBin, sessions}` |
| `GET` | `/v1/sessions` | — | `200`, array of `SessionInfo`, newest `created` first |
| `POST` | `/v1/sessions` | `{cwd?, name?}` | `200` `SessionInfo` |
| `GET` | `/v1/sessions/{id}` | — | `200` `SessionInfo` |
| `DELETE` | `/v1/sessions/{id}` | — | `204`, empty body |
| `GET` | `/v1/sessions/{id}/events` | `?replay=true|false`, `Last-Event-ID` | `200` SSE |
| `GET` | `/v1/sessions/{id}/messages` | — | `200` Pi `get_messages` data + `eventId` |
| `POST` | `/v1/sessions/{id}/prompt` | `{message, context?, images?}` | `200` `{accepted:true}` |
| `POST` | `/v1/sessions/{id}/abort` | `{}` | `200` `{accepted:true}` |
| `POST` | `/v1/sessions/{id}/compact` | `{}` | `200` `{accepted:true}` |
| `POST` | `/v1/sessions/{id}/name` | `{name}` | `200` `SessionInfo` |
| `GET` | `/v1/sessions/{id}/models` | — | `200` Pi `get_available_models` data |
| `GET` | `/v1/sessions/{id}/commands` | — | `200` Pi `get_commands` data |
| `GET` | `/v1/sessions/{id}/state` | — | `200` Pi `get_state` data |
| `POST` | `/v1/sessions/{id}/model` | `{provider, modelId}` | `200` Pi `set_model` data |
| `GET` | `/v1/sessions/{id}/stats` | — | `200` Pi `get_session_stats` data |
| `GET` | `/v1/sessions/{id}/thinking` | — | `200` Pi `get_available_thinking_levels` data |
| `POST` | `/v1/sessions/{id}/thinking` | `{level}` | `200` Pi `set_thinking_level` data |
| `POST` | `/v1/sessions/{id}/extension_ui_response` | `{id, value?, confirmed?, cancelled?}` | `200` `{accepted:true}` |

### 2.1 Session shape

```json
{ "id": "uuid", "name": "string", "cwd": "/absolute/path", "status": "running|stopped", "created": 1760000000 }
```

- `status` is process state, not turn state. `running` means a Pi child is attached; `stopped` means it
  exited or was never started.
- `name` defaults to the first 8 characters of the id. It is never derived from a prompt.
- `created` is Unix seconds.

### 2.2 Create

- `cwd` is canonicalized and must be an existing directory. Missing `cwd` uses the gateway process's
  current directory. Invalid path: `400 invalid cwd: ...`; not a directory: `400 cwd is not a directory`.
- No workspace allowlist is enforced. Any directory the gateway user can enter is accepted. Phase 1
  adds the allowlist.
- Creating a session does not start Pi and does not run a turn.

### 2.3 Pi start behavior

Today `/messages`, `models`, `commands`, `state`, `stats`, `thinking`, `abort`, `compact`, and
`extension_ui_response` start the Pi child if it is stopped (`ensure_started`). The product contract says
reading a transcript must not start Pi; Phase 5 changes that. Until then, opening a session can spawn
Pi.

### 2.4 Prompt

```json
{
  "message": "What is wrong with this layout?",
  "context": "<file name=\"notes.md\">...</file>",
  "images": [{ "data": "<base64>", "mimeType": "image/jpeg" }]
}
```

- `context` is optional. It is trimmed and prepended to `message` as `context + "\n\n" + message`.
  The gateway does not escape or reinterpret it.
- `images` carries zero or more images. The app sends at most one, the staged screenshot. The gateway
  forwards `data` and `mimeType` to Pi and does not decode, log, or write the bytes.
- `message` may be empty only when at least one image is present; otherwise `400 message is empty`.
- While a turn is streaming, the gateway adds `streamingBehavior: "steer"` to the Pi command.
- The response means "accepted by the gateway for delivery to Pi", not "turn finished". Output arrives on
  SSE.

### 2.5 Messages and cursor

`GET /messages` returns Pi's `get_messages` data with an added `eventId`:

```json
{ "messages": [ ... ], "eventId": 42 }
```

`eventId` is the per-session event id at the moment Pi answered. Open the event stream with
`Last-Event-ID: 42` to receive events after the transcript snapshot. The app loads `/messages` on every
session open, not only after a restart.

### 2.6 Rename

`name` is trimmed; empty is `400 name is empty`. If Pi is running, the gateway also sends
`set_session_name` and returns `502` if Pi rejects it. The record is persisted after a successful rename.

### 2.7 Session removal

`DELETE /v1/sessions/{id}` removes the gateway record, rewrites the state file, drops Pi's stdin, and
returns `204`. The Pi child exits when its stdin closes; the gateway sends no signal and does not wait.
The Pi session file stays on the host. There is no purge route: see §6.

## 3. SSE

```
GET /v1/sessions/{id}/events
Accept: text/event-stream
Last-Event-ID: 42
```

- `id:` is a per-session `u64`, starting at 1.
- `event:` is Pi's `type` field from the JSON line (for example `agent_start`, `agent_settled`, a
  message or tool event), or `raw` when the line is not JSON.
- `data:` is the raw JSON line Pi wrote, unmodified.
- `gateway_exit` is gateway-generated with `{"type":"gateway_exit","reason":"pi exited"}` when the Pi
  child exits.
- A keep-alive comment is sent every 15 seconds.

Replay:

- Default `replay=true` uses `Last-Event-ID`; a missing or unparseable header means 0.
- A cursor greater than the current id resets to 0 (replay the whole buffer).
- `?replay=false` starts at the current id; only new events are sent.
- The buffer is an in-memory ring of 4,096 events per session. Older events are gone; `/messages` is the
  durable path.
- The broadcast channel holds 2,048 events; a subscriber that falls behind skips missed events silently
  and continues with the next one.

### 3.1 `eventId` and epochs

There is no `epoch` field and no epoch route. An epoch is one gateway process lifetime:

- ids increase monotonically per session for the life of the gateway process, including across a Pi
  restart (the event buffer is cleared, the counter is not);
- a gateway process restart restores the session records with the counter at 0, so the next event is id
  1 again;
- a `404` means the session record is gone.

A client that sees a lower id than its cursor, or a `404`, must treat the stream as a new epoch: reload
`/messages`, replace the transcript, and stream after the returned `eventId`. Never concatenate events
from two epochs.

## 4. Busy and streaming

There is no `busy` field on the wire. Turn state is derived from SSE:

- `agent_start` sets streaming; `agent_settled` clears it;
- the gateway uses the same flag to decide `streamingBehavior: "steer"`;
- `gateway_exit` means the Pi child is gone; `status` becomes `stopped`.

The client must treat `status: running` as "process alive", not "turn in progress". Send is allowed
while busy; the gateway marks a prompt sent during a turn as a steer.

## 5. Errors

Error bodies are plain text, not JSON. Empty bodies are possible.

| Status | When |
|---|---|
| `400` | Invalid `cwd`, `cwd` not a directory, empty `message` with no image, empty `name`, malformed JSON fields. |
| `401` | Token configured and missing or wrong. |
| `404` | Session id not found, unknown path. |
| `405` | Known path, wrong method. |
| `413` | Request body over 12 MiB. |
| `422` | JSON body parsed but a required field has the wrong type. |
| `500` | Gateway could not resolve its own current directory during create. |
| `502` | Pi failed to start, stdin is closed, a Pi command failed, or a Pi command timed out (10–15 s). |

The app surfaces `HTTP <code>: <body>`; do not parse the body for branching.

## 6. Retention

- The gateway writes no prompt, image, or transcript to its own disk path. Pi owns transcripts.
- `sessions.json` (`HELLDYE_STATE_FILE` or `~/.local/share/helldye/sessions.json`) stores only
  `id`, `name`, `cwd`, and `created`. It is rewritten in full on create, rename, and delete; the write
  is not atomic.
- The event ring is memory only and dies with the process.
- Pi persists each session under `--session-id` in Pi's session storage on the host. Prompts and images
  are part of that transcript.
- **Remove from list** is the phone action: it removes the gateway record only.
- **Purge on host** is manual: stop the gateway and delete the Pi session file for that id. The gateway
  has no purge route.

### 6.1 Ephemeral sessions

Not implemented. Every session is started as `pi --mode rpc --session-id <id> --name <name> --tools
<list>`, so Pi persists every prompt, including a screenshot. The gateway has no `--no-session` mode and
no `ephemeral` field on create. Phase 3 adds an ephemeral create path for screenshot queries; until then
a screenshot sent through this gateway lands in the selected session's transcript.

## 7. Workspace and tools

- `cwd` is the only workspace control. It is canonicalized per create and fixed for the session's life.
  There is no allowlist, no per-session worktree, and no path check beyond "is a directory".
- The tool allowlist comes from `--tools` or `HELLDYE_TOOLS`; the default is `read,grep,find,ls`.
  `*` enables every tool, including extension tools. It is applied when the Pi child starts, so a
  change requires restarting the gateway or the session.
- The gateway sets `current_dir` to the session `cwd` and passes no extra environment allowlist. Pi
  inherits the gateway environment today. Phase 1 changes that.

## 8. One writer

- The gateway is the only writer to a live Pi session. It spawns one child per session with
  `--session-id` and owns its stdin and stdout.
- Do not run a second writer against the same session (for example the Pi TUI) while the gateway is
  live. Stop the gateway first.
- Any number of SSE subscribers may read one session; they all receive the same events.
- HTTP clients share the gateway's stdin. Two clients prompting the same session are serialized by the
  gateway; "one writer" constrains processes, not phone connections.
- A client detach does not abort a turn. A gateway restart loses the in-memory ring; reattach with
  `/messages` + `eventId`. The gateway does not guarantee that a Pi child survives its own restart.
