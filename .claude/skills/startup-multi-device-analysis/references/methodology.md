# Run shapes, comparison rules, and statistics

## Run shape

- **Default: 4 passes × 50 iterations per device.** Rationale:
  - 4 passes = an EVEN count (some devices exhibit a two-state fast/slow pass toggle that
    alternates per benchmark cycle — odd counts skew pooled stats), 4 independent
    first-post-install samples, 4 ambient-state draws.
  - 50 iterations = runway for churn-driven outliers: system-process GC compactions start
    firing after ~10–15 iterations of accumulated load and recur every ~15–20; short passes
    under-sample the most important outlier class.
- **Floor: 4 × 25** for targeted or time-constrained runs. Never fewer iterations or an odd
  pass count.
- Passes back-to-back within a campaign. Condition arms (hot/cool etc.) are separate
  campaigns with ≥2 h separation, not extra passes appended later — a topped-up pass after
  hours of idle is not representative of a contiguous campaign.

## Comparison rules

- **Pass states first.** Run hypothesis_tests per device before any comparison. If pass
  medians alternate two levels with the ratio fingerprint (pure-CPU sections ~1.0× between
  states, short block-resume sections ~2×), the device has a two-state toggle: compare only
  matching-state passes, and report the fast-state numbers as the device's honest baseline.
  (Known instance: Exynos 850 / Galaxy A14 — 18/18 passes alternating, robust to temperature,
  idle gaps, charging; largely a measurement-context phenomenon — see device-gotchas.)
- **Window sources must match** (emb-sdk-start slice vs composed fallback) — never compare
  across sources; analyze_startup/variance name the source.
- **iter000 is its own cohort** (fresh-install config fast path + install aftermath). Verify
  freshness per pass: if iter000's persisted-config-load is NOT fast (~2–5 ms vs the 7–37 ms
  cached mode, tier-dependent), app data survived a failed uninstall and the pass's
  first-launch sample is poisoned.
- **Same work check before device comparisons**: section shares (median section ÷ median
  window) should match across devices within a few points; the known-good profile is
  span-service-init 22–37%, otel-tracer-init 15–28%, persisted-config-load 18–35%,
  modules-init 83–90%. Divergent shares mean different code paths, not different hardware.

## Statistics

- **Tails first**: report p50, p90, p95, max, top-3 values, and the slow-iteration rate.
  Slow threshold is tier-relative: window − pass median > max(4 ms, 10% of pass median).
- **No p99 below ~500 samples** — at n≤200 the "p99" is one or two samples; say max/top-3
  instead.
- Expected extreme rates for calibration (this SDK, quiet bench): ~3–5% of iterations on
  mid/entry tier, ~30%+ on 1 GB Go-class, near-zero absolute damage on modern flagships
  (2022+: worst observed windows < entry-tier medians).
- Event-count sizing: to compare tails between two arms you want ≥5–10 extreme events per
  arm → n ≈ 100–200 per device×arm at mid-tier rates.
- Scheduling-table triage per iteration (from variance/analyze output): high wait% =
  contention; low wait but elevated Running = execution/IPC effect (pressure, thermal,
  placement); elevated D/io = storage. Classify before averaging.

## Ordering & counterbalancing (condition arms)

- Fixed arm order + back-to-back passes = monotonic self-heating and churn accumulation,
  which systematically advantages whichever arm runs first. Observed in practice: a
  fixed noaot→profile order inflated an apparent profile penalty from ~+23% (temperature-
  adjacent comparison) to +43% (headline medians) on a thermally-sensitive device.
- Controls, in order of strength: **counterbalance arm order (ABBA)** across passes;
  insert **silicon-temp-gated cooling gaps** between arms (wait for AP/skin to return to
  the pre-arm baseline); always log silicon temps per pass; and when order can't be
  counterbalanced, report the **boundary comparison** (last iterations of arm A vs first
  iterations of arm B — temperature-adjacent) alongside headline medians.
- A conclusion is order-robust only if the disadvantaged-arm-first ordering still shows it,
  or the boundary comparison preserves its sign.

## Telemetry verification channel

Verifying what the SDK *logged* (span attributes, section durations) is a separate problem from
measuring how long things took. Ranked by reliability, learned the hard way 2026-08-14:

1. **Verification tap (preferred).** A gated `SpanProcessor` registered by the app before
   `start()`, emitting chunked JSON to a logcat tag. Works on ANY build type (including the
   non-debuggable benchmark build), on locked/unattended devices, and regardless of session
   state; the sdk-init span arrives ~ms after init. Read it with one `adb logcat -d -s <TAG>`
   and wait for the batch's flush marker rather than sleeping a guessed interval.
   - It must be a **processor**, not an exporter: `DefaultSpanExporter` filters out `emb.private`
     spans and **sdk-init is private**, so no exporter can ever see it.
   - Register the KOTLIN-typed processor (`addSpanProcessor`) — it is invoked with full fidelity
     in both engine modes; the java-typed one goes silent if the KMP OTel SDK is enabled.
   - In perf-sensitive runs use a startup-scoped mode: allowlist the startup span names, capture
     immutable snapshots at `onEnd`, and defer all serialization/logging to one flush after the
     window closes — otherwise the verification itself perturbs what it measures.
2. **Cached payload pull** (`run-as … cat files/embrace_cache/*session*`): debuggable builds
   only, and needs a foreground user session — see device-gotchas.md. Use only interactively.
3. Trace slices: prove *timing*, never attribute *values*.

Grade attributes per iteration against that same iteration's trace (internal consistency), and
prefer checks that need no ground truth at all: `init-cpu-pct + init-run-delay-pct` must sum to
~100% minus blocked share, and that closure held on 24/24 iterations when the attributes were
verified.

## Temperature discipline

- Battery temperature understates silicon by 30 °C+ under load; log thermalservice AP /
  per-CPU sensors around every pass (fleet_campaign does).
- **Do not drive thermal band logic from `dumpsys battery`.** Two of four fleet devices are
  unusable that way: the Pixel 3 reports a *constant* 37.7 °C (its staircase never advanced,
  wasting a whole night), and a plugged-in device's warm idle floor can sit above a cool gate
  forever — cap cooling gates in minutes and allow per-device thresholds. Drive bands from
  thermalservice AP/skin sensors (Pixel 3 exposes 26, P7P 74), and treat the SDK's own
  `thermal-headroom-pct` as a cross-check: on the Pixel 3 it varied (46–87) while battery temp
  was pinned, i.e. it carried more information than the battery sensor.
- Reference magnitudes (2026-08-14, counterbalanced staircases, debug build — relative response
  only): **P7P +10% median from ≤33 °C to ≥38 °C**, arms agreeing within 1–4 ms per band, so
  "immune below ~35 °C, ~+6–10% by 38 °C".
- Devices can be thermally clean in paced 4×50 campaigns yet throttle under rapid-fire
  probing (launch loops without benchmark pacing) — check silicon temps whenever a probe
  loop replaces the harness.
- Heat effects can be invisible to cpufreq: thermal governors throttle DDR/bus/cache domains
  first on some SoCs (SD845 registers its devfreq as a cooling device). A monotonic
  window-vs-silicon-temp rise at constant delivered CPU clock is the signature.
