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
RELEASE_URL="https://github.com/$REPO/releases/download/latest"

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
# The repository is public: releases download without a token. A token only raises the
# unauthenticated API limit and is required for artifact downloads from a non-main branch.

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
    if [ -n "$AUTH" ]; then
        curl -fsSL -H "Authorization: Bearer $AUTH" -H "Accept: application/vnd.github+json" "$@"
    else
        curl -fsSL -H "Accept: application/vnd.github+json" "$@"
    fi
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

run_info() {
    api "$API/actions/runs/$1" |
        jq -r '"\(.head_sha[0:8]) \(.created_at[0:16])"'
}

fetch() { # <run id> <artifact name> <zip> <dir>
    local url
    url=$(artifact_url "$1" "$2")
    [ -n "$url" ] || die "artifact $2 not found in run $1"
    info "downloading $2"
    if [ -n "$AUTH" ]; then
        curl -fsSL -H "Authorization: Bearer $AUTH" -L "$url" -o "$3" ||
            die "download failed for $2"
    else
        curl -fsSL -L "$url" -o "$3" || die "download failed for $2"
    fi
    unzip -q -o "$3" -d "$4"
}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

if [ "$BRANCH" = "main" ]; then
    # Public release assets; no token and no API rate limit.
    mkdir -p "$tmp/gateway"
    info "downloading the latest gateway release"
    curl -fsSL "$RELEASE_URL/helldye-gateway" -o "$tmp/gateway/helldye-gateway" ||
        die "no latest gateway release; wait for the main build or pass a branch"
    gateway_bin="$tmp/gateway/helldye-gateway"

    mkdir -p "$tmp/apk"
    info "downloading the latest APK release"
    curl -fsSL "$RELEASE_URL/HELLDYE.apk" -o "$tmp/apk/HELLDYE.apk" ||
        die "no latest APK release; wait for the main build or pass a branch"
    apk_file="$tmp/apk/HELLDYE.apk"
else
    [ -n "$AUTH" ] || die "GITHUB_TOKEN is required to download artifacts from $BRANCH"
    gateway_run=$(latest_run gateway.yml)
    [ -n "$gateway_run" ] || die "no successful gateway build on $BRANCH"
    info "gateway build: $(run_info "$gateway_run")"
    fetch "$gateway_run" "$GATEWAY_ARTIFACT" "$tmp/gateway.zip" "$tmp/gateway"
    gateway_bin=$(find "$tmp/gateway" -type f -name helldye-gateway | head -n 1)
    [ -n "$gateway_bin" ] || die "gateway binary missing from the artifact"

    apk_run=$(latest_run ci.yml)
    [ -n "$apk_run" ] || die "no successful Android build on $BRANCH"
    info "apk build:     $(run_info "$apk_run")"
    fetch "$apk_run" "$APK_ARTIFACT" "$tmp/apk.zip" "$tmp/apk"
    apk_file=$(find "$tmp/apk" -type f -name '*.apk' | head -n 1)
    [ -n "$apk_file" ] || die "APK missing from the artifact"
fi

mkdir -p "$GATEWAY_DIR"
install -m 755 "$gateway_bin" "$GATEWAY_DIR/helldye-gateway"

if [ ! -d /sdcard/Download ]; then
    info "requesting storage access"
    command -v termux-setup-storage >/dev/null && termux-setup-storage || true
    for _ in $(seq 1 60); do
        [ -d /sdcard/Download ] && break
        sleep 1
    done
fi
mkdir -p "$APK_DIR" || die "cannot write $APK_DIR; grant storage access with termux-setup-storage and rerun"
rm -f "$APK_DIR/HELLDYE.apk"
cp "$apk_file" "$APK_DIR/HELLDYE.apk"
apk_size=$(du -h "$APK_DIR/HELLDYE.apk" | cut -f1)
apk_hash=$(sha256sum "$APK_DIR/HELLDYE.apk" | cut -c1-12)

mkdir -p "$(dirname "$TOKEN_FILE")"
if [ -s "$TOKEN_FILE" ]; then
    TOKEN=$(cat "$TOKEN_FILE")
else
    TOKEN=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')
    printf '%s' "$TOKEN" >"$TOKEN_FILE"
fi
chmod 600 "$TOKEN_FILE"

tmp_hellgate="$PREFIX/bin/hellgate.new"
if [ "$BRANCH" = "main" ]; then
    curl -fsSL "$RELEASE_URL/hellgate.sh" -o "$tmp_hellgate" ||
        die "could not download hellgate from the latest release"
elif [ -n "$AUTH" ]; then
    curl -fsSL -H "Authorization: Bearer $AUTH" -H "Accept: application/vnd.github.raw" \
        "$API/contents/scripts/hellgate.sh?ref=$BRANCH" >"$tmp_hellgate" ||
        die "could not install hellgate"
else
    curl -fsSL -H "Accept: application/vnd.github.raw" \
        "$API/contents/scripts/hellgate.sh?ref=$BRANCH" >"$tmp_hellgate" ||
        die "could not install hellgate"
fi
chmod 755 "$tmp_hellgate"
# Replace atomically: `hellgate update` may be running this file right now.
mv "$tmp_hellgate" "$PREFIX/bin/hellgate"
rm -f "$GATEWAY_DIR/start.sh"

info "restarting the gateway on $LISTEN"
healthy=""
if "$PREFIX/bin/hellgate" restart; then
    healthy=1
fi

echo
echo "gateway: http://$LISTEN"
echo "token:   $TOKEN"
echo "apk:     $APK_DIR/HELLDYE.apk ($apk_size, sha256 ${apk_hash}…)"
echo
if [ -n "$healthy" ]; then
    echo "Gateway is up. Enter the URL and token above in HELLDYE settings,"
    echo "then tap the APK in Downloads to install it."
else
    echo "Gateway did not start. Log: $GATEWAY_DIR/gateway.log"
fi
echo "Manage it with: hellgate start|stop|restart|status"
echo "If the app reports HTTP 401, copy the token with: hellgate token"
echo "If Android says 'app not installed', uninstall the old HELLDYE once."
