#!/usr/bin/env bash
set -e


sh -c ./open_app.sh
sh -c ./compile_aoa.sh


sox -t coreaudio "BlackHole 2ch" -n trim 0 3600 2>/dev/null &
KEEPALIVE_PID=$!

sox --buffer 128 \
    -t coreaudio "BlackHole 2ch" \
    -r 48000 -c 2 -b 16 -e signed-integer -L \
    -t raw - 2>/dev/null \
| ./aoa &
STREAM_PID=$!

cleanup() {
  echo "Encerrando..."
  kill -9 $KEEPALIVE_PID
  kill -9 $STREAM_PID
  adb shell am force-stop dev.victorlpgazolli.mobilesink
  kill 0
}
trap cleanup EXIT INT TERM

wait $KEEPALIVE_PID $STREAM_PID
