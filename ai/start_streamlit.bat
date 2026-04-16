@echo off
cd /d "%~dp0"

start "Streamlit" cmd /k "venv\Scripts\activate && streamlit run streamlit_app.py --server.port 8501 --server.headless true"

timeout /t 3 /nobreak > nul

start "Tunnel-Streamlit" cmd /k "cloudflared tunnel --url http://localhost:8501"
