#!/usr/bin/env bash
# Controls the HELLDYE gateway installed by scripts/install-termux.sh.
set -euo pipefail

DIR="${HELLGATE_HOME:-$HOME/helldye-gateway}"
PID_FILE="$DIR/gateway.pid"
LOG_FILE="$DIR/gateway.log"
TOKEN_FILE="$HOME/.config/helldye/token"
TOOLS_FILE="$DIR/tools"
ALL_TOOLS=(read bash edit write grep find ls)
READ_ONLY_TOOLS=(read grep find ls)
LISTEN="${HELLDYE_LISTEN:-127.0.0.1:8787}"

die() {
    echo "error: $*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Usage: hellgate <command>

  start     start the gateway in the background
  stop      stop the gateway
  restart   restart the gateway
  status    show whether it is running and accepts the token
  token     print the bearer token
  tools     show the Pi tool allowlist
  tools all           enable all tools, including extension tools
  tools read-only     read, grep, find, ls
  tools select        pick the allowlist interactively
  tools reset         clear the override (read-only default)
EOF
}

pid() {
    [ -s "$PID_FILE" ] || return 1
    cat "$PID_FILE"
}

is_running() {
    local p
    p=$(pid) || return 1
    [ -n "$p" ] && kill -0 "$p" 2>/dev/null
}

auth_ok() {
    local token
    token=$(cat "$TOKEN_FILE")
    curl -fsS -H "Authorization: Bearer $token" "http://$LISTEN/v1/info" >/dev/null 2>&1
}

wait_ready() {
    local i
    for i in $(seq 1 40); do
        auth_ok && return 0
        sleep 0.5
    done
    return 1
}

start() {
    [ -x "$DIR/helldye-gateway" ] || die "not installed; run the HELLDYE installer first"
    [ -s "$TOKEN_FILE" ] || die "missing $TOKEN_FILE; run the HELLDYE installer first"

    if is_running; then
        echo "hellgate already running (pid $(pid)) on http://$LISTEN"
        return 0
    fi

    local pi="${PI_BOLT:-$(command -v pi-bolt || true)}"
    [ -n "$pi" ] || die "pi-bolt not found; install Pi-Bolt or set PI_BOLT=/path/to/pi-bolt"

    if [ -s "$TOOLS_FILE" ]; then
        export HELLDYE_TOOLS="$(cat "$TOOLS_FILE")"
    fi

    HELLDYE_TOKEN="$(cat "$TOKEN_FILE")" nohup "$DIR/helldye-gateway" \
        --listen "$LISTEN" --pi "$pi" >>"$LOG_FILE" 2>&1 &
    echo $! >"$PID_FILE"

    if wait_ready && is_running; then
        echo "hellgate running on http://$LISTEN (pid $(cat "$PID_FILE"))"
    else
        echo "hellgate did not start or rejected the token; last log lines:" >&2
        tail -n 5 "$LOG_FILE" >&2 || true
        return 1
    fi
}

stop() {
    if ! is_running; then
        rm -f "$PID_FILE"
        echo "hellgate is not running"
        return 0
    fi

    local p
    p=$(cat "$PID_FILE")
    kill "$p" 2>/dev/null || true
    for _ in $(seq 1 20); do
        is_running || break
        sleep 0.2
    done
    if is_running; then
        kill -9 "$p" 2>/dev/null || true
    fi
    rm -f "$PID_FILE"
    echo "hellgate stopped"
}

status() {
    if ! is_running; then
        echo "hellgate stopped"
        return 1
    fi
    echo "hellgate running on http://$LISTEN (pid $(pid))"
    if auth_ok; then
        echo "token: ok"
    else
        echo "token: rejected; another gateway may hold the port, or the token changed"
        return 1
    fi
}

show_token() {
    [ -s "$TOKEN_FILE" ] || die "missing $TOKEN_FILE; run the HELLDYE installer"
    cat "$TOKEN_FILE"
    echo
}

current_tools() {
    if [ -s "$TOOLS_FILE" ]; then
        cat "$TOOLS_FILE"
    else
        echo "read,grep,find,ls"
    fi
}

show_tools() {
    if [ -s "$TOOLS_FILE" ]; then
        local tools
        tools=$(cat "$TOOLS_FILE")
        if [ "$tools" = "*" ]; then
            echo "all tools, including extension tools"
        else
            echo "$tools"
        fi
    else
        echo "read,grep,find,ls (gateway default)"
    fi
}

write_tools() {
    printf '%s' "$1" >"$TOOLS_FILE"
    if [ "$1" = "*" ]; then
        echo "Tool allowlist set to: all tools, including extension tools"
    else
        echo "Tool allowlist set to: $1"
    fi
    echo "Run: hellgate restart"
}

select_tools() {
    [ -t 0 ] || die "run 'hellgate tools select' in an interactive terminal"
    local current=",$(current_tools),"
    if [ "$(current_tools)" = "*" ]; then
        current=",$(IFS=,; echo "${ALL_TOOLS[*]}"),"
    fi
    local i name mark
    echo "Available built-in tools (extensions are included by 'a'):"
    for i in "${!ALL_TOOLS[@]}"; do
        name="${ALL_TOOLS[$i]}"
        mark=" "
        case "$current" in
        *",$name,"*) mark="x" ;;
        esac
        printf '  %d) [%s] %s\n' "$((i + 1))" "$mark" "$name"
    done
    echo
    echo "Enter numbers to select (comma or space separated), a = all incl. extensions, r = read-only, Enter = cancel."
    local choice
    read -r -p "> " choice || return 0

    case "$choice" in
    a | all)
        write_tools "*"
        return
        ;;
    r | read-only)
        write_tools "$(IFS=,; echo "${READ_ONLY_TOOLS[*]}")"
        return
        ;;
    "")
        echo "Cancelled"
        return 0
        ;;
    esac

    local selected=() n
    for n in $(echo "$choice" | tr ',' ' '); do
        case "$n" in
        "" | *[!0-9]*) continue ;;
        esac
        if [ "$n" -ge 1 ] && [ "$n" -le "${#ALL_TOOLS[@]}" ]; then
            selected+=("${ALL_TOOLS[$((n - 1))]}")
        fi
    done
    if [ ${#selected[@]} -eq 0 ]; then
        die "no valid tools selected"
    fi

    local result="" tool
    for tool in "${selected[@]}"; do
        case ",$result," in
        *",$tool,"*) ;;
        *) result="${result:+$result,}$tool" ;;
        esac
    done
    write_tools "$result"
}

set_tools() {
    case "${1:-}" in
    "") show_tools ;;
    all) write_tools "*" ;;
    read-only) write_tools "$(IFS=,; echo "${READ_ONLY_TOOLS[*]}")" ;;
    select) select_tools ;;
    reset)
        rm -f "$TOOLS_FILE"
        echo "Tool allowlist reset to the gateway default: read,grep,find,ls"
        echo "Run: hellgate restart"
        ;;
    *) write_tools "$1" ;;
    esac
}

case "${1:-}" in
start) start ;;
stop) stop ;;
restart)
    stop
    start
    ;;
status) status ;;
token) show_token ;;
tools) set_tools "${2:-}" ;;
help | -h | --help) usage ;;
*)
    usage
    exit 2
    ;;
esac
