# SDK init sections: nesting, environments, and slow passes

The sections the SDK in this repo emits. Only for traces from a released SDK, or to compare
versions, read `section-history.md`, which holds every version-specific rule.

## Nesting and execution order (as of 9.2.0-SNAPSHOT, 2026-08)

Indentation = nesting; a parent's duration includes its children. Presentation in reports should
follow this order.

```
embrace-impl-init            class-load: EmbraceImpl construction (before the span window)
sdk-start                    wraps the whole Embrace.start() call — the SDK-init window proxy
├─ modules-init              createRealSdkInstance() — opens the SDK-init span window
│  ├─ persisted-config-init  cached-config read; most variable section
│  ├─ config-service-init    ConfigService construction (recorded in the harness too)
│  ├─ startup-classifier-init
│  ├─ otel-tracer-init             first touch of the OTel SDK assembly (the real OTel cost), reached via
│  │                               telemetry-destination-init → span-service-init
│  ├─ process-state-tracker-init
│  ├─ instrumentation-args-init
│  ├─ payload-storage-service-init
│  ├─ resource-source-init
│  ├─ session-orchestrator-init
│  └─ current-session-part-span-initialize-service
├─ setup-services            RealSdkInstance.setupServices()
└─ post-services-setup       tail of EmbraceImpl.start()
   └─ load-instrumentation
```

The exported `emb-embrace-init` span is tiled by `modules-init + setup-services +
post-services-setup` (± ~1 ms of unattributed sliver); `sdk-start` wraps that interval plus
<1 ms of Embrace.start() overhead, and is what analyze_startup.py reports as the window. The
class-load sections run before the window opens.

## Where each section appears

| Environment | Sections present |
|---|---|
| Real device via `Embrace` entry point (this skill) | all of the above |
| Robolectric integration harness | all EXCEPT `embrace-impl-init` (harness bypasses the entry point). Authoritative list: `SpanAssertions.expectedSdkInitSections` in embrace-android-otel-fakes |

## Slow passes

Back-to-back passes on the same device can alternate fast/slow at flat battery temperature; this
is not thermal throttling. The inflation is not uniform: pure-CPU sections such as
`otel-tracer-init` barely move, while short block-and-resume sections can roughly double. Judge
regressions on fast-pass numbers.

Slow iterations/passes come in TWO trace signatures (analyze_startup.py's scheduling table
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
