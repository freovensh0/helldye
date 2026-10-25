# helldye-gateway

Small HTTP/SSE bridge between HELLDYE and one or more `pi-bolt --mode rpc` processes.

The gateway owns each Pi-Bolt process and session. The phone can disconnect, switch away, or be
killed without aborting the work; it reconnects with `Last-Event-ID` and receives the buffered
events. Screen text is passed through to the prompt and is not written to disk.

## Run

```sh
cargo build --release
HELLDYE_TOKEN=change-me ./target/release/helldye-gateway \
  --listen 127.0.0.1:8787 \
  --pi /path/to/pi-bolt \
  --tools read,grep,find,ls
```

Environment equivalents: `HELLDYE_PI_BIN`, `HELLDYE_TOOLS`, `HELLDYE_TOKEN`.

The default Pi binary is `pi-bolt` from `PATH`. The default tool allowlist is read-only
(`read,grep,find,ls`); Pi's own default is `read,bash,edit,write`. Override it with `--tools` or
`HELLDYE_TOOLS`, or with `hellgate tools ...` from the Termux install.

### Termux

The `gateway` workflow publishes `helldye-gateway-termux-arm64`. Download the artifact from the
latest successful run, unpack it, then:

```sh
chmod +x helldye-gateway
HELLDYE_TOKEN=change-me ./helldye-gateway --listen 127.0.0.1:8787 --pi "$(command -v pi-bolt)"
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

Use the printed `https://<host>.<tailnet>.ts.net` URL as the Android gateway URL. Keep the bearer
token enabled unless the host is already restricted to your tailnet.

## Android pairing

Open HELLDYE, choose **CONFIGURE** next to **Pi gateway**, and set:

- **Gateway URL**: the Tailscale Serve URL or `http://127.0.0.1:8787` when using a forwarded port.
- **Token**: the same token passed to the gateway.
- **Workspace**: optional working directory for Pi-Bolt; defaults to the gateway process directory.

The app creates a session on the first prompt and lists persisted sessions in the session picker. Pi
writes each session to its own Pi session file (`--session-id`), so a stopped gateway can resume it.

## API

All `/v1` routes require `Authorization: Bearer <token>` when a token is configured.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Unauthenticated liveness check. |
| `GET` | `/v1/info` | Gateway and Pi binary info. |
| `GET` | `/v1/sessions` | Running sessions. |
| `POST` | `/v1/sessions` | Create a session (`cwd`, `name`). |
| `GET` | `/v1/sessions/{id}` | Session status. |
| `DELETE` | `/v1/sessions/{id}` | Close stdin and let Pi exit. |
| `GET` | `/v1/sessions/{id}/events` | SSE stream; supports `Last-Event-ID` and `?replay=false`. |
| `GET` | `/v1/sessions/{id}/messages` | Full Pi transcript plus the `eventId` it reflects. |
| `POST` | `/v1/sessions/{id}/prompt` | Send `message`, optional `context`, optional `images`. |
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

`context` is prepended to the prompt as-is. The Android client wraps text files in
`<file name="...">` before sending them; screen text is not read.

## Limits

- Event replay is an in-memory ring of 4,096 events per session. The session list is persisted to
  `~/.local/share/helldye/sessions.json` (`HELLDYE_STATE_FILE` overrides it), and Pi keeps each
  session's transcript in its own session file; restarting the gateway resumes it on the next request.
- A session is started lazily with `pi --session-id <id>`. The app always loads `/messages` first
  (whenever the session is opened, not only after a gateway restart), restores the transcript, and
  then opens the event stream from the returned `eventId`, so old history never depends on the
  in-memory replay ring.
- Prompts are accepted asynchronously; streamed output and command responses arrive on SSE.
- Pi-Bolt's stderr is logged to the gateway's stderr. Do not put screen text in prompts that you do
  not want visible in transport or Pi logs.
