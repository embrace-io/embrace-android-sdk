# Updating the living docs without losing anyone's work

The startup investigation's docs exist **twice**: a source HTML/Markdown file under
`_shared/records/documents/` (committed) and a published artifact that people actually read. Nothing
keeps the two in step automatically, and they drift in **both** directions.

## The rule

> **Always reconcile before updating. Treat the PUBLISHED copy as the truth, unless you know you have
> modified the local copy since you last reconciled.**

"Unless you know" is not a memory exercise. Run the check:

```
tools/startup artifact-sync check .claude/skills/_shared/records/documents/<file>
```

| verdict | what it means | what to do |
|---|---|---|
| `UNKNOWN` | never published from here | **Fetch the published page first.** The local file may be missing changes it has never seen. |
| `CLEAN` | byte-identical to the last publish from here | The published copy is the truth. Another session may have moved it since, so fetch before a substantive edit. |
| `DIRTY` | edited locally since the last publish | Your edits are real. **Do not replace the file from the published copy** — merge. |

After every publish, record it so the next check can answer:

```
tools/startup artifact-sync record .claude/skills/_shared/records/documents/<file> <artifact-url> <YYYY-MM-DD>
```

(`tools/startup artifact-sync list` shows every tracked doc and its state.)

## Why this is not paranoia — both failure modes have happened

**Local ahead, published stale.** The statistics brief was updated locally after the reviewer asked
for it to be brought current, and never republished. The document they were reading stayed nine days
out of date, and the published artifact turned out to be a *separate hand-authored HTML document*
rather than a rendering of the Markdown — so the two were not even the same artefact. Worse, the
HTML's source was later destroyed by a `/private/tmp` purge, so the stale published copy was the
**only** surviving version and had to be recovered from the served page.

**Published ahead, local stale.** Two archived docs had ARCHIVED banners added
to the *published* copy by an earlier session that never saved them locally. Editing from the local
file and publishing would have **silently deleted those banners** — the operation looks completely
successful. The publish tool's same-session guard caught it by luck, not by design.

The second case is the dangerous one: a lost banner leaves a superseded document looking current.

## Practical notes

- **The publish tool demands a read when this session has not seen the artifact.** That guard is about
  *session provenance*, not content — hitting it does not mean someone else edited the page, only that
  you cannot yet prove they did not. Fetch, compare, then publish.
- **`WebFetch` on a `claude.ai/code/artifact/<uuid>` URL returns the full served HTML**, wrapped in the
  publishing shell. To recover a lost source, strip everything before the document's own `<title>` and
  the trailing `</body></html>`; refuse the result if `__FRAME_PREAMBLE` survives the strip.
- **A doc shared with the organisation updates for viewers immediately.** Check the fetch header — it
  states whether the artifact is private or org-shared — and treat org-shared pages as published the
  moment you press publish, not after review.
- **Docs and their numbers move together.** Tables, prose and the provenance stamp change in one edit,
  never separately; a table whose prose was not re-checked is how a document starts lying.
- **The sources and the manifest are committed** (`_shared/records/documents/` and
  `_shared/records/artifact-manifest.json`), so git history is the second line of defence; the
  manifest is still the only record that a local file and a published page were ever the same, which
  is why it is written beside the docs rather than kept in someone's head. Never write a source to
  `claude-output/` or a temp directory: that is how one was lost.
