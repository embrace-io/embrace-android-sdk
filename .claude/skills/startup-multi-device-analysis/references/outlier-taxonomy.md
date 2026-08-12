# Outlier taxonomy: classes, trace signatures, per-tier casts, prod telemetry mapping

Established across 2,000+ instrumented cold starts on four devices (Tensor G2 / Exynos 850 /
SD845 / MT6739, Android 10–15, 2026-08). The window is memory-bound work; every class below
degrades effective memory-system speed or blocks the main thread. Detailed evidence:
`claude-output/startup-external-factors-summary.html` and the testing-log artifact.

## Class A — concurrent system-process CPU bursts (the extreme-outlier class)

- **Signature**: window Running-time inflated, main-thread wait < a few ms, IO flat; the
  factor catalogue shows another process at ≥0.5 CPU-ms per window-ms (baseline ~0.2–0.35).
  Damage +25–60% per window; PROVEN CAUSAL (induced churn: +40% entry-tier, +24% flagship,
  instant recovery).
- **Casts by device personality**: system_server ART heap-compaction GC (Samsung entry-tier;
  GC overlap >5 ms was sufficient for slowness in 13/13 cases — but partial correlation shows
  it acts purely via the CPU it burns); install-time dex2oat storms + Play/GMS (Pixels;
  dex2oat observed at 150–413 ms CPU inside single windows); GMS/sync adapters + kswapd
  (Go-tier). It is the CONCURRENT CPU, not who burns it.
- **Multi-device use**: same class, different casts across devices = environmental, not SDK.

## Class B — main-thread flash IO stalls

- **Signature**: D-state/io_wait 4–60+ ms in-window (baseline ~2 ms); worst cases co-occur
  with concurrent flash writers (installs) and may include dex re-verification (VerifyClass
  slices). First-class on slow eMMC (r ≈ 0.7 on Go-tier), modest on UFS.
- Function-level attribution usually unavailable unrooted (kernel symbols restricted).

## Class C — scheduler core placement (heterogeneous silicon)

- **Signature**: whole window resident on lower-class cores; per-CPU residency table shows
  it directly. Rare-but-catastrophic on big.LITTLE (~2× when startup lands on little cores);
  mild-but-common on multi-domain homogeneous parts (+2–4 ms); minor on modern 3-tier
  flagships (mid-core placement ≈ +2–3 ms). Requires the device's cluster map (probe).
- On homogeneous-core devices a residual placement correlation can exist at provably equal
  clocks — mechanism unresolved (IRQ locality suspected); do not over-attribute.

## Class D — memory pressure (low-RAM tier only)

- **Signature**: the app's OWN GC slices 60–100 ms inside windows, kswapd active as a
  competitor, MemAvailable negatively correlated. Effectively absent above ~2 GB RAM.

## Class E — install aftermath (deterministic first-launch trigger of Class A)

- **Signature**: iter000/001 run with 2–3× baseline concurrent CPU; competitors are the
  install pipeline itself (Play Store 55–80 ms, installd 17–43 ms, artd ~50 ms,
  system_server bursts, PACKAGE_ADDED receivers). Settles by iteration ~2–3. Also visible in
  raw am-start launches decaying over the first minute post-install.
- Prod: first-launch cohorts are outlier-enriched and per-app amplified (the app's own
  first-run work joins the burst). Segment version_startup_counter == 1 before any outlier
  analysis; note the config fast path partially masks the damage in window terms.

## Non-classes (exonerated — do not chase)

- **CPU clock per se**: pinned at max under benchmark load on every non-thermal device
  measured; where clocks genuinely move (older SoCs, transient dips, post-idle ramp) the
  factor catalogue captures it via eff_mhz.
- **Charging state**: tested directly, null at matched temperature.
- **Thermal on modern flagships** in the ≤35 °C range: null (Tensor G2 identical hot/cool).
  On older/hotter silicon heat is real but acts via the memory subsystem (see methodology).

## Prod telemetry mapping (no perfetto)

Per class, the cheap in-process proxy (full spec + costs:
`claude-output/startup-outlier-mitigation-telemetry.html`): A → cpu_pressure (/proc/stat
first-line minus /proc/self/stat, per window-ms) ± PSI; B → majflt + /proc/self/io deltas;
C → sched_getcpu at section boundaries + probed core-class map; D → own-GC runtime-stat
deltas + ActivityManager memory info (post-window); E → version_startup_counter == 1 +
seconds-since-install; thermal → PowerManager thermal status/headroom (post-window);
plus init_cpu_ms vs init_wall_ms (currentThreadTimeMillis) to split ran-slow from
was-blocked. Validate any implementation against bench ground truth (labeled outliers from
this skill's factor catalogue) before trusting prod classifications.
