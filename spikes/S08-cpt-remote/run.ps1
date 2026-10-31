# S08 - CPT remote mode against running c8run (localhost:8080 / 26500 / 9600, demo/demo)
# Each test in its own JVM: a 2nd test in the same Spring context loses its @JobWorker (see RESULT.md)
Set-Location $PSScriptRoot
mvn -B test "-Dtest=TimerProcessRemoteTest#pt1hTimerCompletesAfterIncreaseTime"        # expect SKIPPED (clock immutable) or PASS if clock-controlled
mvn -B test "-Dtest=TimerProcessRemoteTest#shortTimerViaVariableCompletesWithoutClockControl"  # expect PASS
