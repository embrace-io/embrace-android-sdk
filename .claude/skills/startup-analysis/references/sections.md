# SDK init sections: nesting, environments, and reference numbers

## Nesting and execution order (as of 9.2.0-SNAPSHOT, 2026-08)

Indentation = nesting; a parent's duration includes its children. Presentation in reports should
follow this order.

```
embrace-impl-init            class-load: EmbraceImpl construction (before the span window)
└─ bootstrapper-init         class-load: ModuleInitBootstrapper ctor default
sdk-start                    wraps the whole Embrace.start() call — the SDK-init window proxy
├─ modules-init              bootstrapper.init() — opens the SDK-init span window
│  ├─ persisted-config-load  cached-config read; most variable section
│  ├─ config-service-init    only on the no-supplier path (always on-device, never in harness)
│  ├─ span-service-init
│  │  └─ otel-tracer-init    first touch of the OTel SDK assembly (the real OTel cost)
│  ├─ essential-service-init
│  ├─ delivery-init
│  └─ payload-source-init
├─ post-init                 SdkInitActions.postInit()
└─ post-services-setup       tail of EmbraceImpl.start()
   └─ load-instrumentation
```

The exported `emb-embrace-init` span is tiled by `modules-init + post-init +
post-services-setup` (± ~1 ms of unattributed sliver); `sdk-start` wraps that interval plus
<1 ms of Embrace.start() overhead, and is what analyze_startup.py reports as the window
(falling back to the composed interval on SDKs without it). The class-load sections run before
the window opens.

## Where each section appears

| Environment | Sections present |
|---|---|
| Real device via `Embrace` entry point (this skill) | all of the above |
| Robolectric integration harness | all EXCEPT `embrace-impl-init`, `bootstrapper-init` (harness bypasses the entry point) and `config-service-init` (harness supplies a config service). Authoritative list: `SpanAssertions.expectedSdkInitSections` in embrace-android-otel-fakes |
| Public 9.0.0 | only `embrace-impl-init`, `bootstrapper-init`, `modules-init`, `config-service-init`, `span-service-init`, `otel-tracer-init`, `post-services-setup`; no duration attributes at all |

## Reference numbers (sanity check, not baselines)

Samsung Galaxy A14 (SM-A145M), Android 15, R8-minified ExampleApp, 20 cold starts, 2026-08-11,
9.2.0-SNAPSHOT, cool device: TTID median ~865 ms; SDK-init span median ~37 ms; biggest sections
`modules-init` ~34, `span-service-init` ~15.5, `persisted-config-load` ~10, `otel-tracer-init`
~12. Public 9.0.0 on the same device: span median ~55 ms at TTID ~857.

Back-to-back passes on the same device ALTERNATE fast/slow (~37→50→39→48→39→50 ms span over six
passes, 2026-08-11) at flat battery temperature — this is NOT thermal throttling. The inflation
is NOT uniform: pure-CPU sections (`otel-tracer-init`, `span-service-init`) stay ≤1.04×, while
short block-and-resume sections (`config-service-init`, `payload-source-init`, `post-init`,
`essential-service-init`, `delivery-init`) run ~2×. Judge regressions on fast-pass numbers.

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
