# Debugging Embrace Gradle Plugin
You can attach a debugger to an integration test by:

1. Set `attachDebugger = true` on the test case
2. Select 'Edit Configurations' in Android Studio
3. Add & save a new 'Remote JVM Debug' configuration
4. Run the test case (without a debugger)
5. Set a breakpoint where you require in the gradle plugin code
6. Run the 'Remote JVM Debug' configuration
7. Debug as you normally would!

## Debugging the debugger
If the debugger can't attach, please check whether the port is in use via `lsof -i :5005`
and kill the process if necessary. Running `./gradlew --stop` may also help.

## How does it work?
Setting `attachDebugger = true` adds `-Dorg.gradle.debug=true` and `--no-daemon` to the Gradle TestKit arguments.
This principle can be used anytime a gradle task needs debugging and doesn't strictly need to happen in an integration test.
