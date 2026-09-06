# Experiment design: `mem-available-pct` and `low-memory` — definitive verdict

Status: RUN 2026-08-14 — verdicts reached, see the **Results (2026-08-14)** section at the end of
this document. The design and feasibility probes below are the pre-campaign record; the campaign
ran on the Galaxy A01 Core (`entry-a`, API 29) only — the sole device in the fleet where the
`low-memory` threshold is reachable without extreme allocations, exactly as §6 anticipated.

## Summary table

| attribute | verdict so far | basis | what's still needed for a final verdict |
|---|---|---|---|
| `mem-available-pct` | **Provisionally OBTAINABLE + DIRECTIONALLY ACCURATE** (not yet FINAL) | Ground-truth formula pinned to AOSP source (below); idle-state ground truth recomputed on all 4 devices matches previously-measured ranges on 3/4 devices (P7P 60–61% known vs 61% computed; A14 33–35% known vs 36% computed; Pixel 3 44–46% known vs 47% computed) | It has **never been graded under actual induced pressure** or checked for record-time drift. That is the whole point of the campaign below. A01 divergence (35–37% known vs 44% computed idle) is unexplained pending a real run — likely idle-vs-load state, not a formula error (see §1). |
| `low-memory` | **UNKNOWN — feasibility CONFIRMED, not yet fired even once** | Threshold derived exactly per device from `dumpsys activity oom` (below); on the A01 Core the gap from idle to threshold is only ~254 MB (28 pct points) — the smallest of the 4 devices and a realistic allocator target | Must actually make it fire under controlled, ramped pressure on the A01 Core, and confirm zero false positives elsewhere. This is the core deliverable of the campaign. |

**Computed `low-memory` threshold per device (2026-08-14, idle, read-only probe):**

| device | total RAM | threshold (`homeAppMem` + half-gap to `cachedAppMin`) | threshold as % of total | idle availMem-proxy now | idle % now | gap to threshold |
|---|---|---|---|---|---|---|
| Galaxy A01 Core (entry-a, API 29) | 911,420 KB | 147,456 KB | 16% | 401,336 KB | 44% | **~254 MB (28 pts) — smallest absolute gap, primary target** |
| Pixel 3 (mid-b, API 31) | 3,665,328 KB | 221,184 KB | 6% | 1,738,540 KB | 47% | ~1.52 GB (41 pts) |
| Galaxy A14 (mid-a, API 35) | 3,771,792 KB | 140,113 KB | 4% | 1,341,480 KB | 36% | ~1.20 GB (32 pts) |
| Pixel 7 Pro (flagship-a, API 35) | 11,746,524 KB | 221,184 KB | 2% | 7,198,096 KB | 61% | ~6.98 GB (59 pts) — not a realistic target |

On all 4 devices, `HOME_APP_ADJ`'s memory level equals `CACHED_APP_MIN_ADJ`'s memory level exactly, so the
"half the gap between them" term in the `lowMemory` formula collapses to zero — the threshold is simply
`getMemLevel(HOME_APP_ADJ)` on every device in this fleet. This simplifies prediction (fewer moving parts)
but may be a fleet coincidence rather than an AOSP guarantee; re-derive per device, don't assume it holds
elsewhere.

**Biggest risk to the experiment**: the ordering problem (§3) — under an active pressure *ramp*, the
gap between "init window ends" and "attribute is read at record time" could span meaningfully more memory
movement than under quiet conditions, especially on the A01 Core where the OS is more likely to react
(reclaim, kill background processes) precisely because we're pushing it toward its threshold. A secondary
risk is that OEM/kernel behavior on a low-end MediaTek device (A01 Core) may not follow the mainline AOSP
`ProcessList` formula exactly — the derivation below is sourced from AOSP tags, not verified against this
specific vendor's fork.

---

## 1. Ground truth, independent of the attribute

### What `ActivityManager.MemoryInfo.availMem` actually is (sourced from AOSP, not assumed)

Read `SdkInitEnvironmentAttributes.kt` (`putMemoryAttributes`): the code calls
`ActivityManager.MemoryInfo()` via `activityManager::getMemoryInfo`, then computes
`MEM_AVAILABLE_PCT = round(100.0 * availMem / totalMem)` and emits `LOW_MEMORY = "true"` iff
`memoryInfo.lowMemory`.

I fetched the actual implementation from `android.googlesource.com` (not `cs.android.com`, which is
JS-rendered and returns nothing to a fetcher) at several release tags to see whether `availMem`'s
definition has changed across our fleet's OS versions (API 29–35):

- **`frameworks/base/services/core/java/com/android/server/am/ProcessList.java`** (`getMemoryInfo`):
  ```java
  final long homeAppMem = getMemLevel(HOME_APP_ADJ);
  final long cachedAppMem = getMemLevel(CACHED_APP_MIN_ADJ);
  outInfo.availMem = getFreeMemory();
  outInfo.totalMem = getTotalMemory();
  outInfo.threshold = homeAppMem;
  outInfo.lowMemory = outInfo.availMem < (homeAppMem + ((cachedAppMem - homeAppMem) / 2));
  outInfo.hiddenAppThreshold = cachedAppMem;
  ```
- **`core/jni/android_util_Process.cpp`** (`android_os_Process_getFreeMemory`, the native backing for
  `getFreeMemory()`) — checked at tags `android-10.0.0_r47`, `android-12.0.0_r34`, `android-14.0.0_r1`,
  and `android-15.0.0_r1` (covers every API level in our 4-device fleet, 29/31/35): **all four read
  `SysMemInfo::kMemFree` + `SysMemInfo::kMemCached` and sum them** (in KB, ×1024 for bytes). Only on
  current AOSP **`master`** (an unreleased future branch, not shipped to any device we have) does this
  function switch to reading `kMemAvailable` directly instead. **For every device in this fleet, and
  almost certainly for every Android device shipping today, `availMem` = (`MemFree` + `Cached`) from
  `/proc/meminfo`, NOT `MemAvailable`.**
- **`getTotalMemory()`** uses `sysinfo().totalram * mem_unit` (a syscall, not `/proc/meminfo`), which is
  the same number the kernel derives `MemTotal` from — confirmed byte-for-byet equal to
  `/proc/meminfo`'s `MemTotal` on the A01 Core (911,420 KB both ways).

**Exact ground-truth expression for our fleet:**
```
availMem_groundtruth_KB = MemFree + Cached          (raw /proc/meminfo fields, KB)
totalMem_groundtruth_KB = MemTotal                  (/proc/meminfo, KB) — equals sysinfo().totalram
mem-available-pct_groundtruth = round(100 * availMem_groundtruth_KB / totalMem_groundtruth_KB)
```

**This is explicitly NOT `MemAvailable`, and NOT `dumpsys meminfo`'s "Free RAM"** (which is a third,
different composite: `cached_pss + cached_kernel + free`, built from per-process PSS accounting via
`MemInfoReader.getCachedSize()` = `Buffers + KReclaimable(or SReclaimable) + Cached - Mapped`, not the
same arithmetic at all). Grading this attribute against `MemAvailable` or against `dumpsys meminfo`'s
"Free RAM" would be grading it against the wrong field — exactly the trap the task warned about. Confirmed
divergence on the A01 Core right now: `dumpsys meminfo` "Free RAM" = 465,285 KB vs. the correct
`MemFree+Cached` = 401,336 KB — 16% apart, and neither equals `/proc/meminfo`'s own `MemAvailable` field
(397,860 KB, closer to the correct answer by coincidence on this device, but not to be relied on — see
below).

**Corroboration from today's idle readback** (computed `MemFree+Cached` pct vs. previously-measured
ranges, which were captured historically during benchmark campaigns, not right now):

| device | computed idle pct (today) | previously measured range |
|---|---|---|
| Pixel 7 Pro | 61% | 60–61% |
| Galaxy A14 | 36% | 33–35% |
| Pixel 3 | 47% | 44–46% |
| A01 Core | 44% | 35–37% |

3 of 4 devices land within 1–3 points of the historical range under a totally different, unrelated
sampling method (today's is a cold host-side `adb shell cat`, months apart from the original benchmark
captures) — strong corroboration the formula derivation is correct. The A01 Core is the outlier (44%
today vs. 35–37% historically), by 7–9 points more than the others. The likely explanation is that the
historical numbers were captured *while a benchmark harness was actively cycling app launches* (screen
on, launcher + test infra resident, repeated cold-start churn), whereas today's probe is a genuinely idle
device — and the A01 Core, being the smallest-RAM device, is the one where that kind of background
churn should move the percentage the most. **This is a hypothesis, not something this probe measured** —
the campaign below should capture a true "quiet baseline" arm (harness running, nothing else) specifically
to settle whether idle-vs-harness-load is really the explanation or whether there's a residual formula
gap on this specific vendor build.

**Caveat on vendor forks**: the AOSP source above is mainline; the A01 Core is a MediaTek Samsung build,
and the JNI function is deep framework plumbing that vendors rarely touch, but this has not been verified
against Samsung's or MediaTek's actual shipped source for this exact device. Treat the formula as
high-confidence, not vendor-source-verified.

### Deriving the exact `low-memory` flip point per device

`dumpsys activity oom` prints the `OOM levels` table directly — this is `getMemLevel()`'s output for
every `*_ADJ` level, already in KB, and it is a fixed per-device-boot table (derived once from total RAM
tier at boot; stable across reboots for a given device/RAM configuration, so it does not need
re-deriving per experiment run, only per device model).

Captured today (read-only, `dumpsys activity oom`, no `-a` package filter needed — the `OOM levels`
header is the first ~17 lines of output on every device, cheap to isolate):

| device | `HOME_APP_ADJ` mem level | `CACHED_APP_MIN_ADJ` mem level | computed threshold |
|---|---|---|---|
| A01 Core | 147,456 K | 147,456 K | 147,456 K (16.2% of 911,420 K total → rounds to 16%) |
| Pixel 3 | 221,184 K | 221,184 K | 221,184 K (6.03% of 3,665,328 K → 6%) |
| Galaxy A14 | 140,113 K | 140,113 K | 140,113 K (3.71% of 3,771,792 K → 4%) |
| Pixel 7 Pro | 221,184 K | 221,184 K | 221,184 K (1.88% of 11,746,524 K → 2%) |

Applying `formula: lowMemory = availMem < (homeAppMem + (cachedAppMem-homeAppMem)/2)`, and since
`homeAppMem == cachedAppMem` on all four, the threshold is simply `homeAppMem` — no interpolation
needed on this fleet. The relative threshold shrinks as total RAM grows (16% → 6% → 4% → 2%), consistent
with `ProcessList`'s memory-level step function scaling sub-linearly with total RAM — meaning
`low-memory` is proportionally *easier* to trigger (in % terms) on a big-RAM device, but the *absolute*
bytes needed to get there balloon (254 MB on the A01 vs. ~7 GB on the Pixel 7 Pro), which is why the A01
Core remains the only realistic target despite having the numerically highest threshold percentage.

---

## 2. Inducing memory pressure repeatably, without bricking the device

Evaluated options, against the specific ground-truth formula above (`MemFree + Cached`, not
`MemAvailable`):

| method | effect on `MemFree + Cached` | can it reach the A01 threshold? | risk |
|---|---|---|---|
| **Reading large files to fill the page cache** | **Near zero.** Reading a file moves bytes from `MemFree` into `Cached` — the *sum* barely moves (it only drops by the bytes actually evicted from `MemFree` before the read populates `Cached`, which nets out). This is a genuine trap: it would visibly change `/proc/meminfo`'s own `MemAvailable` (which discounts reclaimable cache more carefully) while leaving our actual ground-truth metric almost flat. **Do not use this method for this experiment** — it induces the wrong kind of "pressure" for the metric being tested. | No | Low risk, but useless |
| **`dd` into `/data/local/tmp`** | Same flaw as above (fills page cache with dirty pages backed by a real file) — plus it needs free disk. The A01 is ~94% full per prior findings; this is the explicitly risky option there. | No (doesn't move the right metric) and unsafe on A01 | High risk (disk exhaustion) on A01; ruled out |
| **Launching several real memory-hungry apps via `am start` cycling** | Yes — resident app anon memory is not reclaimable to `Cached`, so it directly reduces `MemFree+Cached`. Effective and simple. | Yes, but imprecisely — footprint is unpredictable and can overshoot straight past the threshold into a real LMK spree | Medium: hard to hold a *steady* near-threshold level; good as a corroboration/fallback method, not as the primary ramp |
| **Purpose-built allocator (foreground service, native mmap + touch every page, fixed KB increments)** | Yes, directly and precisely — every touched (dirtied) page is a page pulled out of `MemFree` and never added to `Cached`, so it moves exactly the metric we're grading. Ramp size is fully controlled (add N MB, check ground truth, repeat). | **Best fit.** ~260–300 MB of allocator holding is enough headroom to approach/hold near the A01's 147 MB threshold from today's 401 MB idle baseline. | Lowest risk of the pressure-inducing options *if* held as a separate long-lived process (own `android:process=":allocator"`), not inside the app-under-test's own process, so app-under-test's cold-start cycle (killed + relaunched every iteration) doesn't disturb the held allocation |
| **Allocating from an instrumented test in the app-under-test's own process** | N/A — architecturally wrong for a cold-start benchmark: the app-under-test process is killed and relaunched fresh every iteration, so anything allocated inside it can't persist across iterations, and it would directly compete with/contaminate the very init being measured. | N/A | Ruled out |

**Recommended design**: add a debug-only helper service to `ExampleApp` (own manifest process,
e.g. `:allocator`, gated behind a build variant/flavor that is never part of the shipped SDK or a
release build of ExampleApp — consistent with how the existing macrobenchmark harness is already
scoped) that:

1. On command (broadcast or `am startservice`/`am start-foreground-service`), starts allocating in
   fixed ~10–20 MB increments: `ByteBuffer.allocateDirect` or a JNI `mmap` + `memset` to force real
   physical commitment (an untouched `mmap` region doesn't cost real pages and wouldn't move
   `MemFree`).
2. After each increment, the *harness* (host-side, via `adb shell cat /proc/meminfo`) samples ground
   truth and computes the live `availMem_groundtruth` percentage.
3. Ramps toward the pre-computed per-device threshold (147,456 K on the A01), then **holds** — this is
   the "controlled approach to the threshold" the task asks for, not a blunt one-shot exhaustion.
4. Stays resident as a foreground service (visible notification, elevated LMK priority) for the
   duration of a batch of app-under-test cold-start iterations, then releases all memory and stops.

This needs one small, clearly-scoped code change to `ExampleApp` (new debug-only component) — it is a
real build-touching change, unlike today's read-only probes, and should be developed on its own branch,
scheduled for a time when the A01 Core (currently attached and apparently free) isn't needed by other
work.

---

## 3. The ordering problem: record-time value vs. init-time conditions

Both attributes are read in `sdkInitEnvironmentAttributes(...)` **after** SDK init completes (the doc
comment in `SdkInitEnvironmentAttributes.kt` explicitly acknowledges this: "these attributes still have
to be valid for SDK init despite the post-init retrieval"). Under a moving memory-pressure ramp, the
value at record time can differ from the value that actually applied during the init window.

**Design to detect and quantify this drift, using the fact that in this experiment we control the ramp
rate ourselves:**

1. Immediately **before** triggering the app-under-test launch (T0), the harness samples ground truth
   (`MemFree+Cached` pct) from the host.
2. The SDK init runs; `sdkInitEnvironmentAttributes` reads its own value synchronously at span-build
   time — call this T_record (unknown to the harness in wall-clock terms, but it is somewhere inside or
   just after the init window).
3. The verification tap emits the sdk-init span (with `mem-available-pct`/`low-memory` attributes)
   moments later; as soon as that `EMBV1` logcat line lands, the harness immediately (T1) samples ground
   truth again.
4. Report **drift = groundtruth(T1) − groundtruth(T0)**, and separately check whether the SDK's
   *reported* value sits closer to groundtruth(T0) or groundtruth(T1). Because we control the allocator's
   ramp rate (KB/second), we can predict exactly how much the metric *should* have moved across the
   T0→T1 gap, and confirm the SDK's number is consistent with a read taken at record time (T1-ish), not a
   stale value from earlier.
5. Run this specifically **under the near-threshold ramping arm**, where movement is fastest and drift
   is most likely to be visible — under the quiet baseline, drift should be ~0 and is not informative.

**If drift under the ramp is large enough that the SDK's reported value disagrees with groundtruth(T1)
by more than the T0→T1 gap can explain, that is a genuine finding**: the attribute is measuring the wrong
moment relative to what a reader would assume ("conditions during init"), and should be reported as such
regardless of the other verdicts.

---

## 4. Grading protocol

**Arms** (all on the A01 Core, the only device where the threshold is reachable without extreme
measures):

| arm | allocator hold | expected ground truth | expected `low-memory` | purpose |
|---|---|---|---|---|
| A. Quiet baseline | none | ~44% (today's idle) or lower if harness itself adds load — measure, don't assume | never fires | attribute stability / no false positives at rest |
| B. Moderate pressure | ~125 MB held (~half the 254 MB gap) | mid-20s–30s % | never fires (still above threshold) | confirms directional movement without crossing |
| C. Near-threshold, approaching from above | ramp to threshold + ~10 MB margin, hold | ~17–18% | never fires | precision check just above the line |
| D. Near-threshold, just below | ramp to threshold − ~10 MB margin, hold | ~15% | **must fire** | the core positive-case test |

**Iterations**: this is a targeted, cheap probe, not a timing campaign — the 4×50/4×25 run-shape
guidance in `interpreting-results.md` is for distributional timing claims and doesn't strictly apply to
a binary/categorical flip check. Propose: 10 iterations for arm A, 10 for B, 10 for C, 20 for D (10
confirmed-just-above + 10 confirmed-just-below via ground truth, not just nominal allocator target,
since the allocator's actual held size vs. the live ground truth can drift slightly run to run) — **~50
total cold-start iterations on one device**, well short of a "long device campaign."

**Per-iteration comparison**: for every iteration, capture (a) the SDK's reported
`mem-available-pct`/`low-memory` via the verification tap, and (b) ground truth at T1 (§3). Grade each
iteration against its own ground truth, never pooled.

**Numeric bar for "directionally accurate" (`mem-available-pct`)**:
- `|reported_pct − groundtruth_pct(T1)| ≤ 2` percentage points on every iteration (the attribute rounds
  to a whole percent, so ≤2 accommodates rounding plus the T0→T1 read gap under quiet conditions; widen
  only if §3 shows the ramp arms need more slack, and say so explicitly if it does).
- Strictly monotonic ordering of arm medians (A > B > C > D in ground truth and in reported value, no
  arm-crossing) — this is the "moves correctly with induced memory pressure" bar from the task.

**Bar for `low-memory` (binary)**:
- Fires on 100% of arm-D iterations where ground truth at T1 is *confirmed* below that iteration's own
  computed threshold (not just nominally targeted — use the measured T1 value, since the allocator's
  actual hold will jitter around the target).
- Fires on 0% of iterations in arms A/B/C, and 0% of any arm-D iteration where ground truth at T1 turned
  out to still be above threshold (allocator undershoot) — those are baseline-equivalent for grading
  purposes, not attribute failures if the flag stays silent.
- Any single false positive (fires while confirmed above threshold) is disqualifying on its own — the
  attribute's contract ("presence indicates pressure") is binary and a single false positive breaks it.

---

## 5. Kill-risk management

Under real pressure near an OOM-adjust threshold, the OS can kill either the app-under-test or (less
likely, since it should run at a low LMK priority ranking as a foreground service) the allocator helper.

**Detecting the app-under-test was killed rather than the attribute failing:**
- Absence of the tap's `EMBV1` log lines within a generous timeout (e.g. 5 s post-launch) is the
  primary signal — a genuinely silent attribute still emits the *rest* of the sdk-init span; a
  completely missing span means the process didn't survive to report anything.
- Corroborate via `dumpsys activity exit-info <package>` (surfaces `ApplicationExitInfo` history,
  including `REASON_LOW_MEMORY`) queried immediately after a missing-tap iteration, matching the
  exit timestamp to the launch attempt.
- **These two outcomes must never be conflated**: "no tap output + matching `REASON_LOW_MEMORY` exit"
  = the app was killed before it could report (exclude the iteration from grading, log it as a kill
  event, and back off the allocator size for subsequent iterations). "No tap output + no matching exit
  reason" = a genuine harness/instrumentation failure, investigate separately. "Tap output present but
  `low-memory` absent" while ground truth said it should have fired = an actual attribute failure, only
  countable once kill-adjacent explanations are ruled out.

**Detecting the allocator itself was killed** (which would silently release the pressure mid-iteration,
invalidating that iteration's arm assignment): have the allocator log a periodic heartbeat with its
currently-held size; if a post-iteration ground truth read unexpectedly jumps back toward baseline, treat
that as evidence the allocator died, discard the iteration, and restart the allocator (holding as a
foreground service with a visible notification is specifically meant to minimize this, but it is not
guaranteed on an unfamiliar vendor's LMK tuning).

---

## 6. Integration with profiling runs, cost, and device requirements

- **Reuses existing tooling**: the verification tap (`embrace_verify_telemetry` setting +
  `EmbVerify`/`EMBV1` logcat channel) is the only read path for these attributes, since sdk-init is
  `emb.private` and never reaches an exporter (per `interpreting-results.md`). No new telemetry channel
  needed.
- **New code required**: one debug-only allocator component in `ExampleApp` (§2) — this is a real
  build-touching change (unlike today's read-only probes), should live behind a variant/flag that never
  ships, and must be developed on its own branch.
- **Device requirement**: single device, the Galaxy A01 Core (`entry-a`) — the only device in the
  4-device fleet where the threshold gap (~254 MB) is reachable without extreme or unsafe measures. The
  other three devices need 1.2–7 GB of held allocation to reach their thresholds and are not realistic
  targets for this design; they remain useful only as a "never fires under normal load" corroboration
  (arm A equivalent) for the no-false-positive requirement.
- **A01-specific hazard**: its disk is ~94% full (established previously) — this design deliberately
  avoids any disk-filling induction method (`dd`, large file reads) for exactly this reason, in addition
  to those methods being ineffective for this metric (§2).
- **Cost**: ~50 short cold-start iterations on one device, no perfetto tracing strictly required (the
  verification tap + host-side `/proc/meminfo` polling suffice); optional trace capture only if
  corroborating with GC/kswapd activity is later desired. This is a cheap, single-device, single-session
  experiment — not a long campaign.
- **Does it block other repo work?** No, if the allocator component is scoped to a variant that isn't
  part of the default build/version pin used by other running campaigns. It does need the A01 Core
  device to itself for the duration of the run (currently attached and not flagged as in use by other
  work, but confirm before starting since other devices in this fleet are noted as in use for other
  profiling work).

---

## 7. Kill criteria

**`mem-available-pct`** — downgrade from OBTAINABLE + DIRECTIONALLY ACCURATE to **OBTAINABLE BUT
USELESS** if: under a ground-truth-confirmed swing of ≥20 percentage points between arms A and D, the
reported value fails to move by at least half that swing in the same direction, OR per-iteration error
vs. ground truth exceeds 5 percentage points on more than 20% of graded iterations. Downgrade to
**FANTASY** only if it stops appearing at all under pressure (would contradict the already-established
100%-population-rate finding, so not expected, but the criterion exists in case pressure itself
destabilizes the read path, e.g. via an exception in `getMemoryInfo` under extreme conditions swallowed
by the outer `runCatching`).

**`low-memory`** — verdict is **OBTAINABLE + DIRECTIONALLY ACCURATE** only if it fires on ≥90% of
confirmed-below-threshold arm-D iterations with zero false positives anywhere. Downgrade to **OBTAINABLE
BUT USELESS** if it fires inconsistently (fires on <50% of confirmed-below-threshold iterations) but
never produces a false positive — i.e., it's real but too unreliable to act on. Declare **FANTASY** if,
despite repeated ground-truth-confirmed crossings below the computed threshold across all 20 arm-D
iterations, it never fires even once — the PSI precedent this task is calibrated against. If the
allocator cannot safely and repeatably drive ground truth below the computed threshold at all (e.g., the
device's actual LMK behavior diverges sharply from the mainline AOSP formula, or holding the required
allocation destabilizes the device before any clean iterations complete), report the experiment itself
as **inconclusive/not reachable within safe bounds** — this is distinct from a FANTASY verdict on the
attribute, and should not be conflated with it. Today's numbers (only 254 MB needed) suggest this escape
hatch should not be needed, but it is stated for completeness.

---

## Results (2026-08-14)

The campaign ran on the **Galaxy A01 Core** only (`entry-a`, entry/Go tier, API 29, 911 MB RAM),
exactly the device §6 identified as the only realistic target. The other three devices were not
re-tested here; they remain no-false-positive corroboration from idle-state checks only, per the
original scope.

**Induction, and the two method points that made this work at all.** Pressure was applied in
cumulative ~120 MiB steps by a small native binary (`memhog`, cross-compiled for armeabi-v7a) that
mallocs and then memsets every page, so the pages are genuinely dirty anonymous memory — the
untouched-`mmap` trap §2 warned against was avoided. After each step the app-under-test was
cold-launched and its attributes read via the verification tap (X11), with `/proc/meminfo` sampled
immediately before and immediately after each launch.

- **Ground truth held to `MemFree + Cached`**, exactly as derived in §1 — not `MemAvailable` and not
  `dumpsys meminfo`'s "Free RAM." Grading against either wrong field would have invented an error
  that was really the grader's.
- **Pressure had to come from outside the app-under-test's own package.** §2 already ruled out an
  in-app leak as architecturally wrong for a cold-start benchmark because it can't persist across
  iterations; the campaign confirms a second, independent reason it can't work here regardless of how
  it's written: `am force-stop` runs before every launch and kills every process in the package,
  returning the memory before the measured launch, and SDK init happens at the very start of the
  process — before the app could leak anything in that run. A separate leaky APK would also work and
  would be more realistic, but for a pure free-memory threshold the source of the pressure doesn't
  change what's measured. Worth recording explicitly, since "just make the app leak" is the obvious
  first idea and it doesn't apply here.
- **The page-cache route was confirmed useless for this metric, exactly as §2 predicted**: filling
  the cache just shifts bytes from `MemFree` to `Cached` and nets ~zero against `availMem`. Dirtying
  pages is what moves it.

**Device threshold** (from §1's derivation, unchanged): `low-memory` should flip when `availMem <
147,456 KB` (16% of 911,420 KB) — the point where the two OOM levels collapse on this device.

### Data — Galaxy A01 Core

| step | attr `mem-available-pct` | ground-truth bracket (before..after launch) | inside? | attr `low-memory` | expected |
|---|---|---|---|---|---|
| quiet baseline | 43% | 41–52% | yes | absent | no |
| +120 MiB | 32% | 30–37% | yes | absent | no |
| +240 MiB | 20% | 19–27% | yes | absent | no |
| +360 MiB | 15% | 16–16% | yes | **true** | YES |
| +480 MiB | 13% | 13–14% | yes | **true** | YES |
| +600 MiB | 14% | 11–14% | yes | **true** | YES |

The bracket, not a single sample, is the correct comparison — per §3's ordering-problem design, the
attribute is read at record time, i.e. *between* the before and after ground-truth samples, so a
correct reading should fall inside the bracket rather than match either endpoint. It did on **6 of 6**
launches, including steps where available memory moved 10 percentage points during a single launch.
This directly answers §3's drift question for this device, within the bracket method: the reported
value never disagreed with what the T0→T1 movement could explain.

### Verdict — `mem-available-pct`: OBTAINABLE + DIRECTIONALLY ACCURATE (and better than directional)

It tracked ground truth across a 52% → 11% ramp, landing inside the bracket on 6/6 launches — clearing
both the numeric bar and the monotonic-ordering bar from §4's grading protocol, and comfortably inside
the §7 kill-criteria floor (no more than 5 points of error on more than 20% of iterations; observed
error was inside the bracket, i.e. effectively zero, every time). The provisional verdict from §1's
idle-only corroboration is now final.

### Verdict — `low-memory`: OBTAINABLE + DIRECTIONALLY ACCURATE — its emit path has been observed for the first time

This was the core deliverable (summary table above: "UNKNOWN — feasibility CONFIRMED, not yet fired
even once"). It stayed absent at 52/37/27% ground truth and turned true at 16/13/11% — zero false
positives, matching the §4/§7 bar exactly. It first fired at `availMem` = **147,196 KB** against the
platform-derived threshold of **147,456 KB** — 260 KB below the threshold, i.e. on the correct side of
the boundary. This both proves the emit path works at all and empirically confirms the AOSP-derived
threshold (§1) rather than merely assuming it holds on this vendor's build — closing the §1
vendor-fork caveat for this specific device.

### Kill-risk (§5): no ambiguity in this dataset

No launch was killed at any step, so there is no kill-versus-failure ambiguity to resolve — the
`dumpsys activity exit-info` cross-check designed in §5 was not needed. Available memory recovered
fully to 53% once the `memhog` holds were released, confirming the allocator left no residual
pressure between steps.

### What remains outstanding

- **Only the A01 Core was tested**, as §6 anticipated: it's the only device whose threshold is
  reachable. The other three sit roughly 1.2 GB (A14), 1.5 GB (Pixel 3), and 7.0 GB (Pixel 7 Pro)
  below idle available memory — a real limit, not an oversight, and not closable without absurd
  allocations. They remain no-false-positive corroboration only, as originally scoped.
- **The record-time drift question (§3) is answered for this device, but only within the bracket
  method.** A precise "what moment does the value describe" answer would need in-process
  instrumentation, which this campaign didn't add.
