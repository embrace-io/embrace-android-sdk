# SDK init sections at each release

Every version-specific rule for the startup analysis lives here. Read this file only for traces
from a released SDK, or to compare versions: SKILL.md and `sections.md` describe the SDK in this
repo, and nothing here applies to it.

Each release has an entry, newest first, saying how to read its traces.

## Reading a trace from a release

1. **Find its entry**: the newest entry at or before that version. Its "Reading its traces" notes
   say where the window comes from, which sections it records, and what to watch for.
2. **Read the canonical rows against that entry.** analyze_startup.py's canonical rows are the
   current SDK's. A row the release doesn't record reads "not instrumented in this SDK version".
   The release's own sections that are no longer canonical show under the other sections
   (`--all-sections` lists every one).
3. **When comparing two versions**, compare a section only if both record it with the same scope.
   Call out any drift, and lean on the window and TTID as the apples-to-apples numbers. TTID is
   comparable across every version; a window is comparable only with one from the same source,
   which the output names.
4. **Confirm which sections a version should emit** with
   `git grep -n "EmbTrace.trace" <version-tag> -- "*.kt"` — and beware pathspec globs: `*` does
   not cross directory separators, so use the bare `"*.kt"` form over module-scoped globs.

## 9.2.0

Reading its traces:
- Window: the `emb-sdk-start` slice.
- `persisted-config-load` is the section now named `persisted-config-init`.

## 9.0.0

Reading its traces:
- Window: there is no `emb-sdk-start`, so analyze_startup.py composes the window from the first
  `modules-init` start to the first `post-services-setup` end, the exported span's interval, and
  names it as the composed source. It doesn't compare with an `emb-sdk-start` window.
- Recorded sections: none, so the `sdk-init` span has no duration attributes. Its traces hold only
  `embrace-impl-init`, `bootstrapper-init`, `modules-init`, `config-service-init`,
  `span-service-init`, `otel-tracer-init` and `post-services-setup`.
- `post-services-setup` also covers what later releases split into `load-instrumentation` and
  `setup-services`, and `config-service-init` covers a different construction path, so neither
  compares one to one with a later release.

## Reference numbers from a 9.2.0 build

Raw measurements, with the section names of that build. They show the expected shape of a run,
not baselines.

Samsung Galaxy A14 (SM-A145M), Android 15, R8-minified ExampleApp, 20 cold starts, 2026-08-11,
9.2.0-SNAPSHOT, cool device: TTID median ~865 ms; SDK-init span median ~37 ms; biggest sections
`modules-init` ~34, `span-service-init` ~15.5, `persisted-config-load` ~10, `otel-tracer-init`
~12. Public 9.0.0 on the same device: span median ~55 ms at TTID ~857.

Back-to-back passes on the same device alternated fast/slow (~37→50→39→48→39→50 ms span over six
passes, 2026-08-11) at flat battery temperature. Pure-CPU sections (`otel-tracer-init`,
`span-service-init`) stayed ≤1.04×, while short block-and-resume sections
(`config-service-init`, `payload-source-init`, `post-init`, `essential-service-init`,
`delivery-init`) ran ~2×.
