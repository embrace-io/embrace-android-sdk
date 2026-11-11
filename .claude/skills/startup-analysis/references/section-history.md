# SDK init sections at each release

Every version-specific rule for the startup analysis lives here. Read this file only for traces
from a released SDK, or to compare versions: SKILL.md and `sections.md` describe the SDK in this
repo, and nothing here applies to it.

Each release has a snapshot, newest first: its section tree, read with `scripts/section_tree.sql`
from a startup trace of the released SDK in ExampleApp; how to read its traces; and what changed
from the release before. When to add one is in `sections.md`, "Keeping these docs current".

## Reading a trace from a release

1. **Find its entry**: the newest entry at or before that version. Its "Reading its traces" notes
   say where the window comes from, which sections it records, and what to watch for.
   Its tree takes the place of the one in `sections.md`.
2. **Read the canonical rows against that entry.** `tools/startup analyze`'s canonical rows are
   the current SDK's. A row the release doesn't record reads "not in these traces".
   A row whose section was only renamed since is filled from the older name and labelled
   `(as <old name>)` (`OLDER_SECTION_NAMES` in the tool's `StartupReport.kt`).
   The release's own sections that are no longer canonical show under the other sections
   (`--all-sections` lists every one).
3. **When comparing two versions**, compare a section only if both record it with the same scope.
   The change lists between the two entries say which sections were renamed (comparable) and
   which were split, merged or re-scoped (not comparable one to one).
   Call out any drift, and lean on the window and TTID as the apples-to-apples numbers. TTID is
   comparable across every version; a window is comparable only with one from the same source,
   which the output names.
4. **Confirm which sections a version should emit** with
   `git grep -n "EmbTrace.trace" <version-tag> -- "*.kt"` — and beware pathspec globs: `*` does
   not cross directory separators, so use the bare `"*.kt"` form over module-scoped globs.

Trees use the conventions of `sections.md`: indentation = nesting; `●` = exported as a
`<name>-duration-ms` attribute on the `sdk-init` span; `○` = trace-only slice. Only the
trace-only slices that hold a recorded section, or that take a large share of the window, are
shown. `huc-init` is recorded only with HttpURLConnection capture on, which ExampleApp leaves off,
so it is in none of these traces.

## 9.4.0

```
● embrace-impl-init               class load of Embrace (SdkStateHolder assembly); runs BEFORE the window
○ sdk-start                       wraps SdkStateHolder.start(), the whole Embrace.start() call: the window
├─ ● modules-init                 builds the SDK instance; the span's start time is taken here
│  ├─ ○ config-load-services-init
│  │  ├─ ● persisted-config-init  cached-config read; the most variable section
│  │  └─ ● config-service-init
│  ├─ ● startup-classifier-init
│  ├─ ● process-state-tracker-init
│  ├─ ○ telemetry-destination-init
│  │  └─ ○ span-service-init
│  │     └─ ● otel-tracer-init    the OTel SDK assembly
│  ├─ ● instrumentation-args-init
│  ├─ ● payload-storage-service-init
│  ├─ ● resource-source-init
│  ├─ ● session-orchestrator-init
│  └─ ○ sdk-services-start
│     └─ ● current-session-part-span-initialize-service
├─ ● process-state-tracker-register
├─ ● setup-services
│  └─ ● start-first-session
├─ ● post-services-setup
│  ├─ ○ service-registration
│  ├─ ● load-instrumentation
│  └─ ● huc-init
└─ ○ startup-tracking
floating: ● key-value-store-init (under ordinal-store-init in the trace) and ● prefs-first-read (under user-service-init)
```

Reading its traces:
- Window: the `emb-sdk-start` slice.
- Recorded sections: the `●` rows above.

Changes from 9.3.1: 9.4 inlined the SDK's module classes, so most module-level sections became
sections for the services they built. Only the rows marked same or renamed compare one to one.

| 9.3.1 | 9.4.0 | |
|---|---|---|
| `embrace-impl-init` | `embrace-impl-init` | same name; in 9.3.1 it also held `bootstrapper-init` |
| `bootstrapper-init` | — | removed; the nearest is trace-only `pre-start-services-init` |
| `modules-init` | `modules-init` | same name and role: opens the span and builds the SDK |
| `persisted-config-load` | `persisted-config-init` | renamed |
| `config-service-init` | `config-service-init` | same |
| `span-service-init` | `span-service-init` (○) | no longer an attribute |
| `otel-tracer-init` | `otel-tracer-init` | same |
| `essential-service-init` | `process-state-tracker-init` and trace-only service slices | split |
| `instrumentation-init` | `instrumentation-args-init` | re-scoped |
| `delivery-init` | `payload-storage-service-init` and trace-only delivery slices | split |
| `payload-source-init` | `resource-source-init` and trace-only source slices | split |
| `user-session-orchestration-init` | `session-orchestrator-init` | re-scoped |
| — | `startup-classifier-init` | new |
| — | `current-session-part-span-initialize-service` | new |
| — | `process-state-tracker-register` | new |
| `post-init` | `setup-services` | renamed |
| `start-first-session` | `start-first-session` | same, now under `setup-services` |
| `post-services-setup`, `load-instrumentation`, `huc-init` | same | same |
| `key-value-store-init`, `prefs-first-read` | same | same, still floating |

## 9.3.1, 9.3.0 and 9.2.0

The three releases have the same tree and record the same sections.

```
● embrace-impl-init               class load of Embrace; runs BEFORE the window
└─ ● bootstrapper-init            ModuleInitBootstrapper construction
   ├─ ○ init-module
   └─ ○ otel-module
○ sdk-start                       wraps Embrace.start(): the window
├─ ● modules-init                 bootstrapper.init(); the span's start time is taken here
│  ├─ ● persisted-config-load     cached-config read
│  ├─ ○ config-init
│  │  └─ ● config-service-init
│  ├─ ○ sdk-disable-check
│  ├─ ● span-service-init
│  │  └─ ● otel-tracer-init       the OTel SDK assembly
│  ├─ ● essential-service-init
│  ├─ ● instrumentation-init
│  ├─ ● delivery-init
│  ├─ ● payload-source-init
│  └─ ● user-session-orchestration-init
├─ ● post-init                    cross-module wiring
│  └─ ● start-first-session
├─ ● post-services-setup
│  ├─ ○ service-registration
│  ├─ ● load-instrumentation
│  └─ ● huc-init
└─ ○ startup-tracking
floating: ● key-value-store-init (under instrumentation-init in the trace) and ● prefs-first-read (under user-session-orchestration-init)
```

Reading their traces:
- Window: the `emb-sdk-start` slice.
- Recorded sections: the `●` rows above.
- `bootstrapper-init` runs inside `embrace-impl-init`, so it is before the window as well.

Changes from 9.1.0:
- Duration attributes are new: the `●` sections above are exported on the `sdk-init` span.
- `emb-sdk-start` is new, wrapping the whole `Embrace.start()` call, with `modules-init`,
  `post-init`, `post-services-setup` and `startup-tracking` under it.
- Module sections take fixed names (`essential-service-init`, `delivery-init`, …). 9.1.0 named
  them after the module's class, which R8 minifies.
- New: `persisted-config-load`, `post-init` (now holding `start-first-session`),
  `load-instrumentation`, `key-value-store-init` and `prefs-first-read`. `event-service-init` is
  gone.

## 9.1.0

```
○ embrace-impl-init               class load of Embrace; runs BEFORE the window
└─ ○ bootstrapper-init
   ├─ ○ init-module
   └─ ○ otel-module
○ modules-init                    the window opens here
├─ ○ <module>-init                one per module, named after the module class, so minified in a release build (e.g. jf0-init)
│  └─ ○ config-service-init       under the config module's section
├─ ○ span-service-init
│  └─ ○ otel-tracer-init          the OTel SDK assembly
└─ ○ event-service-init
○ start-first-session             a sibling of modules-init, not nested in it
○ post-services-setup             the window closes here
├─ ○ service-registration
└─ ○ startup-tracking
```

Reading its traces:
- Window: there is no `emb-sdk-start`, so `tools/startup analyze` composes the window from the first
  `modules-init` start to the first `post-services-setup` end, the exported span's interval, and
  names it as the composed source. It doesn't compare with an `emb-sdk-start` window.
- Recorded sections: none, so the `sdk-init` span has no duration attributes. Every section is a
  trace-only slice, and the canonical rows that share a name are read from those slices.
- Module sections are named after the module's class, which R8 minifies, so their names depend
  on the build: identify a module by what runs inside it (`config-service-init` is in the config
  module's section), not by its name.
- `start-first-session` runs between `modules-init` and `post-services-setup`: inside the window,
  outside `modules-init`.

Changes from 9.0.0: the payload source slices (`session-payload-source`, `resource-source`,
`metadata-source`, `metadata-service-init`) moved from after `modules-init` into a module section
within it.

## 9.0.0

```
○ embrace-impl-init               class load of Embrace; runs BEFORE the window
└─ ○ bootstrapper-init
   ├─ ○ init-module
   └─ ○ otel-module
○ modules-init                    the window opens here
├─ ○ <module>-init                one per module, named after the module class, so minified in a release build (e.g. ob0-init)
│  └─ ○ config-service-init       under the config module's section
├─ ○ span-service-init
│  └─ ○ otel-tracer-init          the OTel SDK assembly
└─ ○ event-service-init
○ sessionEnvelopeSource           payload sources, siblings of modules-init
├─ ○ resource-source
└─ ○ session-payload-source
○ start-first-session
○ post-services-setup             the window closes here
├─ ○ service-registration
└─ ○ startup-tracking
```

Reading its traces:
- As 9.1.0: a composed window, no recorded sections, and module sections with minified names.
- The payload source slices (`sessionEnvelopeSource` and its children) and `start-first-session`
  run between `modules-init` and `post-services-setup`: inside the window, outside
  `modules-init`.
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
