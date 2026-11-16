@echo off
rem Starts Mailpit + Camunda 8.10 (c8run) in the background. Start the Docker containers yourself afterwards.
rem Closing this window does NOT stop them; use stop.bat for that.
powershell -ExecutionPolicy Bypass -File "%~dp0bpmn-models\deploy\start-all.ps1"
pause
