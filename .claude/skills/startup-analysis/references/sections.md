# SDK init sections: nesting, environments, and reference numbers

## Nesting and execution order (current working tree, 9.2.0-SNAPSHOT)

Section names and nesting drift between SDK versions — treat this tree as the shape for the
working tree and confirm it against the version you are actually measuring (see SKILL.md
"Handling missing data").

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

## Establishing baselines (there are no portable reference numbers)

Absolute durations are a property of the device profile and the build, not of the SDK, and they
range over an order of magnitude across tiers. Do not carry a number in from another device,
another build type, or someone else's report. Instead, establish your own baseline on the device
and arm you are about to judge, and cite the summary `.txt` it came from:

1. Run the unmodified SDK version you want as the reference point, full run shape (see SKILL.md
   "Run shape policy"), and keep its summary file.
2. Record the device profile alongside it (api level, tier, vendor, SoC family) — a baseline
   without its profile is not reusable.
3. Judge later runs against that file, same arm, same build type, same device.

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

Entry-tier devices show far heavier tails and more of both signatures than flagships; do not
assume a variance level you measured on one tier transfers to another.

If a fresh run diverges wildly from your baseline's shape (e.g. a section at 10× its usual share),
suspect setup problems before concluding regression: dry-run mode left on, wrong SDK version
resolved (check mavenLocal vs mavenCentral), wrong build type, or device under load.
