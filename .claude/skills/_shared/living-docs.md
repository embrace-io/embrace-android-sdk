# Updating the living docs without losing anyone's work

The startup investigation's docs exist **twice**: a local HTML/Markdown file under `claude-output/`
and a published artifact that people actually read. Nothing keeps the two in step automatically, and
they drift in **both** directions.

## The rule

> **Always reconcile before updating. Treat the PUBLISHED copy as the truth, unless you know you have
> modified the local copy since you last reconciled.**

"Unless you know" is not a memory exercise. Run the check:

```
python3 .claude/skills/_shared/artifact_sync.py check claude-output/<file>
```

| verdict | what it means | what to do |
|---|---|---|
| `UNKNOWN` | never published from here | **Fetch the published page first.** The local file may be missing changes it has never seen. |
| `CLEAN` | byte-identical to the last publish from here | The published copy is the truth. Another session may have moved it since, so fetch before a substantive edit. |
| `DIRTY` | edited locally since the last publish | Your edits are real. **Do not replace the file from the published copy** — merge. |

After every publish, record it so the next check can answer:

```
python3 .claude/skills/_shared/artifact_sync.py record claude-output/<file> <artifact-url> <YYYY-MM-DD>
```

## Why this is not paranoia — both failure modes have happened

**Local ahead, published stale (2026-08-17 → 08-26).** The statistics brief was updated locally after
Hanson asked for it to be brought current, and never republished. The document he was reading stayed
nine days out of date, and the published artifact turned out to be a *separate hand-authored HTML
document* rather than a rendering of the Markdown — so the two were not even the same artefact. Worse,
the HTML's source was later destroyed by a `/private/tmp` purge, so the stale published copy was the
**only** surviving version and had to be recovered from the served page.

**Published ahead, local stale (discovered 2026-08-26).** Two archived docs had ARCHIVED banners added
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
- **`claude-output/` is gitignored**, so none of this is protected by version control. The manifest is
  the only record that a local file and a published page were ever the same, which is exactly why it
  is written beside the docs rather than kept in someone's head.
