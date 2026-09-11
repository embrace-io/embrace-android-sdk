-- Per-trace startup metrics, extracted purely from a macrobenchmark .perfetto-trace.
-- Executed per iteration trace by analyze_startup.py (one result-returning statement, hence
-- the UNION ALL). Rows: (what, k, val)
--   section        k = slice name    val = duration ms of the FIRST occurrence (Mode.First)
--   window_ms      SDK-init window. Preferred source: the emb-sdk-start slice, which wraps
--                  the whole Embrace.start() call (slightly wider — <1 ms — than the exported
--                  emb-embrace-init / private emb-sdk-init span). Fallback when emb-sdk-start
--                  is absent (SDKs before 9.2.0): first emb-modules-init start -> first
--                  emb-post-services-setup end, which equals the exported span interval by
--                  construction. window_source reports which one was used.
--   ttid_ms        cold-start TTID from the android.startup stdlib module (definition differs
--                  from macrobenchmark's timeToInitialDisplayMs by a few ms — consistent
--                  anchor, fine for comparisons, not identical to the JSON metric)
--   wait_ms        main-thread runnable-but-not-running (R / R+) time inside the window —
--                  the scheduler-contention signal
--   running_ms     main-thread Running time inside the window
--   cpus           distinct CPUs the main thread ran on inside the window
INCLUDE PERFETTO MODULE android.startup.startups;
WITH firsts AS (
  SELECT s.name AS name, s.ts AS ts, s.dur AS dur,
         ROW_NUMBER() OVER (PARTITION BY s.name ORDER BY s.ts) AS rn
  FROM slice s
  WHERE s.name GLOB 'emb-*'
),
w AS (
  SELECT ws, we, src FROM (
    SELECT ts AS ws, ts + dur AS we, 'emb-sdk-start' AS src, 0 AS pri
    FROM firsts WHERE name = 'emb-sdk-start' AND rn = 1
    UNION ALL
    SELECT m.ts AS ws, p.ts + p.dur AS we, 'composed' AS src, 1 AS pri
    FROM (SELECT ts FROM firsts WHERE name = 'emb-modules-init' AND rn = 1) m,
         (SELECT ts, dur FROM firsts WHERE name = 'emb-post-services-setup' AND rn = 1) p
  ) ORDER BY pri LIMIT 1
),
mt AS (
  SELECT tt.utid AS utid
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name = 'emb-modules-init'
  ORDER BY s.ts LIMIT 1
)
SELECT 'section' AS what, name AS k, CAST(dur / 1e6 AS REAL) AS val
FROM firsts WHERE rn = 1
UNION ALL
SELECT 'window_ms' AS what, '' AS k, CAST((we - ws) / 1e6 AS REAL) AS val FROM w
UNION ALL
SELECT 'window_source' AS what, src AS k, 0.0 AS val FROM w
UNION ALL
SELECT 'ttid_ms' AS what, '' AS k,
       (SELECT CAST(dur / 1e6 AS REAL) FROM android_startups ORDER BY ts LIMIT 1) AS val
UNION ALL
SELECT 'wait_ms' AS what, '' AS k,
       COALESCE((SELECT CAST(SUM(MIN(ts.ts + ts.dur, w.we) - MAX(ts.ts, w.ws)) / 1e6 AS REAL)
                 FROM thread_state ts, w, mt
                 WHERE ts.utid = mt.utid AND ts.state IN ('R', 'R+')
                   AND ts.ts < w.we AND ts.ts + ts.dur > w.ws), 0.0) AS val
UNION ALL
SELECT 'running_ms' AS what, '' AS k,
       COALESCE((SELECT CAST(SUM(MIN(ts.ts + ts.dur, w.we) - MAX(ts.ts, w.ws)) / 1e6 AS REAL)
                 FROM thread_state ts, w, mt
                 WHERE ts.utid = mt.utid AND ts.state = 'Running'
                   AND ts.ts < w.we AND ts.ts + ts.dur > w.ws), 0.0) AS val
UNION ALL
SELECT 'cpus' AS what, '' AS k,
       (SELECT CAST(COUNT(DISTINCT sched.cpu) AS REAL)
        FROM sched, w, mt
        WHERE sched.utid = mt.utid AND sched.ts < w.we AND sched.ts + sched.dur > w.ws) AS val;
