#!/usr/bin/env bash
# Contract guard: screen context is screenshot-only. Documentation is exempt by path; this scans
# the app, the gateway, and the dependency catalogs that could introduce text extraction.
set -euo pipefail
cd "$(dirname "$0")/.."

status=0

scan() {
    local label="$1"
    local pattern="$2"
    shift 2
    local matches
    matches=$(grep -RInE --binary-files=without-match \
        --exclude-dir=.git --exclude-dir=build --exclude-dir=target \
        --exclude-dir=docs "$pattern" "$@" 2>/dev/null || true)
    if [ -n "$matches" ]; then
        printf 'contract guard: %s\n%s\n' "$label" "$matches" >&2
        status=1
    fi
}

scan "screen-text extraction is forbidden" \
    'READ_ASSIST_STRUCTURE_SCREEN_CONTENT' app gateway/src
scan "AssistStructure traversal is forbidden" \
    'AssistStructure' --include='*.kt' --include='*.java' app
scan "screen-content assist data must stay disabled" \
    'usesAssistStructureScreenContent="true"' app
scan "accessibility scraping is forbidden" 'AccessibilityService' app gateway/src
scan "media projection is forbidden" 'MediaProjection' app gateway/src
scan "OCR API is forbidden" 'TextRecognizer|InputImage' app
scan "OCR dependency is forbidden" \
    '(^|[^a-z])(ocr|mlkit|ml-kit|tesseract)([^a-z]|$)' \
    gradle/libs.versions.toml app/build.gradle.kts gateway/Cargo.toml

if [ "$status" -ne 0 ]; then
    echo "contract guard failed" >&2
fi
exit "$status"
