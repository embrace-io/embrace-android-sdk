# Outlier taxonomy across a device set: tier gating, casts, and prod telemetry mapping

The five outlier classes (A–E) and **how to recognise each one in a single trace** are defined in
`startup-analysis/references/interpreting-results.md`. Learn them there first — this file adds
only what more than one device tells you about them: which classes a given device can even
exhibit, what each class looks like when it wears a different device's costume, and how the
classes map onto in-process proxies you can ship to production.

Established across thousands of instrumented cold starts on a device set spanning entry to
flagship tiers, multiple vendors, and several ART generations. Magnitudes are order-of-magnitude
expectations to re-establish on your own devices, never thresholds to import — the tier-gating of
each class (which classes can even appear) is the durable part.

## Tier gating: which classes a device can show at all

| class | gating | consequence for the set |
|---|---|---|
| A — concurrent system-process CPU bursts | universal, but inflation is tier-differentiated (larger on entry tier than flagship) | present everywhere; only a tier contrast grades its severity |
| B — main-thread flash IO stalls | largely a **storage-class** property: first-class on eMMC-class storage (strong per-iteration correlation on low-RAM entry hardware), modest on UFS-class | record `storage_class`; a UFS-only set concludes IO is a non-issue |
| C — scheduler core placement | a **topology** property: rare-but-catastrophic on big.LITTLE (landing on little cores can roughly double the window); mild-but-common on multi-domain homogeneous parts (a few ms); minor on modern 3-tier flagships (mid-core placement costs a couple of ms) | the ordering by topology is the reusable part; verify magnitude per device |
| D — memory pressure | effectively absent above a couple of GB of RAM | **this is the single strongest reason the set must include an entry/low-RAM device** — without one you will report the class does not exist |
| E — install aftermath | universal, but its *size* varies by vendor independently of tier, because install-time compile policy is an OEM decision | two vendors in the set is what lets you see that |

## Casts: the same class in different costumes

Each device has a **pressure personality** — its own dominant cast for Class A:

- system-process ART heap-compaction GC (common where the OEM build runs heavy background
  services, typically entry/mid tier);
- install-time dex2oat storms plus app-store/GMS update activity (common on vendors with
  aggressive install-time compilation — dex2oat has been seen burning hundreds of CPU-ms inside
  a single startup window);
- GMS/sync adapters plus kswapd on low-RAM tiers.

**The same class appearing with different casts across vendors and tiers is the proof that it is
environmental rather than SDK.** A single-device set cannot make that argument at all: on one
device a cast and a class are indistinguishable, and you will name the culprit wrongly.

## Non-classes: exonerated across the set, not just on one device

- **CPU clock per se**: pinned at max under benchmark load on every non-thermal device measured
  — a *set*-wide null, which is what makes it safe to skip the clock story early on a new device.
- **Charging state**: tested directly, null at matched temperature.
- **Thermal within a low temperature band on recent flagships**: null. This is a *band* result,
  not a device property, and it does not transfer down-tier: on older/hotter silicon heat is real
  and acts via the memory subsystem, invisible to cpufreq. Establish each device's own curve
  (methodology.md → Thermal control across a set) before exonerating it.

## Extending telemetry validation across the set

The class→proxy mapping and the procedure for grading those attributes against a trace are
**single-device work** and live in `startup-analysis/references/interpreting-results.md` →
Prod telemetry mapping. Grading is per-iteration internal consistency: one iteration's attribute
against that same iteration's trace. A second device adds nothing to that comparison.

What a set adds is **coverage of the validation**, and it is not optional:

- **A proxy validated on one device is validated for that device's profile only.** Sensitivity is
  tier- and vendor-dependent: readability of a source can be denied on one OEM build and open on
  the next, and a proxy's dynamic range depends on how hard the underlying class bites on that
  hardware.
- **A class that cannot occur on your device cannot validate its proxy.** Use the tier-gating
  table above before concluding anything from a proxy that never fires: on a roomy device the
  memory/GC class simply does not happen, so a zero there is the device being healthy, not the
  attribute being broken. Validate each proxy on a device where its class *can* occur — memory/GC
  on the entry/low-RAM device, IO on eMMC-class storage, placement on big.LITTLE, install
  aftermath on each vendor.
- **Feature-detected proxies need a vendor spread** to learn what "usually absent" means for your
  fleet, so that absence is reported as a policy artefact rather than as a measurement.

Report validation coverage as a matrix of proxy × device profile, and state which cells are
unvalidated rather than implying the whole mapping is proven.
