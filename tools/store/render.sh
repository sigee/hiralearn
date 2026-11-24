#!/bin/sh
# Renders the Play Store graphics from their SVG sources with headless Edge or Chrome (no other tools needed).
# Usage: tools/store/render.sh   (from the project root)
set -e
browser="${BROWSER_BIN:-/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe}"
out=fastlane/metadata/android/en-US/images
mkdir -p "$out"
render() { # svg, png, width, height
    html="$(mktemp -d)/page.html"
    printf '<html><body style="margin:0;overflow:hidden">%s</body></html>' "$(cat "$1")" > "$html"
    "$browser" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
        --window-size="$3,$4" --screenshot="$(cygpath -w "$PWD/$2" 2>/dev/null || echo "$PWD/$2")" "file:///$(cygpath -m "$html" 2>/dev/null || echo "$html")" >/dev/null 2>&1
}
render tools/store/icon.svg "$out/icon.png" 512 512

# Feature graphic per language: fonts inlined as base64, tagline per locale.
feature() { # locale, tagline
    page="$(mktemp -d)/feature.html"
    b64() { base64 -w0 "app/src/main/res/font/$1"; }
    sed -e "s|__LANG__|${1%%-*}|" -e "s|__TAGLINE__|$2|" -e "s|__KLEE__|$(b64 klee_one_regular.ttf)|" \
        -e "s|__SERIF__|$(b64 noto_serif_jp_regular.ttf)|" -e "s|__SERIF_SB__|$(b64 noto_serif_jp_semibold.ttf)|" \
        tools/store/feature.html > "$page"
    mkdir -p "fastlane/metadata/android/$1/images"
    "$browser" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --window-size=1024,500 \
        --screenshot="$(cygpath -w "$PWD/fastlane/metadata/android/$1/images/featureGraphic.png" 2>/dev/null || echo "$PWD/fastlane/metadata/android/$1/images/featureGraphic.png")" \
        "file:///$(cygpath -m "$page" 2>/dev/null || echo "$page")" >/dev/null 2>&1
}
feature en-US "Read, write and hear hiragana"
feature hu-HU "Hiragana: olvasás, írás, hallás"
feature de-DE "Hiragana lesen, schreiben, hören"
