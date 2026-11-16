@echo off
rem Stops Camunda 8.10 (c8run) and Mailpit. Stop the Docker containers yourself.
powershell -ExecutionPolicy Bypass -File "%~dp0bpmn-models\deploy\stop-all.ps1"
pause
