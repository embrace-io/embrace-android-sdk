# Nothing in a skill may describe one machine

**The rule: after any edit to a startup skill or to the `embrace-analysis-*` modules, run the sweep
before you call it done.**

```
tools/startup check-local-refs          # exits 1 on a hit, so it can gate a commit
tools/startup check-local-refs --self-test   # checks the patterns themselves, scans nothing
```

A skill is instructions for whoever reads it next, on their own hardware. A device serial, a home
directory, a link to a private artifact, or a dated run directory that exists on one laptop all read as
noise to that person, and worse, as an instruction to look somewhere they have nothing. The same goes
for the tool's own source. This is not a style preference: the knowledge is only useful if it survives
leaving the machine it was learned on.

## What it checks, and the two standards

The sweep holds prose and evidence to different bars, because they do different jobs.

| scope | what it covers | which patterns |
|---|---|---|
| source | every `SKILL.md` and `references/`, `embrace-analysis-*/src/main`, `tools/` | all of them |
| evidence | `_shared/records/`, the test fixtures | only the personal ones |

A campaign record citing the run it came from is the point of a campaign record. A skill citing that
same run is a defect, because the reader has no copy of it. So the records keep their artifact links,
dated directories and experiment codes, and are still swept for home paths, addresses, serials,
credentials and scratch paths. Archives are read without unpacking, so a local reference cannot hide inside one.

## When it fires

1. **Generalise it.** "In one campaign", "on the mid-tier device", "the analysis records (Month YYYY)".
   The claim survives; the coordinates go. Run data belongs in a linked appendix, cited generically.
2. **If the hit is a legitimate look-alike**, suppress it where it sits, with the reason:
   `<!-- local-refs:allow: the URL form, not a link to one -->`. A whole line carrying that marker is
   skipped. For a one-off run, `--allow REGEX` suppresses a matched text without editing anything.
3. **Never widen a pattern to make a hit disappear.** Each pattern carries its own positive and negative
   sample and a unit test holds it to both; loosening one silently retires a check.

Exempt by name, because carrying a serial in machine-readable form is their job: `reference-set*`,
`run-metadata.json`, `cell-state.json`, and the corpus goldens. Every document names devices by their
reference-set key instead.

## The personal layer

The checked-in patterns describe the *shape* of a local reference. The values one engineer's own runs
would expose, their own serials and hosts, go one per line in `.local-refs-tokens` at the repo root,
which is git-ignored. Its absence is reported as a notice, not a failure, and the generic layer still
runs. Write your own when you take the fleet over.

## It runs whether or not you remember

`LocalRefsCheckTest` scans the repo as part of `./gradlew :embrace-analysis-local-refs:test`, so a local reference
fails the suite the same way a broken parity golden does. The command above is the fast way to see what it found.
