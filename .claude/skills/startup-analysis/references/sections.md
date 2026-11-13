# SDK init sections: nesting, environments, and slow passes

The sections the SDK in this repo emits. Only for traces from a released SDK, or to compare
versions, read `section-history.md`, which holds every version-specific rule.

## Keeping these docs current

- **When a change on main adds, removes, renames or re-nests an `EmbTrace` section** (or changes
  whether it records a duration), update the tree below in the same change. If a renamed section
  does the same work as before, also add it to `OLDER_SECTION_NAMES` in
  `embrace-analysis-cli/src/main/kotlin/io/embrace/analysis/startup/StartupReport.kt`, so traces
  from older SDKs still fill its row.
- **After each release**, add a snapshot of that release to the top of `section-history.md`: the
  tree read with `scripts/section_tree.sql` from a startup trace of the released SDK, and what
  changed from the release before. Then check this file against the same trace, and update the
  "Last checked" line below.

## Nesting and execution order

Last checked against a 9.5.0 startup trace, read with `scripts/section_tree.sql`.

Indentation = nesting; a parent's duration includes its children. Presentation in reports should
follow this order. `●` = also exported as a `<name>-duration-ms` attribute on the `sdk-init`
span; `○` = trace-only slice. Only the trace-only slices that hold a recorded section, or that
take a large share of the window, are shown; `--all-sections` lists every one.

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
│  │     └─ ● otel-tracer-init    the OTel SDK assembly: the real OTel cost
│  ├─ ● instrumentation-args-init
│  ├─ ● payload-storage-service-init
│  ├─ ● resource-source-init
│  ├─ ● session-orchestrator-init loads (does not write) the previous user session
│  └─ ○ sdk-services-start
│     └─ ● current-session-part-span-initialize-service
├─ ● process-state-tracker-register
├─ ● setup-services               wires the services together and opens the first session part
│  └─ ● start-first-session       the initial session-part transition
├─ ● post-services-setup
│  ├─ ○ service-registration
│  ├─ ● load-instrumentation
│  └─ ● huc-init                  only when HttpURLConnection capture is enabled (off in ExampleApp)
└─ ○ startup-tracking             after the durations are recorded, so never an attribute
floating: ● key-value-store-init and ● prefs-first-read nest wherever the store is first touched
```

The recorded sections directly under `sdk-start` (`modules-init`,
`process-state-tracker-register`, `setup-services` and `post-services-setup`) tile the exported
span, up to a small sliver of call overhead. `sdk-start` wraps that interval plus well under 1 ms
of `Embrace.start()` overhead, and is what `tools/startup analyze` reports as the window. The
class-load section runs before the window opens. Much SDK work runs on worker threads during and
right after the window (native crash handler install, payload resurrection, the startup trace's
recording); those slices show under "other emb-* sections" and are not part of the window.

## Where each section appears

| Environment | Sections present |
|---|---|
| Real device via `Embrace` entry point (this skill) | all of the above; `huc-init` only with HttpURLConnection capture on |
| Robolectric integration harness | all EXCEPT `embrace-impl-init` (harness bypasses the entry point). Authoritative list: `SpanAssertions.expectedSdkInitSections` in embrace-android-otel-fakes |

## Slow passes

Back-to-back passes on the same device can alternate fast/slow at flat battery temperature; this
is not thermal throttling. The inflation is not uniform: pure-CPU sections such as
`otel-tracer-init` barely move, while short block-and-resume sections can roughly double. Judge
regressions on fast-pass numbers.

Slow iterations/passes come in TWO trace signatures (`tools/startup analyze`'s scheduling table
separates them; judge regressions only on iterations showing neither):
1. **Contention** — high main-thread wait (R/R+) inside the window plus migration across many
   CPUs. Observed pass 4 slow iteration: 14.8 ms wait / 7 CPUs vs 0.2 ms / 2 CPUs when fast;
   competitors were install-triggered background work (system_server GC `HeapTaskDaemon`, Play
   Store `stall_source.db` — each benchmark pass reinstalls the APK).
2. **Slow execution** — near-zero wait but elevated Running time (pass 5: 43–59 ms running vs
   ~31 ms on fast passes, wait ~1%): the same work simply consumes more CPU-time — core
   placement/clocks, uncapturable via cpufreq counters in these traces (too sparse in a ~50 ms
   window; all 8 cores report the same 2.0 GHz ceiling on the A14).

If a fresh run diverges wildly from these shapes (e.g. a section at 10× its usual share), suspect
setup problems before concluding regression: dry-run mode left on, wrong SDK version resolved
(check mavenLocal vs mavenCentral), or device under load.
