# SDK init sections: nesting, environments, and reference numbers

## Nesting and execution order (released 9.2.0; the working tree matches)

Section names and nesting drift between SDK versions — treat this tree as the shape for 9.2.0
and confirm it against the version you are actually measuring (see SKILL.md "Handling missing
data"). Read from `EmbraceImpl.start()`, `ModuleInitBootstrapper.init()`, the property order of
`InitializedModuleGraph`, `SdkInitActions.postInit()`/`loadInstrumentation()`, and
`SessionOrchestratorImpl.transitionState()`; re-derive it from those when the version changes.

Indentation = nesting; a parent's duration includes its children. Presentation in reports should
follow this order. `●` = also exported as a `<name>-duration-ms` attribute on the `sdk-init` and
`emb-embrace-init` spans (9.2.0+, first occurrence per name, elapsedRealtime ms, snapshotted when
init completes); `○` = trace-only slice.

```
● embrace-impl-init          class load of Embrace - runs BEFORE the span opens; attribute only
└─ ● bootstrapper-init       ModuleInitBootstrapper construction
   ├─ ○ init-module
   └─ ○ otel-module          module shell only; the OTel engine is built in span-service-init

○ sdk-start                  wraps Embrace.start(); ≈ the exported emb-embrace-init span
├─ ● modules-init            bootstrapper.init(): the span's start stamp is taken here
│  ├─ ● persisted-config-load     cached-config read + decode; most variable section; bimodal (no file on first launch)
│  ├─ ○ workerthread-init         then the SharedPreferences prewarm is posted to a worker
│  ├─ ○ core-init
│  ├─ ○ config-init
│  │  ├─ ● config-service-init    schedules the background HTTP config fetch; on-device only (harness supplies one)
│  │  ├─ ○ sdk-disable-check ▸ behavior-check
│  │  └─ ● span-service-init
│  │     └─ ● otel-tracer-init    the OTel SDK assembly - the real OTel cost
│  ├─ ● essential-service-init    ▸ process-state-service-init, network-connectivity-service-init,
│  │                                 session-properties-init, user-service-init ▸ load-user-info-from-pref (○)
│  ├─ ○ storage-init
│  ├─ ● instrumentation-init
│  ├─ ○ feature-init
│  ├─ ○ data-capture-service-init
│  ├─ ● delivery-init
│  ├─ ○ thread-blockage-init
│  ├─ ● payload-source-init       ▸ session-payload-source, resource-source, deviceImpl, metadata-source,
│  │                                 metadata-service-init (○)
│  ├─ ○ log-init
│  └─ ● user-session-orchestration-init   loads (does not write) the previous user session's metadata
│     floating: ● key-value-store-init and ● prefs-first-read nest wherever the store is first touched,
│     on whichever thread got there first (the prewarm worker usually wins)
├─ ● post-init               cross-module wiring
│  └─ ● start-first-session  the INITIAL session-part transition (see below)
│     └─ ○ transition-state-start ▸ prepare-new-session, create-new-session, initiate-periodic-caching (○)
├─ ● post-services-setup
│  ├─ ○ service-registration
│  ├─ ● load-instrumentation
│  └─ ● huc-init             only when HttpUrlConnection capture is enabled (off in the benchmark app)
└─ ○ startup-tracking        after the durations are snapshotted - never an attribute; closes the span
```

Reading rules for the attributes: parents include children, so values never sum to the span
(`modules-init` alone is the large majority of it); the two class-load sections are reported on
the span but happen before its start stamp; the recorded children account for most but not all
of `modules-init`, the rest being the trace-only module constructions; the three top-level
sections tile the span up to a small sliver of call overhead; the two SharedPreferences sections
float. Measured per-section shares by device tier live in the published primer's "section tree"
table - treat them as the shape to expect, not as thresholds.

## What `start-first-session` does, and why production pays for it

It runs one of two paths, chosen by `SessionOrchestratorImpl.loadPersistedUserSession()` at
construction:

* **Restore** - a persisted user session exists and is within its inactivity timeout (default
  30 minutes) and max duration: the INITIAL transition creates no session part (background
  activity capture is off by default) and writes nothing. Sub-millisecond on every tier measured.
* **Create and persist** - no stored session, or inactive, or expired: a new unclassified user
  session is created, the USER_SESSION ordinal incremented, and the metadata serialized to JSON
  and written to the default SharedPreferences (buffered by `store.batch` and flushed at the end
  of the transition, then `apply()`), plus a 5-second background-startup timer scheduled. In
  9.2.0 the JSON write resolved its serializer at RUNTIME (a reified `serializer<Map<String,
  String>>()` inlined into core, where the serialization compiler plugin does not run), which on
  the first such call in the process loads kotlinx's builtin serializer table and runs
  reflection. The cost is dominated by class loading and cold code, so it is small on a
  `speed-profile` install and several times larger in `verify` state; the `apply()` can also
  block on the app-wide `QueuedWork` lock behind any other thread writing preferences,
  including the SDK's own workers. On mid-tier production hardware this path is a large share
  of `post-init` at the median.

Every benchmark method that relaunches within seconds takes the restore path. Production cold
starts are hours apart and take the create path. Use `coldStartupBaselineProfileNewUserSession`
(`pm clear` per iteration) or `coldStartupBaselineProfileExpiredUserSession` (session key
deleted, config kept) to measure the create path; see SKILL.md "User-session state arms".
Evidence for all of the above: `claude-output/2026-09-03-first-session-fix/RESULTS.md`.

Fixed after 9.2.0 by passing a static `MapSerializer` on the write path and deleting the reified
helpers; `startup-tools trace-health` flags a recurrence (a burst of ART class loads inside
`emb-start-first-session` on the init thread). The `init-compile-filter` attribute added at the
same time tells you which compile-state population a production span came from.

## Where each section appears

| Environment | Sections present |
|---|---|
| Real device via `Embrace` entry point (this skill) | all of the above; `huc-init` only with HUC capture on |
| Robolectric integration harness | all EXCEPT `embrace-impl-init`, `bootstrapper-init` (harness bypasses the entry point) and `config-service-init` (harness supplies a config service). Authoritative list: `SpanAssertions.expectedSdkInitSections` in embrace-android-otel-fakes |
| Public 9.0.0 / 9.1.0 | only `embrace-impl-init`, `bootstrapper-init`, `modules-init`, `config-service-init`, `span-service-init`, `otel-tracer-init`, `post-services-setup`; no duration attributes at all (they shipped in 9.2.0) |

## Establishing baselines (there are no portable reference numbers)

Absolute durations are a property of the device profile and the build, not of the SDK, and they
range over an order of magnitude across tiers. Do not carry a number in from another device,
another build type, or someone else's report. Instead, establish your own baseline on the device
and arm you are about to judge, and cite the summary `.txt` it came from:

1. Run the unmodified SDK version you want as the reference point, full run shape (see SKILL.md
   "Run shape policy"), and keep its summary file.
2. Record the device profile alongside it (api level, tier, vendor, SoC family) — a baseline
   without its profile is not reusable.
3. Judge later runs against that file, same arm, same build type, same device, **same
   user-session state arm**.

What IS portable is the *shape*: `modules-init` dominates the window, and the largest children are
normally the OTel-assembly and cached-config-read sections. A section that is suddenly a wholly
different share of the window than your own baseline is the signal — not any particular millisecond
count.

## Slow-pass signatures

Back-to-back passes on one device can ALTERNATE fast/slow even while battery temperature stays
flat — a two-state effect, not thermal throttling; do not reach for a thermal explanation until
you have checked silicon temperature (`dumpsys thermalservice`), not battery temperature. Use an
even pass count and judge regressions on
fast-state passes. The inflation is NOT uniform, which is how you recognise it: pure-CPU sections
(`otel-tracer-init`, `span-service-init`) barely move, while short block-and-resume sections
(`config-service-init`, `payload-source-init`, `post-init`, `essential-service-init`,
`delivery-init`) can roughly double. Check whether your device does this before trusting a
single-pass comparison; devices differ in whether, and how strongly, they show it. The cause is
install-time compile state, and it is real rather than an instrumentation artifact — mechanism,
the one-command detection, and how to handle it are in
`interpreting-results.md` → Install-time compile state.

Slow iterations/passes come in TWO trace signatures (analyze_startup.py's scheduling table
separates them; judge regressions only on iterations showing neither):
1. **Contention** — high main-thread wait (R/R+) inside the window, together with migration
   across many CPUs, versus low wait and few CPUs on a clean iteration. The usual competitors are
   install-triggered background work (system_server GC `HeapTaskDaemon`, store/package-manager
   activity) — each benchmark pass reinstalls the APK, so this is self-inflicted and expected to
   cluster early in a pass. To name the competitors, query `sched` joined to `thread`/`process`
   over the window, excluding the window's own utid.
2. **Slow execution** — near-zero wait but elevated Running time against your own fast-pass
   baseline: the same work simply consumes more CPU-time (core placement / clock state). Do not
   expect the traces to prove the clock story: cpufreq counters are far too sparse to resolve a
   window this short, and on devices with a homogeneous cluster topology every core reports the
   same ceiling anyway. Treat it as "environmental, cause unresolved at this layer" and escalate
   to the multi-device skill, which carries the per-iteration clock/thermal forensics.

A THIRD signature is easy to mistake for the first two: a single iteration whose whole excess
sits in one section with a burst of class-load slices on the init thread inside it. That is a
different code path, not a different environment - see "What `start-first-session` does" above
and interpreting-results.md "A rare lab iteration is a hypothesis about production".

Entry-tier devices show far heavier tails and more of both signatures than flagships; do not
assume a variance level you measured on one tier transfers to another.

If a fresh run diverges wildly from your baseline's shape (e.g. a section at 10× its usual share),
suspect setup problems before concluding regression: dry-run mode left on, wrong SDK version
resolved (check mavenLocal vs mavenCentral), wrong build type, or device under load.
