# Writing integration tests
Each test case boots a Gradle build, so prefer covering several behaviours in one build over adding a new test case:

- List several `tasks` in one `runTest` call to run them in one build. This reuses the project directory and resets
  the fake API server
- Express config scenarios (e.g. NDK disabled, config overrides) as extra build types in one fixture, with its own
  `src/<buildType>/embrace-config.json`, rather than separate test cases/fixtures
- Give any test that asserts several behaviours a KDoc `Covers:` list that detail responsibilities

# TestKit home
Fixture builds share a TestKit Gradle user home at `~/.gradle/embrace-testkit` so that `./gradlew clean` keeps its
warm dependency, transform and build caches. Set `EMBRACE_TESTKIT_DIR` to use a different directory, e.g. an empty one
to reproduce a cold CI run. Old matrix versions accumulate there, so delete it if it grows too large.

Tests run in 2 forks by default; pass `-PintegrationTestForks=1` to run them serially.

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
