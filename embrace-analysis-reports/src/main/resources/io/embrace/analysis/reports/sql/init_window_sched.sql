-- Scheduling analysis of the main thread over the emb-modules-init window, for diagnosing
-- slow-pass variance (contention vs thermal). Run with perfetto trace_processor
-- (https://get.perfetto.dev/trace_processor is a python3 launcher script):
--   python3 trace_processor -q init_window_sched.sql <iteration>.perfetto-trace
-- Interpretation: high R/R+ thread_state time + residency spread over many CPUs means the
-- iteration lost time to scheduler contention (check competing threads with a sched query
-- excluding the window utid), not SDK code. trace_processor allows one result-returning
-- statement per file, hence the UNION ALL.
WITH w AS (
  SELECT s.ts AS ws, s.ts + s.dur AS we, tt.utid AS utid
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name = 'emb-modules-init'
  ORDER BY s.ts LIMIT 1
)
SELECT 'window_ms' AS what, '' AS k, CAST((we - ws) / 1e6 AS REAL) AS val FROM w
UNION ALL
SELECT 'thread_state_ms' AS what, ts.state AS k,
       CAST(SUM(MIN(ts.ts + ts.dur, w.we) - MAX(ts.ts, w.ws)) / 1e6 AS REAL) AS val
FROM thread_state ts, w
WHERE ts.utid = w.utid AND ts.ts < w.we AND ts.ts + ts.dur > w.ws
GROUP BY ts.state
UNION ALL
SELECT 'cpu_residency_ms' AS what, CAST(sched.cpu AS TEXT) AS k,
       CAST(SUM(MIN(sched.ts + sched.dur, w.we) - MAX(sched.ts, w.ws)) / 1e6 AS REAL) AS val
FROM sched, w
WHERE sched.utid = w.utid AND sched.ts < w.we AND sched.ts + sched.dur > w.ws
GROUP BY sched.cpu;
