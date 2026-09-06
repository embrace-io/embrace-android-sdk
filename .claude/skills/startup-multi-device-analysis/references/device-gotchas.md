# Vendor-class tooling limits and traps

Single-trace interpretation, trace_processor query traps, telemetry verification, install-time
compile state, run-shape statistics, the outlier classes' trace signatures, and measurement-context
artifacts live in `startup-analysis/references/interpreting-results.md` — read that first; this
file covers only what a *diverse device set* adds on top of it.

Everything here was hit in practice on physical devices spanning several vendors, SoC
families, tiers, and ART generations. **Feature-detect, don't assume** — the same node can be
readable on one vendor's build and SELinux-blocked on the next despite identical file modes,
and the failure mode is usually silent (empty output, zero counts) rather than an error. Probe
each capability once per device, record the result in that device's profile, and make the
analysis degrade explicitly when a capability is missing.

## Sysfs / procfs readability

- `/sys/class/devfreq/*/cur_freq` (memory/bus clocks): SELinux-denies shell reads on some OEM
  builds even with permissive-looking 0644 modes, and perfetto's `linux.sys_stats` devfreq
  prober ALSO returns nothing there. On other builds the entire devfreq class directory is
  unreadable. Assume bus clocks are unobservable without root unless a probe proves otherwise;
  infer memory-system speed from IPC signatures instead (run time at constant delivered
  clock).
- `/proc/cpuinfo` part ids and `/sys/devices/system/cpu/cpufreq/policy*/{related_cpus,
  cpuinfo_max_freq}`: readable on every device tried — this is the topology probe's basis and
  the most portable source of cluster structure.
- `dumpsys thermalservice`: works unrooted on every device tried, but the exposed sensor set
  varies enormously by vendor (a couple of named AP/skin zones on some builds, dozens of
  per-CPU/GPU entries on others). Read the sensor names the probe found; never hardcode one.
  (That it beats battery temperature at all is in interpreting-results.md.)
- `/proc/pressure/*` (PSI): readable from a **shell** on most modern devices, and never from an
  app. AOSP labels these files with their own SELinux types (`proc_pressure_cpu` and siblings, via
  `genfscon`) and grants read access to `lmkd` and `system_server` only — no app domain has it, on
  any release since the types appeared in Android 10, and no OEM policy was found that widens it.
  So a probe run over adb reports "supported" while the app gets nothing: measured zero of 12
  launches across 4 devices, API 29–35, two vendors. Fine as a *device-profile* probe value; never
  assume an app-side attribute can read what your probe just read. Verify anything app-side with
  `run-as <pkg>`, not a shell.
- `/proc/meminfo` MemTotal and `/sys/block/*` presence give ram_class and a storage_class
  hint (scsi-style `sda*` for UFS-class, `mmcblk*` for eMMC-class). Storage class changes
  IO-stall severity by an order of magnitude, so record it.

## simpleperf

- `simpleperf stat --app` fails SILENTLY (empty output, zero exit) on some OEM user builds
  even for apps marked profileable. Detect by checking for empty output, not by exit code.
- Command-workload counters (`simpleperf stat -e ... <command>`) work where `--app` does not
  (`perf_event_paranoid = -1` on typical Android builds) — synthetic mem/cpu workloads under
  simpleperf are the portable workaround for state-comparison IPC questions.

## ART / OS version differences

- Which ART slices exist at all changes by generation (older generations — around the Android 10
  era, including Go editions — emit no per-class load slices). How to detect and read that in a
  single trace is in interpreting-results.md; what the *set* adds is that a class-load number is
  only comparable between two devices once you have confirmed both emit the slices.
- Verification/class-load cost *shares* differ by ART generation at similar tier — in one
  cross-generation comparison pre-window class-load moved by roughly a factor of two between
  an older and a newer generation. Measure it on your own set; this is exactly the kind of
  difference the ART-generation axis exists to expose.
- The harness works on Go-edition and 32-bit targets at exactly API 29 — the floor, set by
  profileable shell tracing. Below API 29 only debuggable targets run and the numbers are
  meaningless.

## Harness / benchmark traps

Single-device harness traps (failed-uninstall poisoning of iter000, the SDK version-pin revert,
BuildIdValueSource/APK-bytes, the wiped per-device trace output dir, and why cached-payload
telemetry verification fails unattended) are in
`startup-analysis/references/interpreting-results.md`. What running a *set* adds:

- **Which compile state the benchmark's default mode actually lands on varies by OEM** (the
  mechanics of default-vs-`Partial(Require)` are in interpreting-results.md → Install-time compile
  state). Confirm it **per device** with `dumpsys package dexopt`: assuming one device's default
  applies to the set silently compares different execution modes across devices.
- **Multiple concurrent drivers on one device destroy a run silently.** A second driver's
  `am force-stop`/install cycles kill the app mid-init and its perfetto session competes for
  buffer space; symptoms are missing `emb-sdk-start` slices and arms that silently switch build
  type mid-pass. Before launching anything unattended, verify no other driver process is alive
  (`pgrep -fl startup-tools`, `pgrep -fl connectedBenchmark`) — a harness task list is NOT proof — and give long-running masters a
  pidfile singleton lock that refuses to start when a live PID holds it.
- Harness `cat`/device-state reads appear as small competitor processes in early iterations
  of a pass — expected, not a foreign process.
- Two devices of the SAME model share one `connected/` output directory name — give them
  distinct campaign dirs and never run them concurrently.
- **A leg is verified against the DEVICE's crash buffer, never the harness exit code.** A
  macrobenchmark leg can report rc=0 and write a complete, plausible trace set from a process that
  crashed on every single launch: a crash in a *posted* callback fires after the activity is up, so
  the harness's activity check passes, the window slices are present (init "completed" before the
  crash), and nothing anywhere reads failed. Measured instance ([evidence: E1](#e1)): a runtime-classpath
  conflict killed SDK 8.3.0 on every launch; the two slower devices crashed before the activity
  check and failed honestly, while the fastest device produced 200 green-looking traces and a crash
  buffer holding 200 `FATAL EXCEPTION`s — one per launch. Faster devices are MORE exposed, not
  less. After every leg: `adb logcat -d -b crash` and require zero new `FATAL EXCEPTION`s before
  the leg's data is used; a smoke pass before an unattended campaign must check the crash buffer
  too, not just "the app launched".
- **A hung leg emits nothing, so log monitoring cannot see it.** Watching a campaign log for
  failure signatures catches failures, not stalls: a leg that hangs produces no lines at all and
  looks identical to a slow leg until a driver-level timeout fires hours later (measured: one leg
  hung for a full 4-hour outer timeout, zero passes collected, zero log lines). Give every leg its
  own subprocess timeout sized to the LEG (~2–3× its expected duration), and make any watchdog
  assert on *expected progress within an interval* rather than only matching failure strings.
- **Killing a campaign does not kill its tree.** SIGTERM to a driver leaves its child benchmark
  runner and THAT child's gradle client alive (and device-side tracers — see the tracer note in
  "Driving a device outside macrobenchmark"). After any kill, sweep the host for survivors
  (`pgrep -fl fleet-campaign`, `pgrep -fl connectedBenchmark`) and the device for tracers before
  trusting the fleet is idle; a driver that spawns children should run them in a process group and
  kill the group.
- **The Go-tier `ddmlib ShellCommandUnresponsiveException` on install is intermittent, not a
  device state.** Across four attempts on the same device it allowed 6, 0, 10 and 4 passes with no
  pattern; load average ~20 is that tier's normal idle (it coexists with clean 10-pass legs), so
  neither reboots nor cooldowns fix it. The mitigation is a bounded retry scoped to exactly that
  failure signature — anything broader retries genuine failures into false passes.

## Driving a device outside macrobenchmark

Running launches from a own-built harness (prebuilt APK + `am start` + perfetto) avoids the gradle
catalog pin entirely, which is what makes several experiments runnable in parallel on different
devices. It also walks into a specific set of traps, all of which cost a failed run at least once:

- **Feed the perfetto config on STDIN, never as a pushed file path.** On-device `perfetto` runs
  under an SELinux context that cannot read `/data/local/tmp`, so `-c /data/local/tmp/cfg` dies
  with `Could not open ... (errno: 13, Permission denied)`. Use
  `adb shell perfetto -c - --txt -o <out> --background` with the config text as process stdin.
- **`adb shell` joins argv into ONE string handled by the device's outer shell** — which runs as
  `shell` with cwd `/`. So a redirect passed as a separate argv element is applied by that shell,
  not by the command you think you are running: `["shell","run-as",PKG,"sh","-c","cat > f"]`
  fails with a misleading "No such file or directory" even when the path exists. Pass the whole
  thing as one quoted string instead: `shell "run-as PKG sh -c 'cat > f'"`.
- **`run-as` needs a debuggable build, and the build types are not what their names suggest.**
  In ExampleApp, `debug` is debuggable; `benchmark` sets `isDebuggable = false`; `obfuscated` is
  release-like AND debuggable. Copies of "the debug APK" saved by earlier experiments may in fact
  be the benchmark variant — check `dumpsys package <pkg> | grep pkgFlags` for `DEBUGGABLE` rather
  than trusting a filename. Note the standing tension: deleting app files needs `run-as` → needs
  debuggable → and a debuggable app is never fully AOT-compiled, so a clean file-manipulation arm
  and a fully-compiled arm cannot be the same build.
- **A `run-as` FAILURE is not a measurement.** `not debuggable` / `unknown package` /
  `Permission denied` must raise, never be folded into "the file is absent" — otherwise a broken
  probe is indistinguishable from a successful reading of an empty state. Same
  did-the-reader-work-vs-is-the-data-absent rule that governs attribute grading.
- **Verify paths on-device instead of reading them out of current source.** A prebuilt APK carries
  whatever the SDK looked like when it was built; the config store was `files/embrace_remote_config/`
  in the APK under test while `PersistedConfig.STORAGE_DIR_NAME` in HEAD already said `"n"`.

## Compile state is an experimental variable, not a fixed property

`cmd package compile -m <filter> -f <pkg>` pins the filter, which turns the fleet's largest
confounder into something blockable — the confound protocol's preferred treatment for a discrete
confounder. Three states matter and they are NOT ordered the way "more compilation is faster"
suggests:

- `speed-profile` **with a populated profile** — fastest.
- `verify` — middle. Pre-verifies every class at install, so runtime verification is skipped.
- `speed-profile` **with no usable profile** — worst, and by a lot. It forgoes install-time
  verification without gaining AOT coverage, so classes are verified at runtime.

Measured on the mid device: init windows of 29 / 36 / 51 ms across those three. Measured on a
Tensor flagship: no difference at all (all within 1 ms of 126 ms, CIs spanning zero). **So treat
compile state as a mid/entry-tier lever and always record which state a run was in.**

Populate the profile with
`am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE <pkg>/androidx.profileinstaller.ProfileInstallReceiver`
before compiling. **Profile POPULATION cannot be verified on an unrooted device** — both
`/data/misc/profiles` and the app's `oat/` dir are shell-unreadable, and `dumpsys package`'s
Samsung "profile utilization" line reports a stale `s=REMOVED` that does not track `cmd package
compile`. So "profiled" only ever means "the broadcast was sent"; say so rather than claiming a
verified state. The first launch after ANY recompile pays re-verification and must be held out
like an install-aftermath iteration.

## Long-running campaigns: DON'T borrow global state — run from a worktree

**The preferred pattern (adopted after four borrowed-state incidents in two days): campaigns
that need repo mutations — the catalog's SDK version pin, benchmark iteration counts, compat
patches for old versions — run from a dedicated `git worktree add --detach`, never from the user's
checkout.** The worktree gets edited freely, per-version resets are a plain in-worktree
`git checkout`, gradle builds into the worktree's own build dirs, and the whole thing is deleted
afterwards — there is nothing to restore, so the entire class of restore-on-kill hazards below
does not exist. `tools/startup fleet-campaign --repo <worktree>` targets it, and `compat-patch`
and `cell-runner` take the same `--repo` flag (without it a command self-locates via
`git rev-parse`, so one run from inside the worktree targets it automatically). Two boundaries:
the user's working tree is the *subject* only
when benchmarking uncommitted changes (then the checkout is the point — copy the diff into the
worktree or accept borrowing); and the host must still stay quiet-ish during legs — the worktree
frees the *tree*, not the CPU.

**A worktree directory that still exists is not a worktree that still works.** Scratchpad worktrees
live under `/private/tmp`, which macOS purges by *file* age, and the purge leaves the module
directory tree standing while deleting `gradlew`, the top-level build files and `.git`. The result
looks intact to `ls`, is invisible to `git worktree list`, and fails only once a campaign tries to
build. Probe for a **file** the build needs — `examples/ExampleApp/gradlew` — not for the directory,
and recreate rather than repair: a gutted worktree cannot be restored in place. (Observed on two
A/B worktrees, each reduced to a few hundred KB of empty directories.)

The borrowing discipline below remains for the cases a worktree cannot cover. When you must
mutate something genuinely global (a device setting, mavenLocal contents, the user's checkout
itself), a campaign must be able to give it back after being killed. `try/finally` is not enough —
**SIGTERM skips `finally` entirely**, and two killed runs in one session each left the pin at the
wrong version. Three layers, because each covers what the others cannot:

1. **A marker file** written when the state is borrowed and deleted when returned. Its presence at
   startup means a previous run died holding it. This is the ONLY layer that survives SIGKILL or a
   power cut, and recovery must run **before** the current value is read — otherwise a stale value
   left by a dead run is mistaken for the user's own setting and then faithfully "restored" at the
   end, making the damage permanent.
2. **Signal handlers** for SIGTERM/SIGINT/SIGHUP that restore and then re-raise with `SIG_DFL`, so
   the exit status still reflects the signal instead of looking like a clean exit.
3. **`finally`** for normal exit and exceptions.

Worth testing rather than assuming: drive the real file, send a real SIGTERM, send a real SIGKILL,
and assert the next startup recovers.

Two defects measured in a real implementation of this pattern, both of the kind a casual read passes
over:

- **Nested borrows half-restore on a signal.** With two borrowed states (pin wrapping a source
  edit), each context's own handler restores *its* state and re-raises with `SIG_DFL` — so the
  INNER context restores and the re-raised default disposition kills the process before the outer
  handler ever runs. Normal exit and Python exceptions unwind both correctly, which is exactly why
  the defect hides: it only manifests on the signal path. The handler must unwind a module-level
  registry of ALL live borrows in reverse order, then re-raise once.
- **The marker round-trip must be byte-exact.** A restore that strips a trailing newline returns a
  source file that is one byte off — a spurious diff on an otherwise clean tree, and a lint finding
  on Kotlin. No `strip()` anywhere on the stored value; assert `restore(capture(x)) == x` on bytes.

## Config-flag A/B: prove the arms differ BEFORE spending device time

A flag A/B is two builds of one commit differing in an `embrace-config.json` key. If the flag does
not take effect — wrong key, config not picked up, a stale APK reused — **both arms are the same
program**, and the campaign then reports "no significant difference" with clean statistics and no
symptom anywhere distinguishing that from a real null.

**Before anything else, verify WHICH SDK the app will build against.** A config-flag A/B changes a
key in `embrace-config.json`; it does not change which SDK artifact the app resolves. The ExampleApp
pins `embrace = "<version>"` in `examples/ExampleApp/gradle/libs.versions.toml` and resolves from
mavenCentral, so **a worktree checked out at the commit under test still builds against a published
release** unless that pin is repointed. That one line also drives the *plugin* version, since the
plugin entry resolves through the same ref. Flipping a flag then changes a setting inside an SDK that
is not the one being tested.

This voided a whole engine A/B campaign at a cost of hours of device time ([evidence: E2](#e2)): twenty
clean legs returned a null where a campaign eight days earlier had measured a large effect. The config
file was right, the plugin had read that key for months, and the dex payloads differed — every check
that was run passed, because none asked which SDK was in the APK. The earlier campaigns had listed the
prerequisite plainly (publish the build under test to mavenLocal and repoint the pin) and it was not
carried forward.

Three checks, cheapest first: **read the pin** and confirm it names the build under test; **run the
propagation gate on leg 1** (below) and abort the device if it does not fire; and afterwards,
**compare the level against the longitudinal store** — the voided campaign's pooled median sat on the
store's record for the *previous* released version, not the one under test, which is how the wrong SDK
was identified. The store doubles as a provenance check: it can tell you which version you actually
measured.

**The primary arm check is the PROPAGATION GATE, not an artefact comparison.** Verify the arms from the
SDK's own instrumented sections: the effect must concentrate in the sections the flag targets while
unrelated sections stay flat. For the otel-kotlin flag that signature is unmistakable — the OTel
construction sections move −42% to −90% while `otel-module` holds within ±7% as an internal control.
Noise cannot concentrate a reduction of that size in exactly the right sections and leave everything
else alone. This is the method the project arrived at after three artefact-based attempts failed, and
it is what the earlier engine A/B campaigns gated on per device. **Prefer it whenever the flag has a
predicted locus**; fall back to artefact comparison only when it does not.

*Correction: an earlier version of this section led with the dex comparison and did not mention the
propagation gate at all, which understated a method the project had already established and reduced
a decisive check to a smoke test.*

The artefact-level check is still worth running as a cheap pre-flight —
`tools/startup verify-arms <a> <b> [<a-rebuilt>]` — because it catches the total-failure case before any device
time is spent. What it can and cannot establish:

- **Identical APK size is what SUCCESS looks like**, not failure. The plugin injects local config by
  rewriting SDK *bytecode*, so a boolean flip is a one-opcode change. Two correctly-differing arms
  both came out at exactly 7,112,294 bytes.
- **Do not look for generated source.** There is no KSP-generated config class under
  `app/build/generated` — the plugin rewrites bytecode instead of emitting source. A check that
  looks there reports a false failure on good arms, which is exactly what happened first.
- **The APK digest proves nothing on its own**; zip metadata and timestamps move it.
- **Android builds are not byte-reproducible, so "the dex differs" is a smoke test, not proof.** The
  same two trees differed by **275** dex byte positions when built as a pair, and by **11,790** after
  the harness independently rebuilt both — a 43× swing, all noise. Any byte-count threshold is
  meaningless without a control build of one arm against itself.
- **Byte-identical dex is the one conclusive verdict**: the flag did nothing, do not run.
- **The engine-agnostic verification tap cannot discriminate engines** — same span names, same
  resource attributes, same attribute keys in both arms. Checked; it is not a route.
- **The strongest cheap control is a metric already measured independently.** For the otel-kotlin
  flip, prior campaigns had established the median effect per device, so reproducing that median
  inside the new run confirms the flag works and licenses reading the new statistic (the tail). Plan
  an A/B so that it re-measures something known, rather than only the novel quantity.
- **Do not delete the traces until the propagation gate has run.** A campaign that reduces to window
  values only — to save disk, say — throws away the section data the gate needs, and the gate is the
  one check that can confirm the arms differ. Extract per-section medians in the same pass that
  extracts the windows, then delete.

## Structure and level need different sample sizes

Containment — which sections nest inside which — is a property of the code path and is stable in a
handful of traces. A duration median is not. Taking both from the same small sample produced a span
longer than the window that encloses it (`emb-modules-init` 100.77 ms against a 94.46 ms window),
because the four traces came from `pass1`, the pass most distorted by install aftermath. Read
structure from a few traces if that is all you have, but take every *level* from the full-shape
medians, and sample across passes rather than from the head of one.

## Device housekeeping that silently kills campaigns

- **Macrobenchmark accumulates per-iteration traces in `/sdcard/Android/media/<benchmark-pkg>/`
  and never cleans up.** One entry-tier device reached 7.9 GB there and filled `/data` to 94%,
  after which `pm install` began failing. Check `df /data` before an unattended run and clear that
  directory when it has grown — the traces the analysis needs have already been pulled to the host.
- **Entry-tier devices intermittently blow ddmlib's install-commit timeout**, surfacing as
  `Failed to install split APK` → `ShellCommandUnresponsiveException`. Observed at 44 °C and 55 °C
  with 8.7 GB free, on whichever pass got unlucky — so it is neither thermal nor disk, it is a slow
  eMMC. Retry that ONE signature; retrying broadly turns real benchmark failures into false passes.
- **Cool between passes, gated on SILICON not battery.** Battery reads ~31 °C while the CPU sits at
  55 °C, so a battery-gated wait releases immediately. Gate relative to the device's own settled
  idle temperature, since idle baselines differ by tier, and note that "settled" needs a strict
  threshold: a 1 °C-per-30 s test declares 42 °C settled on a device that idles at 34 °C.

## Running unattended: the mechanisms turn on each other

Everything above assumes someone is watching. When nothing is, the failure mode is rarely a single
bug — it is two safeguards interacting, and the run that looks finished is the one to distrust.

- **One host, one campaign — chain, never parallelise.** The per-device rule above is not enough:
  two gradle campaigns on the same host contend even when they drive different phones, and heavy
  `trace_processor` analysis counts as contention too. Run analysis only while the fleet is idle.
- **A timeout kill can manufacture the crash its own guard then trips on.** Observed cascade: an
  attempt timeout set too short for the device's real pass rate fired → process-group kill →
  instrumentation died → the harness wrote a `FATAL EXCEPTION` to the crash buffer → the crash-buffer
  guard poisoned a leg that had in fact just completed. Size timeouts from *measured* pass rates, not
  hope, and read the crash buffer BEFORE the kill so you have a baseline to diff against.
- **Scope the crash-buffer guard to the APP's process.** Counting every `FATAL EXCEPTION` fails a
  valid leg when the *harness* is what died. An app crash invalidates measurements; a harness crash
  aborts the leg and leaves what it already collected good. Match on the app process specifically.
- **Daemonize anything long** (`start_new_session=True`). Background tasks belonging to the agent
  harness get reaped when it moves on, killing a campaign hours in with no error anyone will see.
- **Capture failure evidence BEFORE the cleanup that erases it.** The `git checkout -- .` that makes
  a leg safe to retry also destroys the patched tree that failed to compile, so the build error
  cannot be re-read afterwards. Copy the diff and the FULL build log into the campaign directory on
  failure, then revert.
- **Don't summarise build errors — capture a window.** Logging only the lines matching a marker such
  as `What went wrong` records the header and none of the message. Keep several lines after each
  marker; a truncated error is indistinguishable from an unexplained one when you read it days later.
- **Refuse pass-level statistics when `n != passes × iterations`.** Positional pass recovery
  misaligns when any leg is short, quietly attributing one pass's launches to another.
- **A kill does not propagate — verify teardown on BOTH sides.** Killing a driver left its child
  campaign still driving a device, and killing *that* left the `gradlew` client running the connected
  test; each had to be hunted by hand. The device side is worse, because a host-side `pgrep` cannot
  see it: stranded `tracebox`/`perfetto`/`atrace` processes hold the kernel ftrace buffer, so every
  later trace is silently empty while the tooling still reports success, and their scratch files fill
  `/data` until installs start failing. Run children in a process group and kill the group, then
  VERIFY rather than assume: host-side for the driver and its gradle client, device-side for the
  tracers, plus `df /data`. Clear stuck tracing with `atrace --async_stop`.

---

# Appendix — evidence behind the rules

Point-in-time incidents from one four-device fleet (a Tensor flagship, a 2018 flagship, a mid-tier
Exynos, a 1 GB Go device) during the August 2026 campaigns. Kept so the reasoning above can be checked;
the rules stand on their own and none of these figures is a threshold to import.

<a id="e1"></a>
### E1 — A crash on every launch with a green harness

A runtime-classpath conflict killed SDK 8.3.0 on every launch of a version-sweep leg. The two slower
devices crashed before the activity check and failed honestly; the fastest device produced 200
green-looking traces and a crash buffer holding 200 `FATAL EXCEPTION`s, one per launch.

<a id="e2"></a>
### E2 — An engine A/B that measured the wrong SDK

The ExampleApp catalog pin still named the released 9.2.0 while the worktree was checked out at the
commit under test, so both arms built against the release. Twenty clean legs on the mid-tier device
returned +0.1% at the median (p = 0.97) where a campaign eight days earlier had measured −20.4% on the
same flag; 2.5 hours of device time were voided. The pooled median (61.2 ms) sat on the longitudinal
store's record for the previous release (63.0 ms), not the one under test (45.1 ms), which is how the
wrong SDK was identified.
