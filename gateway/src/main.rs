use std::{
    collections::{HashMap, VecDeque},
    convert::Infallible,
    env,
    path::PathBuf,
    process::Stdio,
    sync::{
        Arc,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
};

use axum::{
    Json, Router,
    extract::{DefaultBodyLimit, Path, State},
    http::{HeaderMap, StatusCode},
    middleware::{self, Next},
    response::{
        IntoResponse, Response,
        sse::{Event as SseEvent, KeepAlive, Sse},
    },
    routing::{get, post},
};
use futures::Stream;
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use tokio::{
    io::{AsyncBufReadExt, AsyncWriteExt, BufReader},
    process::{ChildStdin, Command},
    sync::{Mutex, RwLock, broadcast, mpsc, oneshot},
    time::Duration,
};
use tokio_stream::{StreamExt, wrappers::ReceiverStream};
use uuid::Uuid;

const EVENT_BUFFER: usize = 4_096;
const BROADCAST_CAPACITY: usize = 2_048;

#[derive(Clone)]
struct AppState {
    pi_bin: String,
    tools: String,
    token: Option<String>,
    sessions: Arc<RwLock<HashMap<String, Arc<Session>>>>,
}

struct Session {
    id: String,
    name: RwLock<String>,
    cwd: String,
    stdin: Mutex<Option<ChildStdin>>,
    seq: AtomicU64,
    streaming: AtomicBool,
    events: Mutex<VecDeque<WireEvent>>,
    tx: broadcast::Sender<WireEvent>,
    pending: Mutex<HashMap<String, oneshot::Sender<Value>>>,
    status: Mutex<String>,
}

#[derive(Clone)]
struct WireEvent {
    id: u64,
    kind: String,
    data: String,
}

#[derive(Serialize)]
struct SessionInfo {
    id: String,
    name: String,
    cwd: String,
    status: String,
}

#[derive(Deserialize)]
struct CreateSession {
    #[serde(default)]
    cwd: Option<String>,
    #[serde(default)]
    name: Option<String>,
}

#[derive(Deserialize)]
struct PromptBody {
    message: String,
    #[serde(default)]
    context: Option<String>,
    #[serde(default)]
    images: Vec<ImageBody>,
}

#[derive(Deserialize)]
struct ImageBody {
    data: String,
    #[serde(rename = "mimeType")]
    mime_type: String,
}

#[derive(Deserialize)]
struct SetModel {
    provider: String,
    #[serde(rename = "modelId")]
    model_id: String,
}

#[derive(Deserialize)]
struct RenameSession {
    name: String,
}

#[derive(Deserialize)]
struct SetThinking {
    level: String,
}

#[derive(Deserialize)]
struct ExtensionUiResponse {
    id: String,
    #[serde(default)]
    value: Option<String>,
    #[serde(default)]
    confirmed: Option<bool>,
    #[serde(default)]
    cancelled: Option<bool>,
}

impl Session {
    async fn publish(&self, kind: String, data: String) {
        let id = self.seq.fetch_add(1, Ordering::SeqCst) + 1;
        let event = WireEvent { id, kind, data };
        {
            let mut events = self.events.lock().await;
            if events.len() >= EVENT_BUFFER {
                events.pop_front();
            }
            events.push_back(event.clone());
        }
        let _ = self.tx.send(event);
    }

    async fn request(&self, mut command: Value, timeout: Duration) -> Result<Value, String> {
        let id = Uuid::new_v4().to_string();
        command["id"] = json!(id);
        let (tx, rx) = oneshot::channel();
        self.pending.lock().await.insert(id.clone(), tx);
        if let Err(error) = self.send(command).await {
            self.pending.lock().await.remove(&id);
            return Err(error);
        }
        match tokio::time::timeout(timeout, rx).await {
            Ok(Ok(response)) => Ok(response),
            Ok(Err(_)) => Err("Pi response channel closed".to_string()),
            Err(_) => {
                self.pending.lock().await.remove(&id);
                Err("Pi command timed out".to_string())
            }
        }
    }

    async fn send(&self, command: Value) -> Result<(), String> {
        let mut guard = self.stdin.lock().await;
        let stdin = guard
            .as_mut()
            .ok_or_else(|| "session is closed".to_string())?;
        let mut line = serde_json::to_vec(&command).map_err(|error| error.to_string())?;
        line.push(b'\n');
        stdin
            .write_all(&line)
            .await
            .map_err(|error| error.to_string())
    }

    async fn info(&self) -> SessionInfo {
        SessionInfo {
            id: self.id.clone(),
            name: self.name.read().await.clone(),
            cwd: self.cwd.clone(),
            status: self.status.lock().await.clone(),
        }
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut listen = "127.0.0.1:8787".to_string();
    let mut pi_bin = env::var("HELLDYE_PI_BIN").unwrap_or_else(|_| "pi-bolt".to_string());
    let mut tools = env::var("HELLDYE_TOOLS").unwrap_or_else(|_| "read,grep,find,ls".to_string());
    let mut token = env::var("HELLDYE_TOKEN")
        .ok()
        .filter(|value| !value.is_empty());

    let mut args = env::args().skip(1);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--listen" => listen = args.next().ok_or("--listen needs a value")?,
            "--pi" => pi_bin = args.next().ok_or("--pi needs a value")?,
            "--tools" => tools = args.next().ok_or("--tools needs a value")?,
            "--token" => token = args.next().filter(|value| !value.is_empty()),
            "--help" | "-h" => {
                println!(
                    "Usage: helldye-gateway [--listen ADDR] [--pi PATH] [--tools LIST] [--token TOKEN]\n\
                     Env: HELLDYE_PI_BIN, HELLDYE_TOOLS, HELLDYE_TOKEN"
                );
                return Ok(());
            }
            other => return Err(format!("unknown argument: {other}").into()),
        }
    }

    let state = AppState {
        pi_bin,
        tools,
        token,
        sessions: Arc::new(RwLock::new(HashMap::new())),
    };

    let app = Router::new()
        .route("/health", get(health))
        .route("/v1/info", get(info))
        .route("/v1/sessions", get(list_sessions).post(create_session))
        .route("/v1/sessions/{id}", get(get_session).delete(delete_session))
        .route("/v1/sessions/{id}/events", get(session_events))
        .route("/v1/sessions/{id}/prompt", post(prompt))
        .route("/v1/sessions/{id}/abort", post(abort))
        .route("/v1/sessions/{id}/compact", post(compact))
        .route("/v1/sessions/{id}/name", post(rename_session))
        .route("/v1/sessions/{id}/models", get(session_models))
        .route("/v1/sessions/{id}/commands", get(session_commands))
        .route("/v1/sessions/{id}/state", get(session_state))
        .route("/v1/sessions/{id}/model", post(set_session_model))
        .route("/v1/sessions/{id}/stats", get(session_stats))
        .route(
            "/v1/sessions/{id}/thinking",
            get(session_thinking).post(set_session_thinking),
        )
        .route(
            "/v1/sessions/{id}/extension_ui_response",
            post(extension_ui_response),
        )
        .with_state(state.clone())
        .layer(DefaultBodyLimit::max(12 * 1024 * 1024))
        .layer(middleware::from_fn_with_state(state, require_auth));

    let listener = tokio::net::TcpListener::bind(&listen).await?;
    eprintln!("helldye-gateway listening on http://{listen}");
    axum::serve(listener, app).await?;
    Ok(())
}

async fn require_auth(
    State(state): State<AppState>,
    headers: HeaderMap,
    request: axum::extract::Request,
    next: Next,
) -> Result<Response, StatusCode> {
    if request.uri().path() == "/health" {
        return Ok(next.run(request).await);
    }
    if let Some(expected) = &state.token {
        let provided = headers
            .get(axum::http::header::AUTHORIZATION)
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.strip_prefix("Bearer "));
        if provided != Some(expected.as_str()) {
            return Err(StatusCode::UNAUTHORIZED);
        }
    }
    Ok(next.run(request).await)
}

async fn health() -> &'static str {
    "ok"
}

async fn info(State(state): State<AppState>) -> impl IntoResponse {
    let sessions = state.sessions.read().await.len();
    Json(json!({
        "name": "helldye-gateway",
        "version": env!("CARGO_PKG_VERSION"),
        "piBin": state.pi_bin,
        "sessions": sessions,
    }))
}

async fn list_sessions(State(state): State<AppState>) -> impl IntoResponse {
    let sessions = state.sessions.read().await;
    let mut values = Vec::with_capacity(sessions.len());
    for session in sessions.values() {
        values.push(session.info().await);
    }
    Json(values)
}

async fn create_session(
    State(state): State<AppState>,
    Json(body): Json<CreateSession>,
) -> Result<Json<SessionInfo>, (StatusCode, String)> {
    let cwd = body
        .cwd
        .map(PathBuf::from)
        .unwrap_or(env::current_dir().map_err(internal)?);
    let cwd = cwd
        .canonicalize()
        .map_err(|error| (StatusCode::BAD_REQUEST, format!("invalid cwd: {error}")))?;
    if !cwd.is_dir() {
        return Err((StatusCode::BAD_REQUEST, "cwd is not a directory".into()));
    }

    let id = Uuid::new_v4().to_string();
    let name = body.name.unwrap_or_else(|| id[..8].to_string());
    let session = spawn_session(&state.pi_bin, &state.tools, id.clone(), name, cwd)
        .await
        .map_err(|error| (StatusCode::BAD_GATEWAY, error))?;
    let info = session.info().await;
    state.sessions.write().await.insert(id, session);
    Ok(Json(info))
}

async fn get_session(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<SessionInfo>, StatusCode> {
    let session = lookup(&state, &id).await?;
    Ok(Json(session.info().await))
}

async fn delete_session(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<StatusCode, StatusCode> {
    let session = state
        .sessions
        .write()
        .await
        .remove(&id)
        .ok_or(StatusCode::NOT_FOUND)?;
    session.stdin.lock().await.take();
    Ok(StatusCode::NO_CONTENT)
}

async fn prompt(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(body): Json<PromptBody>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup(&state, &id)
        .await
        .map_err(|status| (status, "not found".into()))?;
    if body.message.trim().is_empty() && body.images.is_empty() {
        return Err((StatusCode::BAD_REQUEST, "message is empty".into()));
    }

    let message = merge_context(body.message, body.context);

    let mut command = json!({
        "id": Uuid::new_v4().to_string(),
        "type": "prompt",
        "message": message,
    });
    if !body.images.is_empty() {
        command["images"] = json!(
            body.images
                .into_iter()
                .map(|image| json!({
                    "type": "image",
                    "data": image.data,
                    "mimeType": image.mime_type,
                }))
                .collect::<Vec<_>>()
        );
    }
    if session.streaming.load(Ordering::SeqCst) {
        command["streamingBehavior"] = json!("steer");
    }

    session
        .send(command)
        .await
        .map_err(|error| (StatusCode::BAD_GATEWAY, error))?;
    session.streaming.store(true, Ordering::SeqCst);
    Ok(Json(json!({ "accepted": true })))
}

async fn abort(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, StatusCode> {
    let session = lookup(&state, &id).await?;
    session
        .send(json!({ "type": "abort" }))
        .await
        .map_err(|_| StatusCode::BAD_GATEWAY)?;
    Ok(Json(json!({ "accepted": true })))
}

async fn compact(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, StatusCode> {
    let session = lookup(&state, &id).await?;
    session
        .send(json!({ "type": "compact" }))
        .await
        .map_err(|_| StatusCode::BAD_GATEWAY)?;
    Ok(Json(json!({ "accepted": true })))
}

async fn rename_session(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(body): Json<RenameSession>,
) -> Result<Json<SessionInfo>, (StatusCode, String)> {
    let name = body.name.trim().to_string();
    if name.is_empty() {
        return Err((StatusCode::BAD_REQUEST, "name is empty".into()));
    }
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(
            json!({ "type": "set_session_name", "name": name }),
            Duration::from_secs(10),
        )
        .await
        .map_err(bad_gateway)?;
    command_data(response)?;
    *session.name.write().await = name;
    Ok(Json(session.info().await))
}

async fn session_models(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(
            json!({ "type": "get_available_models" }),
            Duration::from_secs(15),
        )
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn session_commands(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(json!({ "type": "get_commands" }), Duration::from_secs(10))
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn session_state(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(json!({ "type": "get_state" }), Duration::from_secs(10))
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn set_session_model(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(body): Json<SetModel>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(
            json!({
                "type": "set_model",
                "provider": body.provider,
                "modelId": body.model_id,
            }),
            Duration::from_secs(10),
        )
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn session_stats(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(
            json!({ "type": "get_session_stats" }),
            Duration::from_secs(10),
        )
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn session_thinking(
    State(state): State<AppState>,
    Path(id): Path<String>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(
            json!({ "type": "get_available_thinking_levels" }),
            Duration::from_secs(10),
        )
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn set_session_thinking(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(body): Json<SetThinking>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let response = session
        .request(
            json!({ "type": "set_thinking_level", "level": body.level }),
            Duration::from_secs(10),
        )
        .await
        .map_err(bad_gateway)?;
    command_data(response).map(Json)
}

async fn extension_ui_response(
    State(state): State<AppState>,
    Path(id): Path<String>,
    Json(body): Json<ExtensionUiResponse>,
) -> Result<Json<Value>, (StatusCode, String)> {
    let session = lookup_owned(&state, &id).await?;
    let mut response = json!({ "type": "extension_ui_response", "id": body.id });
    if let Some(value) = body.value {
        response["value"] = json!(value);
    }
    if let Some(confirmed) = body.confirmed {
        response["confirmed"] = json!(confirmed);
    }
    if body.cancelled == Some(true) {
        response["cancelled"] = json!(true);
    }
    session.send(response).await.map_err(bad_gateway)?;
    Ok(Json(json!({ "accepted": true })))
}

async fn session_events(
    State(state): State<AppState>,
    Path(id): Path<String>,
    headers: HeaderMap,
) -> Result<Sse<impl Stream<Item = Result<SseEvent, Infallible>>>, StatusCode> {
    let session = lookup(&state, &id).await?;
    let last = headers
        .get("last-event-id")
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.parse::<u64>().ok())
        .unwrap_or(0);

    let (tx, rx) = mpsc::channel(256);
    tokio::spawn(async move {
        let mut subscriber = session.tx.subscribe();
        let mut next = last + 1;
        let backlog = session.events.lock().await.clone();
        for event in backlog {
            if event.id >= next && tx.send(event.clone()).await.is_err() {
                return;
            }
            if event.id >= next {
                next = event.id + 1;
            }
        }

        loop {
            match subscriber.recv().await {
                Ok(event) => {
                    if event.id < next {
                        continue;
                    }
                    if tx.send(event.clone()).await.is_err() {
                        break;
                    }
                    next = event.id + 1;
                }
                Err(broadcast::error::RecvError::Lagged(_)) => continue,
                Err(broadcast::error::RecvError::Closed) => break,
            }
        }
    });

    let stream = ReceiverStream::new(rx).map(|event| {
        Ok(SseEvent::default()
            .id(event.id.to_string())
            .event(event.kind)
            .data(event.data))
    });
    Ok(Sse::new(stream).keep_alive(KeepAlive::new().interval(Duration::from_secs(15))))
}

async fn lookup_owned(state: &AppState, id: &str) -> Result<Arc<Session>, (StatusCode, String)> {
    lookup(state, id)
        .await
        .map_err(|status| (status, "session not found".to_string()))
}

fn command_data(response: Value) -> Result<Value, (StatusCode, String)> {
    if response.get("success").and_then(Value::as_bool) == Some(true) {
        Ok(response.get("data").cloned().unwrap_or(Value::Null))
    } else {
        Err((
            StatusCode::BAD_GATEWAY,
            response
                .get("error")
                .and_then(Value::as_str)
                .unwrap_or("Pi command failed")
                .to_string(),
        ))
    }
}

fn bad_gateway(error: String) -> (StatusCode, String) {
    (StatusCode::BAD_GATEWAY, error)
}

async fn lookup(state: &AppState, id: &str) -> Result<Arc<Session>, StatusCode> {
    state
        .sessions
        .read()
        .await
        .get(id)
        .cloned()
        .ok_or(StatusCode::NOT_FOUND)
}

async fn spawn_session(
    pi_bin: &str,
    tools: &str,
    id: String,
    name: String,
    cwd: PathBuf,
) -> Result<Arc<Session>, String> {
    let mut child = Command::new(pi_bin)
        .arg("--mode")
        .arg("rpc")
        .arg("--session-id")
        .arg(&id)
        .arg("--name")
        .arg(&name)
        .arg("--tools")
        .arg(tools)
        .current_dir(&cwd)
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|error| format!("failed to start {pi_bin}: {error}"))?;

    let stdin = child.stdin.take().ok_or("pi stdin is unavailable")?;
    let stdout = child.stdout.take().ok_or("pi stdout is unavailable")?;
    let stderr = child.stderr.take().ok_or("pi stderr is unavailable")?;
    let (tx, _) = broadcast::channel(BROADCAST_CAPACITY);
    let session = Arc::new(Session {
        id,
        name: RwLock::new(name),
        cwd: cwd.to_string_lossy().into_owned(),
        stdin: Mutex::new(Some(stdin)),
        seq: AtomicU64::new(0),
        streaming: AtomicBool::new(false),
        events: Mutex::new(VecDeque::new()),
        tx,
        pending: Mutex::new(HashMap::new()),
        status: Mutex::new("running".into()),
    });

    tokio::spawn(read_stdout(session.clone(), stdout));
    tokio::spawn(read_stderr(stderr));
    let watcher = session.clone();
    tokio::spawn(async move {
        let _ = child.wait().await;
        *watcher.status.lock().await = "exited".into();
        watcher
            .publish(
                "gateway_exit".into(),
                json!({ "type": "gateway_exit", "reason": "pi exited" }).to_string(),
            )
            .await;
    });
    Ok(session)
}

async fn read_stdout(session: Arc<Session>, stdout: tokio::process::ChildStdout) {
    let mut lines = BufReader::new(stdout).lines();
    while let Ok(Some(line)) = lines.next_line().await {
        let parsed = serde_json::from_str::<Value>(&line).ok();
        let kind = parsed
            .as_ref()
            .and_then(|value| value.get("type"))
            .and_then(Value::as_str)
            .unwrap_or("raw")
            .to_string();

        if let Some(response) = parsed.as_ref() {
            if response.get("type").and_then(Value::as_str) == Some("response") {
                if let Some(id) = response.get("id").and_then(Value::as_str) {
                    if let Some(tx) = session.pending.lock().await.remove(id) {
                        let _ = tx.send(response.clone());
                    }
                }
            }
        }

        match kind.as_str() {
            "agent_start" => session.streaming.store(true, Ordering::SeqCst),
            "agent_settled" => session.streaming.store(false, Ordering::SeqCst),
            _ => {}
        }
        session.publish(kind, line).await;
    }
}

async fn read_stderr(mut stderr: tokio::process::ChildStderr) {
    let mut lines = BufReader::new(&mut stderr).lines();
    while let Ok(Some(line)) = lines.next_line().await {
        eprintln!("pi: {line}");
    }
}

fn merge_context(message: String, context: Option<String>) -> String {
    match context.filter(|value| !value.trim().is_empty()) {
        Some(context) => format!("{}\n\n{}", context.trim(), message),
        None => message,
    }
}

fn internal(error: std::io::Error) -> (StatusCode, String) {
    (StatusCode::INTERNAL_SERVER_ERROR, error.to_string())
}

#[cfg(test)]
mod tests {
    use super::merge_context;

    #[test]
    fn context_is_prepended_once() {
        assert_eq!(
            merge_context(
                "question".into(),
                Some("  <screen_context>screen</screen_context>  ".into())
            ),
            "<screen_context>screen</screen_context>\n\nquestion"
        );
    }

    #[test]
    fn blank_context_leaves_prompt_unchanged() {
        assert_eq!(
            merge_context("question".into(), Some("  ".into())),
            "question"
        );
        assert_eq!(merge_context("question".into(), None), "question");
    }
}
