@echo off
cd /d "%~dp0"

echo Starting System Agent...

start "SA-Server" cmd /k "java -cp "out;lib/sqlite-jdbc-3.36.0.3.jar" agent.FakeServer"
timeout /t 3 /nobreak >nul

start "SA-Agent" cmd /k "java -cp "out;lib/sqlite-jdbc-3.36.0.3.jar" agent.Main"
timeout /t 2 /nobreak >nul

start "" http://localhost:9000/login
