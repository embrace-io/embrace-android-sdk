# Confound protocol: what to control, what to record, what to declare

Statistics control **sampling** error. They do nothing about **confounding** — a systematic
difference between the things being compared. On this fleet the known confounds are *larger than
most effects being chased*:

| confound | measured magnitude | can it be eliminated? |
|---|---|---|
| install-time compile state (AOT parity) | **±20%** on the window | yes — pin with `pm compile`, or balance across arms |
| thermal state | **+31–49%** on a flagship, +10% on an older mid | partly — gate on temperature, quiesce, interleave |
| install aftermath (dexopt, PACKAGE_ADDED) | 2–3× background CPU on the first iterations | yes — discard or segment the first ~3 iterations |
| background churn (GMS, Play, system_server) | drives most single-iteration outliers | no — only randomised across arms by interleaving |
| page-cache warmth | 1136 KB → 0 KB of reads between cold and warm | yes — control by fixing launch index |
| device identity / tier | 7× spread across the fleet | yes — never pool across devices |

A tight confidence interval around a confounded difference is a **precise wrong answer**. Design
controls therefore do more work here than the choice of test, and the protocol below is not
optional garnish.

## The five rules

### 1. Interleave arms; never run all of A then all of B
Any drift over time — heat accumulating, storage filling, an OS background job starting — aligns
perfectly with a blocked design and becomes indistinguishable from the treatment. Interleave at the
pass level (A B A B), and where a comparison spans devices, **counterbalance the order** so one
device runs A→B and another B→A. A consistent effect then has to survive opposite time-order
biases.

### 2. One arm, one install is not one sample
Compile-state parity is assigned *per install* and is bimodal, so a single install per arm cannot
be averaged out — with one install you simply pick a side. Use **at least four passes per arm from
independent installs**, and record which compile state each landed on (`dumpsys package <pkg>
dexopt`). Four is also the smallest design that permits cluster-level significance at all.

### 3. Fix the launch index
The first launch after an install is a different experiment from the fifth: install aftermath and a
cold page cache both bite. Either discard the first ~3 iterations from every arm identically, or
treat launch index as a factor and compare like with like. Never compare arm A's cold launches with
arm B's warm ones.

### 4. Gate and record the environment, per pass
Before each pass, record and log: battery and skin temperature, whether a cool gate was met, free
storage, compile state, SDK version, app build id, and whether any other harness is running. These
are what make a confound *detectable after the fact* rather than absorbed into "noise". A pass that
ran outside the gate is not deleted — it is recorded and excluded with its reason.

### 5. Declare the comparison before looking
Which statistic (median? p90?), which devices, which arms, and the family of tests — written down
before the data is seen. Choosing after the fact is the garden of forking paths, and with 17
sections × 4 devices something will always look significant.

## The confound statement

Every comparison reports one, in this shape. It is short by design; the point is that each line
forces an explicit answer rather than a silent assumption.

```
Comparison:      <what vs what>, on <devices>, statistic <median|p90>, declared <when>
Design:          <n> passes/arm from <n> independent installs, interleaved <yes/no>,
                 order counterbalanced <yes/no>, launch index <policy>
Controlled:      <confounds actively controlled, and how>
Recorded:        <confounds recorded but not controlled, and their observed range>
Uncontrolled:    <confounds neither controlled nor recorded - the honest gaps>
Known effect:    <magnitude of the largest uncontrolled confound vs the effect being claimed>
```

The last line is the one that matters. **If the largest uncontrolled confound is bigger than the
effect being claimed, the comparison cannot support the claim** regardless of its p-value — say so
in the result rather than in a footnote.

## Worked example, from this project

A version comparison ran 4 passes per arm on each device, arms in opposite order across two devices,
first launch retained (so install aftermath is present in both arms equally):

- **Controlled**: device (never pooled), recipe (build type, compile mode, run shape frozen),
  order (counterbalanced across devices).
- **Recorded**: battery and silicon temperature per pass; compile state per install; free storage.
- **Uncontrolled**: background churn; whichever compile parity each install happened to land on.
- **Known effect**: parity is worth ±20%. On the device where the measured difference was +1.0%,
  **the uncontrolled confound is twenty times the effect** — so that device's result is reported as
  indistinguishable, which is also what the cluster-level interval said independently.

That agreement between the design analysis and the statistics is the ideal case. When they
disagree, the design analysis wins: statistics cannot see a confound that is constant within an arm.
