#!/usr/bin/env bash
# Render every mock screen to shots/<direction>-<screen>.png at 1920x1080.
set -euo pipefail
cd "$(dirname "$0")"
CHROME="${CHROME:-$HOME/.cache/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-linux64/chrome-headless-shell}"
mkdir -p shots
for dir in ${@:-osd dashboard androidtv}; do
  for dir1 in $dir; do
    [ -f "$dir1.html" ] || continue
    for s in home library options launch quick perf keyboard settings pair; do
      "$CHROME" --headless --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
        --window-size=1920,1080 --virtual-time-budget=4000 \
        --screenshot="shots/$dir1-$s.png" "file://$PWD/$dir1.html?s=$s" >/dev/null 2>&1
    done
  done
done
ls shots
