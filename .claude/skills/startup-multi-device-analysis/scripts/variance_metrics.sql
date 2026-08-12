-- Per-iteration variance dataset: window, per-CPU residency of the main thread inside the
-- window, first-occurrence duration of every emb-* section, and the thread-state breakdown
-- (Running vs sleeping vs uninterruptible/IO) of the emitting thread inside each section.
-- One result-returning statement (UNION ALL) per trace_processor -q constraints.
-- Driver: variance_analysis.py (same directory).
WITH w AS (
  SELECT s.ts AS ws, s.ts + s.dur AS we, tt.utid AS utid
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name = 'emb-sdk-start'
  ORDER BY s.ts LIMIT 1
),
firsts AS (
  SELECT s.name AS name, MIN(s.ts) AS mts
  FROM slice s WHERE s.name LIKE 'emb-%' GROUP BY s.name
),
fs AS (
  SELECT f.name AS name, s.ts AS ts, s.dur AS dur, tt.utid AS utid
  FROM firsts f
  JOIN slice s ON s.name = f.name AND s.ts = f.mts
  JOIN thread_track tt ON s.track_id = tt.id
)
SELECT 'window_ms' AS what, '' AS k, CAST((we - ws) / 1e6 AS REAL) AS val FROM w
UNION ALL
SELECT 'cpu_ms' AS what, CAST(sched.cpu AS TEXT) AS k,
       CAST(SUM(MIN(sched.ts + sched.dur, w.we) - MAX(sched.ts, w.ws)) / 1e6 AS REAL) AS val
FROM sched, w
WHERE sched.utid = w.utid AND sched.ts < w.we AND sched.ts + sched.dur > w.ws
GROUP BY sched.cpu
UNION ALL
SELECT 'dur' AS what, name AS k, CAST(dur / 1e6 AS REAL) AS val FROM fs
UNION ALL
SELECT 'st:' || ts.state || CASE WHEN ts.io_wait = 1 THEN '+io' ELSE '' END AS what,
       fs.name AS k,
       CAST(SUM(MIN(ts.ts + ts.dur, fs.ts + fs.dur) - MAX(ts.ts, fs.ts)) / 1e6 AS REAL) AS val
FROM thread_state ts, fs
WHERE ts.utid = fs.utid AND ts.ts < fs.ts + fs.dur AND ts.ts + ts.dur > fs.ts
GROUP BY fs.name, ts.state, ts.io_wait;
