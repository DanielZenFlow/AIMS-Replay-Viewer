@echo off
java -jar target\aims-replay-viewer-1.0.1.jar proxy-client --client "powershell -NoProfile -ExecutionPolicy Bypass -File examples\simple-client.ps1" --out-dir replays --viewer-dir .
