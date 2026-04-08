@echo off
title With Buddy

cd /d "%~dp0"

py -3 -m pip install -r requirements.txt --quiet

start "FastAPI" cmd /k "py -3 -m uvicorn main:app --reload --reload-dir routers --reload-dir core --reload-dir memory --reload-dir chains --reload-dir agents --reload-dir utils --host 0.0.0.0 --port 8000"

timeout /t 5 /nobreak > nul

start "Cloudflared" cmd /k "cloudflared tunnel --url http://localhost:8000"
