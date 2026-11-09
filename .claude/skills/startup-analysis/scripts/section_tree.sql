-- The sdk-init section tree of ONE startup trace: the first occurrence of every emb-* slice up to the end of SDK init,
-- with its depth, parent and thread, in start order. Used to record the tree in references/sections.md and the
-- per-release snapshots in references/section-history.md. Run with perfetto trace_processor
-- (https://get.perfetto.dev/trace_processor is a python3 launcher script):
--   python3 trace_processor -q section_tree.sql <iteration>.perfetto-trace
-- SDK init ends with emb-sdk-start, or on SDKs without it, shortly after emb-post-services-setup. Slices on other
-- threads (thread is not the app's main thread) run alongside SDK init and are not part of its tree.
WITH s AS (
  SELECT s.id, s.name, s.ts, s.dur, s.depth, s.parent_id, tt.utid,
         ROW_NUMBER() OVER (PARTITION BY s.name ORDER BY s.ts) AS rn
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name GLOB 'emb-*'
),
endts AS (
  SELECT COALESCE(
    (SELECT ts + dur FROM slice WHERE name = 'emb-sdk-start' ORDER BY ts LIMIT 1),
    (SELECT ts + dur + 5000000 FROM slice WHERE name = 'emb-post-services-setup' ORDER BY ts LIMIT 1)
  ) AS e
)
SELECT s.depth AS depth, s.name AS name, p.name AS parent, t.name AS thread,
       CAST(s.dur / 1e6 AS REAL) AS ms
FROM s
LEFT JOIN slice p ON s.parent_id = p.id
JOIN thread t ON t.utid = s.utid, endts
WHERE s.rn = 1 AND s.ts <= endts.e
ORDER BY s.ts;
