-- Foreign-process GC overlap inside the SDK-init window (emb-sdk-start on the main thread).
-- Companion to outlier_metrics.sql's `gc_slice_ms`, which is scoped to the app's OWN upid
-- by design (per the anchoring rule: never credit another process's work to this app, and
-- never discard it either -- it is a distinct, legitimate question). This query answers the
-- other half: was some OTHER process (system_server, GMS, dex2oat, ...) doing GC-related
-- work that overlapped our window, and what KIND of GC-related work was it.
--
-- Built for item B1 ("13/13 slow windows showed system_server heap-compaction GC overlap"),
-- which was never re-derived under the corrected rules because the combined query scoped GC
-- to the app's own upid. See interpreting-results.md's "Trace-capture and trace_processor
-- traps" for the general rule this instance follows.
--
-- THREE guards are required here, one more than the own-process query:
--   1. GLOB, not LIKE (case-sensitive). Obfuscated class-load slices in the FOREIGN process
--      (e.g. system_server's own Lgc;/Lgc0; if it has obfuscated classes, or any other
--      process's) fabricate a "GC" flag exactly as they did in-process -- this is a general
--      substring-matching failure, not a property of which process is being scoped.
--   2. Match the collection, not the substring. ART names a real collection so it ENDS in
--      'GC' ('Background concurrent copying GC', 'Background young concurrent mark compact
--      GC', ...). Exclude 'Lock contention*' -- contention ON the collector's locks, not a
--      collection.
--   3. NEW for foreign processes: exclude and separately count 'scheduleAppGcs*'. This is a
--      system_server call that ASKS other apps' GC daemons to run (a scheduling/IPC slice on
--      system_server's own thread) -- not a collection system_server performed itself. It is
--      a distinct, previously-observed LIKE-era false-positive family (see
--      interpreting-results.md) and a different claim from "system_server was collecting":
--      "system_server was contending on its GC lock" vs "system_server was scheduling other
--      apps' GCs" vs "system_server was collecting" are three different mechanisms, and
--      collapsing any of the first two into the third is exactly the failure mode B1's
--      original "13/13 heap-compaction" wording risked.
--
-- Every bucket below is reported separately -- nothing is silently dropped. Compare
-- foreign_gc_like_raw_ms (the old defect's magnitude) -> foreign_gc_glob_raw_ms (after guard
-- 1) -> foreign_gc_collection_ms (after guards 2+3) to see the predicate fix's effect, with
-- foreign_gc_lock_contention_ms and foreign_scheduleAppGcs_ms as the two exclusions broken
-- out on their own so "contention" and "scheduling" claims are never silently reclassified
-- as "collection" claims.
--
-- Requires atrace_categories: "dalvik" for the GC slices themselves (a capability fact -- see
-- outlier_metrics.sql's note on gc_slice_ms) and full process-name resolution for readable
-- `process_name` values; where process_stats didn't resolve a name, the thread name is used
-- as a fallback and NULL/'?' means neither resolved (still correctly scoped by upid, just
-- unlabeled).
--
-- One result-returning statement (UNION ALL); rows are (what, process_name, val_ms | val_n).
WITH w AS (
  SELECT s.ts AS ws, s.ts + s.dur AS we, tt.utid AS utid
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name = 'emb-sdk-start'
  ORDER BY s.ts LIMIT 1
),
me AS (SELECT th.upid AS upid FROM thread th, w WHERE th.utid = w.utid),
foreign_slices AS (
  SELECT s.ts AS ts, s.dur AS dur, s.name AS name,
         COALESCE(p.name, th.name, '?') AS process_name
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread th ON tt.utid = th.utid
  LEFT JOIN process p ON th.upid = p.upid, w, me
  WHERE th.upid != me.upid AND s.ts < w.we AND s.ts + s.dur > w.ws
)
-- Bucket 0: the old defect's magnitude, uncorrected -- case-insensitive substring match.
SELECT 'foreign_gc_like_raw_ms' AS what, process_name AS k,
       CAST(SUM(MIN(ts + dur, (SELECT we FROM w)) - MAX(ts, (SELECT ws FROM w))) / 1e6 AS REAL) AS val
FROM foreign_slices WHERE name LIKE '%GC%' GROUP BY process_name
UNION ALL
SELECT 'foreign_gc_like_raw_n' AS what, process_name AS k, CAST(COUNT(*) AS REAL) AS val
FROM foreign_slices WHERE name LIKE '%GC%' GROUP BY process_name
UNION ALL
-- Bucket 1: after guard 1 only (case-sensitive, still a bare substring match).
SELECT 'foreign_gc_glob_raw_ms' AS what, process_name AS k,
       CAST(SUM(MIN(ts + dur, (SELECT we FROM w)) - MAX(ts, (SELECT ws FROM w))) / 1e6 AS REAL) AS val
FROM foreign_slices WHERE name GLOB '*GC*' GROUP BY process_name
UNION ALL
SELECT 'foreign_gc_glob_raw_n' AS what, process_name AS k, CAST(COUNT(*) AS REAL) AS val
FROM foreign_slices WHERE name GLOB '*GC*' GROUP BY process_name
UNION ALL
-- Bucket 2: the corrected predicate -- real collections only, after all three guards.
SELECT 'foreign_gc_collection_ms' AS what, process_name AS k,
       CAST(SUM(MIN(ts + dur, (SELECT we FROM w)) - MAX(ts, (SELECT ws FROM w))) / 1e6 AS REAL) AS val
FROM foreign_slices
WHERE name GLOB '*GC' AND name NOT GLOB 'Lock contention*' AND name NOT GLOB 'scheduleAppGcs*'
GROUP BY process_name
UNION ALL
SELECT 'foreign_gc_collection_n' AS what, process_name AS k, CAST(COUNT(*) AS REAL) AS val
FROM foreign_slices
WHERE name GLOB '*GC' AND name NOT GLOB 'Lock contention*' AND name NOT GLOB 'scheduleAppGcs*'
GROUP BY process_name
UNION ALL
-- Exclusion 1, reported on its own: contention ON the collector's lock -- not a collection.
SELECT 'foreign_gc_lock_contention_ms' AS what, process_name AS k,
       CAST(SUM(MIN(ts + dur, (SELECT we FROM w)) - MAX(ts, (SELECT ws FROM w))) / 1e6 AS REAL) AS val
FROM foreign_slices WHERE name GLOB 'Lock contention*' AND name GLOB '*GC*' GROUP BY process_name
UNION ALL
SELECT 'foreign_gc_lock_contention_n' AS what, process_name AS k, CAST(COUNT(*) AS REAL) AS val
FROM foreign_slices WHERE name GLOB 'Lock contention*' AND name GLOB '*GC*' GROUP BY process_name
UNION ALL
-- Exclusion 2, reported on its own: system_server SCHEDULING another app's GC -- not a
-- collection system_server itself performed.
SELECT 'foreign_scheduleAppGcs_ms' AS what, process_name AS k,
       CAST(SUM(MIN(ts + dur, (SELECT we FROM w)) - MAX(ts, (SELECT ws FROM w))) / 1e6 AS REAL) AS val
FROM foreign_slices WHERE name GLOB 'scheduleAppGcs*' GROUP BY process_name
UNION ALL
SELECT 'foreign_scheduleAppGcs_n' AS what, process_name AS k, CAST(COUNT(*) AS REAL) AS val
FROM foreign_slices WHERE name GLOB 'scheduleAppGcs*' GROUP BY process_name;
