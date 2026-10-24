#!/usr/bin/env bash
# Installs the latest main-branch HELLDYE gateway and APK inside Termux.
set -euo pipefail

REPO="${HELLDYE_REPO:-freovensh0/helldye}"
BRANCH="${HELLDYE_BRANCH:-main}"
GATEWAY_ARTIFACT="helldye-gateway-termux-arm64"
APK_ARTIFACT="helldye-debug"
GATEWAY_DIR="$HOME/helldye-gateway"
TOKEN_FILE="$HOME/.config/helldye/token"
APK_DIR="/sdcard/Download/Termux"
LISTEN="${HELLDYE_LISTEN:-127.0.0.1:8787}"
API="https://api.github.com/repos/$REPO"

die() {
    echo "error: $*" >&2
    exit 1
}

info() {
    echo "==> $*"
}

case "${PREFIX:-}" in
*com.termux*) ;;
*) die "run this installer inside Termux" ;;
esac

AUTH="${GITHUB_TOKEN:-${GH_TOKEN:-}}"
[ -n "$AUTH" ] || die "GITHUB_TOKEN is required (the repository is private)"

missing=()
for cmd in curl unzip jq; do
    command -v "$cmd" >/dev/null || missing+=("$cmd")
done
if [ ${#missing[@]} -gt 0 ]; then
    info "installing ${missing[*]}"
    pkg install -y "${missing[@]}" >/dev/null || die "could not install ${missing[*]}"
fi

PI="${PI_BOLT:-$(command -v pi-bolt || true)}"
[ -n "$PI" ] || die "pi-bolt not found; install Pi-Bolt or set PI_BOLT=/path/to/pi-bolt"

api() {
    curl -fsSL -H "Authorization: Bearer $AUTH" -H "Accept: application/vnd.github+json" "$@"
}

latest_run() {
    api "$API/actions/workflows/$1/runs?branch=$BRANCH&status=success&per_page=1" |
        jq -r '.workflow_runs[0].id // empty'
}

artifact_url() {
    api "$API/actions/runs/$1/artifacts?per_page=100" |
        jq -r --arg name "$2" \
            '[.artifacts[] | select(.name == $name and (.expired | not))][0].archive_download_url // empty'
}

fetch() { # <run id> <artifact name> <zip> <dir>
    local url
    url=$(artifact_url "$1" "$2")
    [ -n "$url" ] || die "artifact $2 not found in run $1"
    info "downloading $2"
    curl -fsSL -H "Authorization: Bearer $AUTH" -L "$url" -o "$3" ||
        die "download failed for $2"
    unzip -q -o "$3" -d "$4"
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

gateway_run=$(latest_run gateway.yml)
[ -n "$gateway_run" ] || die "no successful gateway build on $BRANCH"
fetch "$gateway_run" "$GATEWAY_ARTIFACT" "$tmp/gateway.zip" "$tmp/gateway"
gateway_bin=$(find "$tmp/gateway" -type f -name helldye-gateway | head -n 1)
[ -n "$gateway_bin" ] || die "gateway binary missing from the artifact"
mkdir -p "$GATEWAY_DIR"
install -m 755 "$gateway_bin" "$GATEWAY_DIR/helldye-gateway"

apk_run=$(latest_run ci.yml)
[ -n "$apk_run" ] || die "no successful Android build on $BRANCH"
fetch "$apk_run" "$APK_ARTIFACT" "$tmp/apk.zip" "$tmp/apk"
apk_file=$(find "$tmp/apk" -type f -name '*.apk' | head -n 1)
[ -n "$apk_file" ] || die "APK missing from the artifact"

if [ ! -d /sdcard/Download ]; then
    info "requesting storage access"
    command -v termux-setup-storage >/dev/null && termux-setup-storage || true
    for _ in $(seq 1 60); do
        [ -d /sdcard/Download ] && break
        sleep 1
    done
fi
mkdir -p "$APK_DIR" || die "cannot write $APK_DIR; grant storage access with termux-setup-storage and rerun"
cp "$apk_file" "$APK_DIR/HELLDYE.apk"

mkdir -p "$(dirname "$TOKEN_FILE")"
if [ -s "$TOKEN_FILE" ]; then
    TOKEN=$(cat "$TOKEN_FILE")
else
    TOKEN=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
    printf '%s' "$TOKEN" >"$TOKEN_FILE"
fi
chmod 600 "$TOKEN_FILE"

cat >"$GATEWAY_DIR/start.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export HELLDYE_TOKEN="\$(cat "$TOKEN_FILE")"
exec "$GATEWAY_DIR/helldye-gateway" --listen "$LISTEN" --pi "$PI"
EOF
chmod 755 "$GATEWAY_DIR/start.sh"

info "restarting the gateway on $LISTEN"
if [ -f "$GATEWAY_DIR/gateway.pid" ]; then
    kill "$(cat "$GATEWAY_DIR/gateway.pid")" 2>/dev/null || true
    sleep 1
fi
nohup "$GATEWAY_DIR/start.sh" >>"$GATEWAY_DIR/gateway.log" 2>&1 &
echo $! >"$GATEWAY_DIR/gateway.pid"

healthy=""
for _ in $(seq 1 30); do
    if curl -fsS "http://$LISTEN/health" >/dev/null 2>&1; then
        healthy=1
        break
    fi
    sleep 0.5
done

echo
echo "gateway: http://$LISTEN"
echo "token:   $TOKEN"
echo "apk:     $APK_DIR/HELLDYE.apk"
echo
if [ -n "$healthy" ]; then
    echo "Gateway is up. Enter the URL and token above in HELLDYE settings,"
    echo "then tap the APK in Downloads to install it."
else
    echo "Gateway did not answer /health yet. Log: $GATEWAY_DIR/gateway.log"
fi
echo "Restart later with: ~/helldye-gateway/start.sh"
