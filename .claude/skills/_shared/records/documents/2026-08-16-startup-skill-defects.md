# Skill defects found running the 10×20 campaign, 2026-08-16

Five defects surfaced during one overnight campaign. Four are in the startup skills; one is in the
scratchpad driver but describes a trap the skills invite. They are ordered by what they cost.

Each cost something real, and three of them share a shape: **a step that is correct exactly once
runs again on a restart, and the second run destroys what the first produced.** A long unattended
campaign will be restarted — by a crash, a reap, or an operator — so "runs once" is not a property
any startup step may assume.

---

## 1. One-time archive steps re-run on restart, destroying the previous series

**Cost: 8 store records and 1,600 per-launch measurements, unrecoverable.**

`combined_campaign.py` archived the previous series with an unconditional
`STORE.replace(ARCHIVE / "store.jsonl")`. Correct on a first run. On a restart the live store holds
*this* run's records, so the move both displaces them and overwrites the archived series beneath.
The 4×50 series was destroyed this way at 08:29 — its raw traces having been pruned earlier
precisely *because* they were reduced into that store.

The same bug hit the reference set twice before that, silently reverting hand-applied device keys.

**Fix applied:** guard both moves on whether the archive already exists. **Fix owed in the skills:**
`references/store.md` should state that archiving a series is a one-time, guarded operation, and
`ingest_run.py` or its caller should refuse to overwrite an existing archive without `--force`.

**The compounding error worth naming separately:** pruning raw traces because "they are reduced
into the store" makes the store a single point of failure. Either the traces stay, or the store is
copied before any driver is allowed to touch it. Reduction is not a backup.

---

## 2. `reference_set.py --probe` emits provisional values that must be corrected by hand — and
nothing says so except a comment about one of them

**Cost: 6 of 8 ingests would have been refused; one full leg's ingest was refused before it was
caught.**

`--probe` writes tier-derived device keys (`entry-mid-b`, `entry-d`) that a human is expected to
rename, and a **default `recipe.instrument` of `app-embrace-start`** that this harness never emits
— ExampleApp has no such span, so every version's window is the `composed` fallback.

The file's `_comment` warns that device keys are "YOUR stable label", but says nothing about the
recipe. `instrument` is the field ingest validation hinges on, and a wrong value fails *every*
ingest with `no 'app-embrace-start' window found in 200 trace(s)`.

**Fix owed:** `--probe` should either detect the instrument from a sample trace, or write it as
`null` and have ingest refuse until it is set deliberately. A default that silently invalidates
every downstream record is worse than a missing value. The `_comment` should cover the recipe too.

---

## 3. `BorrowedState` restores only the innermost context on a signal

**Cost: the version pin left dirty twice, requiring manual `git checkout` both times.**

Nested contexts (`pin` wrapping `kt`) restore correctly on normal exit — verified at 08:28, both
logged. On **SIGTERM** only the inner context restores: the handler re-raises with `SIG_DFL` after
its own restore, so the outer context never runs. The marker file makes it recoverable on the next
startup, which is what saved it here, but the tree is left dirty in the meantime and a run that is
never restarted stays dirty.

**Fix owed:** the signal handler must unwind the whole stack of live `BorrowedState` instances, not
just its own — a module-level registry restored in reverse order, then re-raise.

---

## 4. `BorrowedState` round-trip is not byte-exact

**Cost: cosmetic, but it leaves a spurious source diff.**

`StartupBenchmarks.kt` came back from a restore missing its trailing newline. Content is preserved
through the marker file; the final newline is not. On a Kotlin source file that is a lint finding
and a confusing diff on an otherwise clean tree.

**Fix owed:** the marker round-trip must preserve bytes exactly — no `strip()` on the way in or out.

---

## 5. Killing a campaign orphans its children, host-side and device-side

**Cost: an orphaned `fleet_campaign.py` kept driving a device after its parent died; its Gradle
client survived that too and had to be hunted separately.**

Killing `combined_campaign.py` left `fleet_campaign.py` running a leg, and killing *that* left the
`gradlew` client running `connectedBenchmarkAndroidTest`. Each had to be found and killed by hand.
This compounds the already-documented hazard that a host-side kill leaves device-side tracers
alive.

**Fix owed:** the driver should run its children in a process group and kill the group, and its
teardown should verify — host-side (`fleet_campaign`, `gradlew`) and device-side (`tracebox`,
`perfetto`, `atrace`) — rather than assuming a `kill` propagated.

---

## Not a defect: A01 load, and what the error actually means

Recorded because it was misdiagnosed during the run and the wrong conclusion nearly reached the
skill docs. The A01 Core idles at load ~20 while the flagship sits at 0.44 — that is its **steady
state**, not saturation, and it coexists with successful passes. A reboot on that theory changed
nothing (load back to 20 within 42 minutes) and cost a boot-epoch discontinuity on the one device
with the least data.

`ddmlib ShellCommandUnresponsiveException` on that tier is an **intermittent install/uninstall
timeout**, not a device-state problem. Across four attempts it produced 6, 0, 10, and 4 passes —
no pattern. The correct mitigation is a retry around the install step, not remediation of the
device.
