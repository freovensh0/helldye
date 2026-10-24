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

The default Pi binary is `pi-bolt` from `PATH`. The default tool allowlist is read-only. Each
session starts with:

### Termux

The `gateway` workflow publishes `helldye-gateway-termux-arm64`. Download the artifact from the
latest successful run, unpack it, then:

```sh
chmod +x helldye-gateway
HELLDYE_TOKEN=change-me ./helldye-gateway --listen 127.0.0.1:8787 --pi "$(command -v pi-bolt)"
```

The Termux artifact is built against Android NDK API 29, so it does not need Termux's Rust package.
If `cargo build` in Termux fails with missing `std` rlibs, use this artifact instead of rebuilding.


```sh
pi-bolt --mode rpc --no-session --tools read,grep,find,ls
```

Screen text is untrusted. Keep the read-only allowlist until you explicitly want the assistant to
edit files or run commands.

`--no-session` keeps Pi's session state inside the gateway process. The gateway is the owner; do not
run a second writer against the same session.

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

The app creates a session on the first prompt and reuses it until the app process or gateway dies.

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
| `GET` | `/v1/sessions/{id}/events` | SSE stream; supports `Last-Event-ID`. |
| `POST` | `/v1/sessions/{id}/prompt` | Send `message`, optional `context`, optional `images`. |
| `POST` | `/v1/sessions/{id}/abort` | Abort the active turn. |
| `GET` | `/v1/sessions/{id}/models` | Models configured in Pi. |
| `GET` | `/v1/sessions/{id}/commands` | Prompt templates, skills, and extension commands. |
| `GET` | `/v1/sessions/{id}/state` | Current model, thinking level, and session state. |
| `POST` | `/v1/sessions/{id}/model` | Switch model (`provider`, `modelId`). |

`context` is prepended to the prompt as-is. The Android client wraps screen text in
`<screen_context>` and text files in `<file name="...">` before sending it.

## Limits

- Event replay is an in-memory ring of 4,096 events per session. A restarted gateway loses sessions
  and replay history.
- Prompts are accepted asynchronously; streamed output and command responses arrive on SSE.
- Pi-Bolt's stderr is logged to the gateway's stderr. Do not put screen text in prompts that you do
  not want visible in transport or Pi logs.
