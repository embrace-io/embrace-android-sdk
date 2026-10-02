# embrace-analysis-local-refs

Sweeps a source tree for references to one machine or one author - home paths, addresses, device
serials, private artifact links, run-specific citations - things that are true where the code was
written and false everywhere else. The engine knows NO layout, not even this repository's own:
`scan` requires the roots to walk as a parameter, so the same checker can sweep any tree that
supplies its own. This repository's roots live in `embrace-analysis-cli` as `RepoLocalRefs.ROOTS`,
and the sweep over this repository itself runs from that module's tests, not this one's.

## Using it on its own

```
implementation(project(":embrace-analysis-local-refs"))
```

`LocalRefsCheck.scan` works on any repository, given its own `Roots` - not only this one:

```kotlin
import io.embrace.analysis.localrefs.LocalRefsCheck
import java.nio.file.Path

val roots = LocalRefsCheck.Roots(
    source = listOf("src/main", "docs"),
    data = listOf("fixtures"),
)
val report = LocalRefsCheck.scan(repo = Path.of("/path/to/some/other/repo"), roots = roots)
report.findings.forEach { println(it) }
```

## Public surface

| type | what it is for | note |
|---|---|---|
| `LocalRefsCheck` | the sweep engine, with no layout of its own | `scan`, `selfTest` (every pattern against its own positive/negative sample) |
| `LocalRefsCheck.Scope` | `SOURCE` (every pattern applies) vs `EVERYWHERE` (only personal patterns) | prose/code is held to every pattern; committed records may legitimately cite a real run |
| `LocalRefsCheck.LocalRef` | one pattern, with its regex and required positive/negative samples | a pattern cannot be added or edited without its own test moving too |
| `LocalRefsCheck.Roots` | the paths one repository supplies to `scan` | `scan` requires a `Roots`; this repository's own instance is `RepoLocalRefs.ROOTS` in `embrace-analysis-cli`, the one place its layout is written down |
| `LocalRefsCheck.Report` / `Finding` | the scan result | one `Finding` per hit, with file, line, pattern name and matched text |

## Depends on

- Nothing but the Kotlin standard library, by design: the engine takes its roots as a parameter and
  has no notion of any repository's layout.
- `embrace-analysis-test-fixtures` (`testImplementation`): fixtures for the checker's own tests.

## Tests

`./gradlew :embrace-analysis-local-refs:test` (`LocalRefsCheckTest`) runs `selfTest()` (every pattern
against its samples) plus scan-behaviour tests (the `local-refs:allow` marker, `--allow`, the
personal-token file, archive scanning), all against synthetic trees. No live gate; nothing here
depends on a device or a trace. The sweep over this repository itself - the one that actually fails
when a local reference lands in source - is `RepoLocalRefsTest` in `embrace-analysis-cli`, since that
is where this repository's `Roots` are defined.

## Design notes

- **This module is about *local references*, not retained objects or resource exhaustion.** "Local"
  means specific to one machine or author - a home path, a device serial, a run-specific citation -
  never the Android sense of a resource that was never released. Keep that other, unrelated word out
  of this module's code, tests and docs.
- **Personal tokens are git-ignored, patterns are checked in.** `PATTERNS` describes the *shape* of a
  local reference (a home path, a device-serial shape); `.local-refs-tokens` (or `LOCAL_REFS_TOKENS`)
  is one author's actual values and is never committed. This is the same split the production repo's
  checker uses.
- **Two scopes because evidence and instructions are held to different standards.** A dated run
  citation in a skill's prose is a defect (the reader has no such run); the same citation in a
  committed record is the record's whole point.
