# Why the Pixel 3 gained less from 9.2.0 — answered

**X35, 2026-08-25.** Three devices × {9.0.0, 9.2.0} × **10 passes × 20 iterations**, per-leg section
medians preserved in `claude-output/2026-08-25-x35-attribution-10x20/`. All 6 legs succeeded.
This supersedes X34, which used 4 passes and could not answer the question.

## The premise reproduces at the right shape

| device | X35 (10×20) | store (10×20) | X34 (4×20) |
|---|---|---|---|
| entry-a | −35.2% | −35.1% | −33.7% |
| mid-a | −33.7% | −35.3% | −29.4% |
| **mid-b** | **−29.3%** | **−25.2%** | −38.4% (inverted) |

mid-b is the laggard again, agreeing with the store. X34's inversion was the 4-pass shape, exactly
as suspected — which is itself a clean confirmation that **pass count changes what you measure**,
not merely how precisely you measure it.

## The answer: `emb-post-services-setup`

Comparing each section's improvement as a **fraction of its own 9.0.0 duration** (absolute
milliseconds are useless across devices whose baselines differ threefold — that flaw invalidated
X34's contrast):

| section | base (mid-b) | mid-b | peer median | gap |
|---|---|---|---|---|
| **`emb-post-services-setup`** | 12.04 ms | **−67%** | **−84%** | **+17 pts** |
| `emb-config-service-init` | 8.83 ms | −84% | −93% | +8 pts |
| `emb-span-service-init` | 11.87 ms | −3% | +11% | −14 pts |
| `emb-modules-init` | 25.38 ms | +16% | +28% | −12 pts |
| `emb-otel-tracer-init` | 7.82 ms | −1% | +8% | −9 pts |

**Containment measured, not assumed** (24 traces, every trace agreeing): `modules-init` **contains**
`config-service-init`, `span-service-init`, `essential-service-init`, and `span-service-init`
contains `otel-tracer-init`. **`post-services-setup` is a sibling of `modules-init`** — independent
of all of them.

That structure collapses the table to one finding. Inside `modules-init`, mid-b's small deficit on
`config-service-init` is more than offset by doing *better* on `span-service-init` and
`otel-tracer-init`; the parent as a whole regressed **less** on mid-b (+16% vs +28%). The only
independent, material shortfall is **`post-services-setup`**.

**Arithmetic:** mid-b gained 14.58 ms; at the peers' ~34.5% it would have gained ~17.2 ms — a
shortfall of **~2.6 ms**. `post-services-setup` at −67% saved 8.07 ms where −84% would have saved
10.11 ms — a shortfall of **~2.0 ms, about 79% of the total.**

**So: the Pixel 3's smaller 9.2.0 gain is concentrated in the post-services-setup phase, which
improved 67% there against 84% on its peers.** It is a specific phase, not a diffuse device-wide
effect — which argues *against* the ART-generation hypothesis in its global form (uniformly weaker
codegen would depress every section, and here half the sections improved *more* on mid-b).

## The finding nobody was looking for: `modules-init` got WORSE

`emb-modules-init` — the largest in-window section, 25.4 ms on mid-b — **regressed on every device**
(+16% mid-b, +28% peer median) while its own children improved dramatically (`config-service-init`
−84 to −93%). Work grew inside the parent even as its children shrank.

The likely explanation is the **rename/refactor boundary** documented in X34: 19 sections appear
only in 9.2.0 (`emb-config-init`, `emb-core-init`, `emb-essential-service-init`,
`emb-persisted-config-load`, `emb-storage-init`…), several of them inside `modules-init`. Work that
existed in 9.0.0 under other names may now be attributed there. **This is not established** — the
rename map would have to be resolved name-by-name to separate genuine regression from
re-attribution, and that is the obvious next analysis. Either way it is worth attention: **9.2.0's
window improvement is a large gain in two phases partially offset by growth in the biggest one.**

> ### RESOLVED 2026-08-26 (X38) — see `2026-08-26-rename-map/FINDINGS.md`
>
> The suspicion above is **broadly right, and now measured**: it is a **phase shift inside a naming
> discontinuity, not concealed cost**.
>
> **Why this doc could not settle it, which is worth knowing before trusting any containment claim
> here:** the containment map quoted above was computed by `x35_containment.py`, which globbed
> `9.2.0__*`. It measured the **new structure only**, over six named sections. The 9.0.0 side — which
> is precisely what a re-attribution claim is about — had never been measured at all.
>
> Rebuilding the tree for **both** versions gives the answer. The **residual** — the part of
> `modules-init` that no named child accounts for — moves only +0.51 / +1.57 / +2.69 ms, so it
> explains just **4–12% of the growth**: nothing is hiding inside the span. Its direct children are
> almost wholly replaced (14 added against 14–15 removed, 1–2 kept), and **every removed name is
> minified while every added one is a literal**, so its children cannot be matched across the step by
> name at all. The growth is therefore real in duration and benign in effect — work moved into the
> span while the phases outside it improved far more.
>
> One number here should be read with that in mind: the `+16% mid-b / +28% peers` comparison in the
> table above compares two spans that enclose **different children**, so it is a valid measurement of
> the span's duration but not a like-for-like comparison of its contents.

## Caveats

Three devices, so the "peer median" is a median of two — thin, and entry-a's much larger sections
dominate any absolute reading (hence the proportional method). The comparison excludes the 19
sections that lie **outside** the composed window (X34 finding 1) and the ~44 sections that exist
in only one version (X34 finding 2), so it covers 12 comparable in-window sections ≥0.20 ms. The
window itself is a subset of SDK init. Section medians here come from 10 passes per arm and agree
with the longitudinal store's windows to within a few percent on every device.
