---
name: changelog-entry
description: Draft the markdown changelog entry for the next embrace-android-sdk release from the commits since the last release tag, in the style of https://embrace.io/docs/android/changelog/. Groups related PRs into user-facing bullets and moves changes that don't matter to SDK consumers into a separate section. Use when asked to write, draft, or generate release notes or a changelog entry.
---

# Changelog entry

Produces a draft changelog entry for the upcoming release. The output is a starting point for
a human editor, not a finished artifact — always finish by asking the user to review it.

## 1. Collect the changes

Run the draft script from the repo root. It finds the last release tag below the version in
`gradle.properties`, lists the first-parent commits since that tag (excluding fixes that already
shipped in a hotfix), and splits out dependabot bumps:

```sh
./scripts/draft-changelog-entries.sh [version]
```

Release tags live on release branches and are not reachable from `main`, so `git describe` returns
a stale tag. Don't use it; rely on the script, or on `git tag --sort=-v:refname` if you need the
tag directly. Tell the user which tag the entry is measured from.

## 2. Understand each PR

Commit subjects are often too terse to describe user impact. For any PR whose effect on SDK
consumers is unclear, read it:

```sh
gh pr view <number> --json title,body,files
```

Fall back to `git show --stat <sha>` if `gh` is unavailable. Fetch PRs in parallel where possible.

## 3. Classify

**Consumer-facing** — anything an app developer integrating the SDK would notice or care about:
new or changed public APIs, new configuration, new or changed telemetry, bug fixes with
observable symptoms, performance and memory improvements, deprecations and removals, minimum
version changes (Kotlin, AGP, Gradle, JDK, compileSdk, minSdk), Gradle plugin behaviour, and
updates to dependencies that ship in the SDK's public surface (chiefly OpenTelemetry Java and
OpenTelemetry Kotlin).

**Internal** — everything else: tests, CI and release workflows, refactors with no behaviour
change, build tooling, lint/detekt, docs inside the repo, example app changes, benchmarks, and
dependency bumps of test or build-only libraries.

When unsure, put the change in the consumer-facing list and flag it in the review prompt.

## 4. Write the entry

Match the published changelog:

- `### <version>` heading, then the release date in italics on its own line, written as
  `_Month D, YYYY_` (use today's date; the editor will change it if the release slips).
- A flat bulleted list. No "Features"/"Bug fixes" subsections.
- One bullet per user-facing change. Merge related PRs into a single bullet — e.g. several
  startup optimisations become one "Improved SDK startup performance…" bullet that summarises
  what changed, and a feature built across multiple PRs is one bullet.
- Terse and factual. Start with a past-tense verb ("Added", "Fixed", "Reduced", "Removed") or
  a short noun phrase. Describe the effect on the app, not the implementation.
- Wrap API names, config keys, and version numbers in backticks.
- No PR links in the consumer-facing list.
- Order: new features, then improvements, then bug fixes, then dependency/version updates last.
- OpenTelemetry bumps use the fixed wording ``Update OpenTelemetry Java API and SDK to `x.y.z` ``
  and ``Update OpenTelemetry Kotlin API to `x.y.z` ``. If several bumps landed, list only the final
  version.
- Breaking changes, raised minimum versions, or anything requiring the user to act get an
  `Important` callout above the bullets, explaining what to change. Mention the Upgrade Guide
  for major versions.

Then add the internal section below a separator, with PR links so the editor can trace each
item. It is for review only and is not published:

```markdown
### 9.4.0

_October 15, 2026_

- Added …
- Fixed …
- Update OpenTelemetry Java API and SDK to `1.67.0`

---

#### Internal changes (not for publication)

- Migrated release workflows to a single version source ([#4425](https://github.com/embrace-io/embrace-android-sdk/pull/4425))
- Bumped test dependencies: … ([#4410](…), [#4412](…))
```

Group the internal items too, but keep them brief.

## 5. Output and ask for review

Print the entry as a single fenced ```` ```markdown ```` block so it can be copied verbatim. Do not
write it to a file or open a PR unless asked.

Then prompt the user to review it manually, calling out specifically:

- Any change you were unsure how to classify, or whose user impact you inferred.
- Bullets that merge several PRs, so they can check nothing was lost.
- Whether an `Important` callout is needed (or whether the one you added is warranted).
- The version number and date.
