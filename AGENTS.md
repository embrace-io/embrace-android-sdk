# Embrace Android SDK - Development Guide

This document captures the development conventions, architecture, and rules for the Embrace Android SDK.
It is intended for AI coding agents and human contributors alike.

> **Canonical sources**: This file distills conventions from `CONTRIBUTING.md`, `buildSrc/`, `config/detekt/`,
> `.editorconfig`, module READMEs, and build scripts. When those sources change, this file should be updated.
>
> **Last update**: February 9 2026. AI agents should regenerate this document when the project's structure or
> tooling materially changes or seems out-of-date, and allow humans to review the changes.

---

## Project Overview

The Embrace Android SDK is an observability SDK for Android apps built on [OpenTelemetry](https://opentelemetry.io).
It captures performance telemetry (spans, logs, crashes, network requests) and delivers it to the Embrace backend.
It is published to Maven Central under the `io.embrace` group.

---

## Build & Toolchain

| Tool                         | Version / Notes                                                            |
|------------------------------|----------------------------------------------------------------------------|
| Language                     | Kotlin (primary), some minimal Java/C++                                    |
| Min Supported Kotlin version | 2.0                                                                        |
| Kotlin compile-time version  | 2.3                                                                        |
| JVM target                   | 11                                                                         |
| Android minSdk               | 21                                                                         |
| Android compileSdk           | 36                                                                         |
| Build system                 | Gradle (Kotlin DSL) with convention plugins in `buildSrc/`                 |
| Dependency catalog           | `gradle/libs.versions.toml`                                                |
| Java                         | 21 (CI), 11 (target compatibility)                                         |
| Serialization                | Moshi (with KSP codegen)                                                   |
| OTel                         | `io.opentelemetry.kotlin` (Kotlin-friendly OpenTelemetry wrappers) |
| HTTP                         | OkHttp 4.x                                                                 |
| Configuration cache          | Enabled, problems=fail                                                     |

### Key Commands

```bash
# Full build (compile + lint + detekt + unit tests)
./gradlew build

# Build excluding slow integration tests
./gradlew build -x embrace-gradle-plugin-integration-tests:test

# Run tests for a specific module
./gradlew :embrace-android-core:test

# Run integration tests (in the SDK module)
./gradlew :embrace-android-sdk:test

# Code coverage report (XML)
./gradlew koverXmlReport

# Build the example app
cd examples/ExampleApp && ./gradlew bundleRelease

# Update binary compatibility API dumps
./gradlew apiDump
```

---

## Module Architecture

The project has several modules organized into layers:

### Public API Modules

These use the `embrace-public-api-conventions` plugin, which enforces `kotlin.explicitApi()`,
binary compatibility validation, and Dokka documentation.

- **`embrace-android-api`** - Public API surface for SDK consumers (spans, logs, network, user, session APIs)
- **`embrace-android-sdk`** - Main SDK entrypoint; aggregates all instrumentation and wires everything together
- **`embrace-android-otel-java`** - Java-compatible OTel bindings

### Core Implementation

- **`embrace-android-core`** - Main implementation module; hidden from library consumers via `embrace-android-sdk`
- **`embrace-android-infra`** - JVM-only core infrastructure types used across most modules (e.g., `InternalLogger`, `BackgroundWorker`,
  `Clock`)
- **`embrace-android-utils`** - Android Framework utilities
- **`embrace-internal-api`** - APIs shared across Embrace modules and Embrace's React Native/Unity/Flutter SDKs, but not exposed to
  consumers

### Data & Delivery

- **`embrace-android-payload`** - Data models for HTTP payloads (Moshi `@JsonClass` data classes)
- **`embrace-android-envelope`** - Envelope wrapping for payloads
- **`embrace-android-delivery`** - Telemetry delivery mechanism
- **`embrace-android-config`** - Configuration management and remote config
- **`embrace-android-telemetry-persistence`** - Disk persistence for telemetry data

### OpenTelemetry

- **`embrace-android-otel`** - OTel Kotlin SDK integration (spans, logs, exporters)

### Instrumentation Modules (`embrace-android-instrumentation-*`)

Each captures a specific type of telemetry. These include:
`anr`, `app-exit-info`, `compose-tap`, `crash-jvm`, `crash-ndk`, `fcm`, `huc`, `huc-lite`,
`network-common`, `network-status`, `okhttp`, `power-save`, `profiler`, `startup-trace`,
`taps`, `thermal-state`, `view`, `webview`

Instrumentation modules broadly follow this pattern:

- Implement a data source class that extends framework types from `embrace-android-instrumentation-api`
- Register with the `InstrumentationRegistry` in `embrace-android-core`
- Use `SchemaType` from `embrace-android-instrumentation-schema` for telemetry attributes

### Test Modules

- **`embrace-test-common`** - Common test utilities
- **`embrace-test-fakes`** - Shared fake implementations (see Testing section)
- **`embrace-android-*-fakes`** - Module-specific fakes for `config`, `delivery`, `otel`, `instrumentation-api`

### Build Tooling

- **`embrace-gradle-plugin`** - Gradle plugin for bytecode instrumentation and uploading R8/Dexguard/SO/JS mapping files for getting
  readable production stacktraces
- **`embrace-gradle-plugin-integration-tests`** - Gradle TestKit-based integration tests
- **`embrace-bytecode-instrumentation-tests`** - Bytecode instrumentation verification
- **`embrace-lint`** - Custom Android Lint checks (applied via `lintChecks`)

---

## Convention Plugins (`buildSrc/`)

All modules use convention plugins instead of duplicating build configuration:

| Plugin                             | Applies to                  | What it does                                                   |
|------------------------------------|-----------------------------|----------------------------------------------------------------|
| `embrace-common-conventions`       | All modules                 | Detekt, compiler settings, JVM target                          |
| `embrace-android-conventions`      | Android library modules     | compileSdk, minSdk, lint, test config, Kotlin compiler options |
| `embrace-jvm-conventions`          | JVM-only modules            | JVM compiler settings, test config                             |
| `embrace-prod-android-conventions` | Publishable Android modules | Adds publishing + Kover on top of android-conventions          |
| `embrace-prod-jvm-conventions`     | Publishable JVM modules     | Adds publishing + Kover on top of jvm-conventions              |
| `embrace-public-api-conventions`   | Public API modules          | Explicit API mode, binary compatibility validator, Dokka       |
| `embrace-publishing-conventions`   | All published modules       | Maven Central publishing via vanniktech plugin                 |

When creating a new module, apply the appropriate convention plugin rather than configuring build settings directly.

---

## Code Style & Formatting

### Enforced by Tooling

- **Max line length**: 140 characters
- **Detekt**: Zero-tolerance (`maxIssues: 0`), auto-correct enabled
- **Kotlin compiler**: `allWarningsAsErrors = true`
- **Android Lint**: `warningsAsErrors = true`, `abortOnError = true`
- **Trailing commas**: Preferred on declaration sites and call sites

### Key Detekt Rules

- `BracesOnIfStatements`: Always required on multiline, consistent on single-line
- `MandatoryBracesLoops`: Always required
- `ForbiddenImport`: `android.util.Pair` is forbidden (use `kotlin.Pair`)
- `ElseCaseInsteadOfExhaustiveWhen`: Prefer exhaustive `when` expressions
- `DataClassShouldBeImmutable` / `DataClassContainsFunctions`: Enforced in `**/payload/**` packages
- `UnusedImports`: Enforced
- `SpacingBetweenPackageAndImports`: Enforced
- Each module may have a `config/detekt/baseline.xml` for suppressed legacy issues

### Conventions

- All new code must be Kotlin
- Package: `io.embrace.android.embracesdk.internal.*` for internal code
- Public API classes live under `io.embrace.android.embracesdk` (non-`internal` packages)
- Use `@InternalApi` annotation to mark APIs that are internal but technically visible
- Prefer `internal` visibility for implementation classes
- Public API modules use `kotlin.explicitApi()` - all declarations must have explicit visibility
- No business logic in payload data classes (enforced by detekt)
- JSON files should not have a trailing newline
- Prefer interfaces rather than concrete classes for public APIs

---

## Dependency Injection

The SDK uses **manual dependency injection** via module interfaces:

```
InitModule -> CoreModule -> EssentialServiceModule -> ...
```

- Each DI module is defined as an **interface** in `embrace-android-core/.../injection/`
- Implementations are `*Impl` classes (e.g., `InitModuleImpl`)
- Fake implementations exist in `embrace-test-fakes` for testing (e.g., `FakeInitModule`)
- The `ModuleInitBootstrapper` in `embrace-android-sdk` wires all modules together

---

## Concurrency

- **No coroutines** in the SDK codebase - concurrency uses `ScheduledExecutorService`
- `BackgroundWorker` (in `embrace-android-infra`) wraps `ScheduledExecutorService` to limit API surface
- `WorkerThreadModule` manages thread pools
- Tests use `FakeWorkers` for deterministic scheduling

---

## Testing

### Frameworks

- **JUnit 4** (not JUnit 5)
- **Robolectric** for Android framework mocking in unit tests
- **MockK** for Kotlin mocking
- **OkHttp MockWebServer** for HTTP testing

### Test Organization

**Unit tests**: `src/test/kotlin/` in each module

- Standard JUnit 4 tests with `@Test`, `@Before`, etc.
- Use backtick-quoted test names: `` `sensitive properties are redacted` ``
- Tests are `internal class`

**Integration tests**: `src/integrationTest/kotlin/` in `embrace-android-sdk`

- Uses `SdkIntegrationTestRule` (a JUnit `ExternalResource` rule)
- Boots the full SDK with controlled fakes for time, config, and delivery
- Tests live in `testcases/` and `testcases/features/` packages
- Test framework utilities in `testframework/` package

**Gradle plugin integration tests**: `embrace-gradle-plugin-integration-tests`

- Uses Gradle TestKit
- Supports remote JVM debugging (see module README)

### Fake Conventions

- **Prefer fakes over mocks**: The codebase has extensive hand-written fakes in dedicated modules
- Fakes are named `Fake*` (e.g., `FakeConfigService`, `FakeClock`, `FakePayloadStore`)
- Module-specific fakes live in `embrace-android-*-fakes` modules
- Cross-cutting fakes live in `embrace-test-fakes`
- Common test utilities in `embrace-test-common`
- Each module's tests may also have local fakes in `src/test/kotlin/.../fakes/`
- Mocks should not be used unless they are unavoidable

### Test Configuration

- `unitTests.isReturnDefaultValues = true` (Android methods return defaults instead of throwing)
- `unitTests.isIncludeAndroidResources = true`
- Max parallel forks: `(availableProcessors / 3) + 1`
- Max heap: 2g
- Uses AndroidX Test Orchestrator

---

## Public API Compatibility

- Public API modules use the [Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
- API dumps are stored in `<module>/api/<module-name>.api`
- Any change to public API signatures will fail CI until `./gradlew apiDump` is run
- Public API modules must have Dokka documentation; build fails on Dokka warnings
- `internal` packages are suppressed in generated docs

---

## Versioning & Publishing

- Version is in `gradle.properties` (currently `8.2.0-SNAPSHOT`)
- Published to Maven Central via vanniktech maven-publish plugin
- Group ID: `io.embrace`
- Artifact IDs match module names (e.g., `embrace-android-sdk`, `embrace-android-api`)
- Snapshot publishing runs daily via CI
- Release workflow: `create-release-branch.yml` -> `pre-release-workflow.yml` -> `upload-artifacts-to-maven-central.yml`

---

## CI/CD

- **Platform**: GitHub Actions
- **Main CI** (`ci-gradle.yml`): Runs on push to `main` and all PRs
    - Runs `./gradlew build` (includes compile, lint, detekt, unit tests)
    - Runs `koverXmlReport` for code coverage
    - Uploads coverage to Codecov
    - Builds example app
- **Java 21** on CI, uses Depot runners for non-dependabot builds
- **Robolectric dependencies** are pre-fetched and cached
- **Configuration cache** is enabled

---

## PR Guidelines

From `CONTRIBUTING.md`:

- PRs must have a stated goal and detailed description
- Include test coverage and documentation where applicable
- Pass all CI checks (build, lint, detekt, tests)
- Require at least one approval from a project member
- **AI disclosure required**: none, autocomplete/research, or mostly AI-generated
- Follow existing code and naming conventions
- Lint suppression must be done in code with explanation
- Commits should be reasonably small (<500 lines diff) with proper messages
- PR template has `Goal` and `Testing` sections

---

## Key Patterns to Follow

### Adding a New Instrumentation Module

1. Create module named `embrace-android-instrumentation-<feature>`
2. Apply `embrace-prod-android-conventions` plugin
3. Implement a data source extending types from `embrace-android-instrumentation-api`
4. Define schema attributes using `SchemaType` from `embrace-android-instrumentation-schema`
5. Register in the instrumentation registry
6. Add to `embrace-android-sdk/build.gradle.kts` as an `implementation` dependency
7. Add module to `settings.gradle.kts`
8. Add a `README.md` describing the module's purpose
9. Create a fakes module if needed for testing

### Adding a New Payload Model

1. Add to `embrace-android-payload` module
2. Use Moshi annotations: `@JsonClass(generateAdapter = true)` and `@Json(name = "...")`
3. Use `data class` with `val` properties (immutability enforced by detekt)
4. No business logic in payload classes (enforced by detekt)

### Writing Tests

1. Prefer fakes over mocks (check `embrace-test-fakes` first)
2. Use `lateinit var` + `@Before` setup pattern
3. Use descriptive backtick-quoted test names
4. For integration tests, use `SdkIntegrationTestRule`
5. Mark test classes as `internal`

---

## Important Constraints

- **Do NOT use `android.util.Pair`** - it's a forbidden import; use `kotlin.Pair`
- **Do NOT add business logic to payload data classes** - detekt enforces immutability and no functions
- **Do NOT use coroutines** - the SDK uses `BackgroundWorker` / `ScheduledExecutorService`
- **Do NOT bump Compose version past 1.0.5** - `getAllSemanticsNodes` signature changed in 1.6+, breaking backward compat
- **Some dependency versions are pinned** due to an unpatched AGP issue (see comments in `libs.versions.toml`)
- **Configuration cache** is enabled with `problems=fail` - all build logic must be compatible
- **Public API changes** require running `./gradlew apiDump` and committing the updated `.api` files

## Writing code for SDKs

- Prefer interfaces in public APIs over concrete symbols/constructors
- Minimize the public API surface area
- Use defensive programming and be paranoid with your error checking
- The SDK must not crash. When throwing exceptions make sure that something will catch it that isn't the library consumer.
