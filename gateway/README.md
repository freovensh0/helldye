# helldye-gateway

Small HTTP/SSE bridge between HELLDYE and one or more `pi-bolt --mode rpc` processes.

The gateway owns each Pi-Bolt process and session. The phone can disconnect, switch away, or be
killed without aborting the work; it reconnects with `Last-Event-ID` and receives the buffered
events. Screen context is an image: the app sends a captured screenshot with the prompt, and the
gateway never reads screen text or writes the image to disk.

## Run

```sh
cargo build --release
HELLDYE_TOKEN=change-me \
HELLDYE_WORKSPACES=/home/you/projects \
./target/release/helldye-gateway \
  --listen 127.0.0.1:8787 \
  --pi /path/to/pi-bolt \
  --tools read,grep,find,ls
```

Environment: `HELLDYE_PI_BIN`, `HELLDYE_TOOLS`, `HELLDYE_TOKEN` or `HELLDYE_TOKEN_FILE`,
`HELLDYE_WORKSPACES`, `HELLDYE_DEBUG`, `HELLDYE_STATE_FILE`, `HELLDYE_IDLE_SECS` (default 1800, `0`
disables the idle reaper), `HELLDYE_PI_SESSION_DIR` (explicit host import), `HELLDYE_ACTION_TOOLS`
(the write tool set an explicit per-session "allow edits" enables).

A bearer token is **required for every start, including loopback**. The gateway exits instead of
serving `/v1` without one. Use `HELLDYE_TOKEN` or point `HELLDYE_TOKEN_FILE` at a file that is mode
`0600` on Unix; any other mode is rejected. There is no `--token` flag, because a flag would expose
the token in the process list. The token file must live outside every workspace root; startup is
rejected if it is inside one.

`HELLDYE_WORKSPACES` is a colon-separated allowlist of absolute directories and is also required.
A session `cwd` must be an absolute path inside one of those roots; `..`, relative paths, and
symlink escapes are rejected. When a create request omits `cwd`, the first root is used, never the
gateway process directory.

The default Pi binary is `pi-bolt` from `PATH`. The default tool allowlist is read-only
(`read,grep,find,ls`); Pi's own default is `read,bash,edit,write`. Override it with `--tools` or
`HELLDYE_TOOLS`, or with `hellgate tools ...` from the Termux install.

### Isolation

Pi is spawned with an empty environment plus an explicit allowlist: `PATH`, `HOME`, locale and
timezone values, XDG directories, and provider/API keys. The gateway token, CI variables, and every
other inherited value are dropped. Pi also runs as the gateway's user and `cwd` is only a starting
directory, so the process can still read any file that user can read. **Same-user isolation is not
a sandbox.** For strong isolation, run Pi (and the gateway) as a separate host user, container, or
VM with only the workspace mounted.

### Termux

The `gateway` workflow publishes `helldye-gateway-termux-arm64`. Download the artifact from the
latest successful run, unpack it, then:

```sh
chmod +x helldye-gateway
HELLDYE_TOKEN_FILE="$HOME/.config/helldye/token" \
HELLDYE_WORKSPACES="$HOME/projects" \
./helldye-gateway --listen 127.0.0.1:8787 --pi "$(command -v pi-bolt)"
```

The Termux artifact is built against Android NDK API 29, so it does not need Termux's Rust package.
If `cargo build` in Termux fails with missing `std` rlibs, use this artifact instead of rebuilding.

The installer manages this with `hellgate`. `hellgate tools all` enables every tool, including tools
registered by extensions (`*`); `hellgate tools read-only` restores the safe subset, and
`hellgate tools select` opens a numbered picker. The list is stored in `~/helldye-gateway/tools`;
`hellgate tools reset` removes it. The allowlist applies when a Pi process starts, so restart the
gateway or resume the session afterwards.

The gateway owns the Pi process for each session and starts it with `--session-id <id>`; Pi persists
the transcript. Do not run a second writer against a live session (for example the TUI); stop the
gateway first.

## Tailscale

Bind to localhost and expose it through Tailscale Serve:

```sh
tailscale serve --bg 8787
```

Use the printed `https://<host>.<tailnet>.ts.net` URL as the Android gateway URL. The bearer token is
required even on loopback and over Tailscale; generate a long random value and keep it out of shell
history and process arguments.

## Android pairing

Open HELLDYE, choose **CONFIGURE** next to **Pi gateway**, and set:

- **Gateway URL**: the Tailscale Serve URL or `http://127.0.0.1:8787` when using a forwarded port.
  Release builds accept HTTPS only; a debug build may use cleartext for loopback only.
- **Token**: the same token passed to the gateway; it is required.
- **Workspace**: an absolute directory inside the gateway's `HELLDYE_WORKSPACES` allowlist.
  Leaving it empty uses the first allowed root.

The app creates a session on the first prompt and lists persisted sessions in the session picker. Pi
writes each session to its own Pi session file (`--session-id`), so a stopped gateway can resume it.
Session names are neutral (`Session 4F2A`); the gateway never derives a name from prompt text. A
rename in the app is an explicit user action and is the only way a custom name is persisted.

A screen query creates an `ephemeral` session instead. It is spawned as
`pi --mode rpc --no-session --tools read,grep,find,ls`, never appears in the session list or
`sessions.json`, and is deleted on `agent_settled`, on `DELETE`, or after 120 seconds without an
event. The image and answer are not persisted by the gateway.

## Session removal and retention

Pi owns every transcript. The gateway keeps only a session record (`sessions.json`: identity, name,
cwd, timestamps, whether imported, and the last-known model/thinking) and an in-memory event ring; it
does not write prompts or images to its own storage.

- **Remove from list** (`DELETE /v1/sessions/{id}`): removes the gateway record, persists the list, and
  closes Pi's stdin so the process exits. This is the only removal the phone can do. It does not delete
  the Pi transcript.
- **Purge on host** (manual): stop the gateway and delete the Pi session file for that id from Pi's
  session storage. There is no gateway route and no phone action for this; the app cannot reach the
  host's Pi session files.

The phone confirmation is two-step: the session row reveals `DELETE` and `KEEP`. It does not mention
retention today; Phase 4 (R-04) renames the action to remove-from-list and states that the transcript
stays on the host.

## API

All `/v1` routes require `Authorization: Bearer <token>`, including loopback. `/health` is the only
unauthenticated route.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Unauthenticated liveness check. |
| `GET` | `/v1/info` | Gateway version and capability flags. No paths, env, or cwd. |
| `GET` | `/v1/sessions` | Persisted sessions, newest first. |
| `POST` | `/v1/sessions` | Create a session (`cwd`, optional `ephemeral`; the name is generated). |
| `GET` | `/v1/sessions/{id}` | Session status. |
| `DELETE` | `/v1/sessions/{id}` | Remove the gateway record; Pi exits when stdin closes. |
| `GET` | `/v1/sessions/{id}/events` | SSE stream; supports `Last-Event-ID` and `?replay=false`. |
| `GET` | `/v1/sessions/{id}/messages` | Full Pi transcript plus the `eventId` it reflects. |
| `POST` | `/v1/sessions/{id}/prompt` | Send `message`, optional structured `context`, optional `images`. |
| `POST` | `/v1/sessions/{id}/abort` | Abort the active turn. |
| `GET` | `/v1/sessions/{id}/models` | Models configured in Pi. |
| `GET` | `/v1/sessions/{id}/commands` | Prompt templates, skills, and extension commands. |
| `GET` | `/v1/sessions/{id}/state` | Current model, thinking level, and session state. |
| `POST` | `/v1/sessions/{id}/model` | Switch model (`provider`, `modelId`). |
| `GET` | `/v1/sessions/{id}/stats` | Token, cost, cache, and context usage. |
| `GET` | `/v1/sessions/{id}/thinking` | Thinking levels for the current model. |
| `POST` | `/v1/sessions/{id}/thinking` | Set the thinking level (`level`). |
| `POST` | `/v1/sessions/{id}/extension_ui_response` | Answer an extension dialog (`id`, `value`/`confirmed`/`cancelled`). |
| `POST` | `/v1/sessions/{id}/compact` | Compact the session context. |
| `POST` | `/v1/sessions/{id}/name` | Rename the session (`name`). |

The versioned contract — request shapes, SSE events, `eventId` and epoch semantics, busy state, error
codes, and retention — is [`docs/gateway-contract.md`](../docs/gateway-contract.md). It describes the
implemented gateway, including the gaps that later phases close.

`context` is structured data, not a pre-wrapped prompt: the app sends
`{"files":[{"name":"notes.md","content":"..."}]}`. The gateway serializes it as JSON inside a
clearly labeled `BEGIN CONTEXT DATA` / `END CONTEXT DATA` block, prepends a rule that the block is
untrusted data and never instructions, and only then appends the user's message. File content is
JSON-escaped, so `</file>`, fake system messages, and URLs cannot leave the data block. Screen
context is never text: it arrives only as an image in `images`, base64-encoded. The gateway validates
image MIME type, base64, dimensions, and size before forwarding the bytes.

## Limits

- Images: at most 4 per prompt, JPEG or PNG, at most 1,568 px on the long edge, at most 1.5 MB each
  and 6 MB total decoded. Larger payloads get `413`; an unsupported type or malformed image gets
  `415`.
- Event replay is an in-memory ring of 4,096 events per session. The session list is persisted to
  `~/.local/share/helldye/sessions.json` (`HELLDYE_STATE_FILE` overrides it), and Pi keeps each
  session's transcript in its own session file; restarting the gateway resumes it on the next request.
- A session is started lazily with `pi --session-id <id>`. Opening a stopped session does not spawn
  Pi: `/messages` returns `started=false` and `/events` returns one `not_started` event. The app loads
  `/messages` after a prompt, restores the transcript, and opens the event stream from the returned
  `eventId`, so old history never depends on the in-memory replay ring.
- The state directory holds a `gateway.lock` Unix socket; a second gateway that would own the same
  `sessions.json` refuses to start. A stale socket from a dead process is replaced.
- `POST /v1/sessions/import` explicitly scans `HELLDYE_PI_SESSION_DIR` for `*.jsonl` session files and
  adds neutral `ON HOST` records. It never starts Pi and never returns transcript content. Existing
  ids are skipped; remove-then-import is the only way to bring one back.
- Coding surface: `/diff` returns the workspace diff (capped) plus a per-file summary; `/file` peeks
  UTF-8 text inside the workspace; `/worktrees` lists and creates git worktrees with fixed argv and an
  allowlisted destination. There is no phone-side delete, editor, commit, or push.
- Action tools stay off per session. `POST /allow_edits` with `{enabled:true}` requires
  `HELLDYE_ACTION_TOOLS`, refuses a running session, applies only to that session, and is persisted.
  Enabling is audited on stderr without prompt content.
- Prompts are accepted asynchronously; streamed output and command responses arrive on SSE.
- Pi's stderr is drained but not logged by default. Set `HELLDYE_DEBUG=1` to log it; lines are
  bounded and any prompt-like, content, message, data, or image payload is dropped or redacted. The
  gateway never logs a prompt body, an image body, or the token. Pi persists each prompt and image in
  its session transcript on the host; do not send content you do not want retained there.
