# X34 — section-level attribution of 9.2.0: three findings, and the headline question still open

**Ran 2026-08-25**, four devices × {9.0.0, 9.2.0} × 4 passes × 20 iterations, fresh worktree,
per-leg section medians preserved in `claude-output/2026-08-25-x34-attribution/`. All 8 legs
succeeded.

**The question it was built to answer — why the Pixel 3 gained only −25.2% where the other three
clustered near −35% — is NOT answered, because the design could not answer it.** That is stated
first because the rest of this document is worth reading and it would be easy to skim the section
tables and take an attribution from them that the data does not support.

---

## Why the headline question survives unanswered

**1. This dataset does not reproduce the laggard ordering it was meant to explain.**

| device | store (10×20) | X34 (4×20) |
|---|---|---|
| flagship-a | −38.1% | −41.4% |
| **mid-b (the "laggard")** | **−25.2%** | **−38.4%** |
| mid-a | −35.3% | −29.4% |
| entry-a | −35.1% | −33.7% |

In X34, mid-b is the *second-best* improver and mid-a is the worst. The premise inverts. The cause
is almost certainly the shape: I used 4 passes instead of 10 to save device time, and this project
has already established that **pass count is series-defining** — it moves medians
device-dependently (Pixel 3 −10 to −19%, A14 +10 to +17% between 4×50 and 10×20). I applied a
finding to the store and then ignored it when designing the follow-up.

**2. The contrast compared absolute milliseconds across devices with threefold-different
baselines.** entry-a's sections are inherently larger than the flagship's, so a "peer median" in
raw ms will exceed a smaller device's improvement almost mechanically, and the smaller device will
look like it fell short. Any real version of this comparison must normalise — proportional
improvement per section, or per-device shares — before a shortfall means anything.

**What would answer it:** the same 10×20 shape as the store (so the ordering reproduces), section
deltas expressed proportionally, and the in-window restriction below. Roughly 2× the device time
of X34.

---

## Finding 1 — 19 sections lie OUTSIDE the measured window

Verified against 48 traces: of ~60 `emb-*` sections, **19 are not inside the composed window**
(`emb-modules-init` start → `emb-post-services-setup` end). Some run *before* it —
`emb-bootstrapper-init`, `emb-embrace-impl-init`, `emb-init-module`, `emb-otel-module` — and most
run *after*: `emb-record-startup`, `emb-startup-tracking`, `emb-snapshot-session`,
`emb-on-session-cache`, the power/thermal registrations, `emb-otel-external-export`. A twentieth,
`emb-load-embrace-native-lib`, straddles the boundary on 41 of 48 traces.

**This is load-bearing and it caught a wrong answer in flight.** The first run of the contrast
ranked `emb-record-startup` as the single largest shortfall (+12.24 ms) — a section entirely
outside the window, whose peer-median "improvement" (−13.89 ms) *exceeded the flagship's whole
window gain* (−8.1 ms). That impossibility is what exposed it. **Any claim about what moved the
window must exclude these sections**; they are real SDK work and may matter to startup, but they
cannot explain a change in a window that does not contain them.

It also means the metric of record is a **subset** of SDK init, not all of it — worth remembering
when the window is quoted as "what the SDK costs at startup".

## Finding 2 — 9.0.0 → 9.2.0 is a major section-rename boundary

25 sections exist only in 9.0.0 (obfuscated, class-derived: `emb-dz1-init`, `emb-fc5-init`,
`emb-mb0-init`, `emb-configService`…) and 19 only in 9.2.0 (readable: `emb-config-init`,
`emb-core-init`, `emb-essential-service-init`, `emb-persisted-config-load`…). Much of the *same*
work therefore appears once under each name and cannot be differenced.

**Sharpened 2026-08-26 (X38, `2026-08-26-rename-map/FINDINGS.md`).** Restricted to
`emb-modules-init`'s own **direct children**, the split is total: **all 14 removed names are minified
and all 14 added ones are literals**, with only one or two children kept on any device. So for that
span this is not "much of the same work appears under each name" but *its entire child set is
unmatched by name*, which is what licenses reading its apparent reorganisation as labelling rather
than restructuring. The practical consequence is stronger than a caveat on cross-version tables: any
statement about what runs **inside** `modules-init` at 9.0.0 versus 9.2.0 has to be made from
containment and residual accounting, never from name matching.

Consequence: **every cross-version section comparison over this boundary is a partial view**, not
an accounting of the improvement. The comparable subset is roughly two-thirds of the section set.
This is the same class of hazard the project already documented for section-name drift; the
9.0→9.2 boundary is the largest instance so far.

## Finding 3 — within X34's own data, two sections dominate the improvement on every device

Restricted to in-window sections, the same two lead on all four devices:

| device | window | `config-service-init` | `post-services-setup` |
|---|---|---|---|
| flagship-a | 19.54 → 11.44 | 4.71 → 0.28 (−4.43) | 5.39 → 0.74 (−4.64) |
| mid-a | 65.24 → 46.04 | 14.81 → 0.97 (−13.84) | 16.47 → 2.84 (−13.62) |
| mid-b | 48.55 → 29.92 | 8.52 → 1.24 (−7.27) | ~ (−8.09) |
| entry-a | 144.96 → 96.10 | 22.09 → 1.89 (−20.20) | ~ |

Both collapse to near-zero on every device. **They are NOT nested** — checked explicitly, 0 of 48
traces show `config-service-init` inside `post-services-setup`, despite durations that made
containment look likely (config is ~87–90% of post). They are two genuinely separate
contributions.

This continues the documented `config-service-init` collapse from the 8.x → 9.0 era rather than
starting a new story, and it is consistent across a 13× range of device speed.

**Percentages against the window gain are NOT additive** — sections nest in general, and an earlier
version of this analysis reported "top 8 sections account for 194% of the window gain", which is
what double-counting looks like.

---

## What this cost, and the design lesson

X34 spent ~2 h of device time and produced three real findings plus a clear specification for the
experiment that will answer the original question. The failure was avoidable: the shape shortcut (4
passes rather than 10) is precisely the variable this project proved to be series-defining a week
earlier. **A follow-up that measures a difference between arms must use the same shape as the
series that defined the difference**, or it is not measuring the same quantity.

The preserved aggregates remain useful for the next attempt: findings 1 and 2 are properties of the
SDK and the instrument, not of this run's shape, so the in-window restriction and the rename map
carry forward and need not be re-derived.
