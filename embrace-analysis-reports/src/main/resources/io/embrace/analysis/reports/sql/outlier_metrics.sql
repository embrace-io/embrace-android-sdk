-- Per-trace catalogue of external/on-device factors inside the SDK-init window
-- (emb-sdk-start on the main thread): effective CPU frequency actually delivered,
-- per-cluster clocks at window start, thread-state with D-state blocked_function
-- attribution, ART runtime work (class verify/load, lock contention), GC, in-process
-- competitor threads, other-process CPU pressure, binder activity, swap level.
-- Driver: outlier_factors.py; correlation/report: factors_report.py.
-- TOPOLOGY ASSUMPTIONS — this file indexes clusters positionally, which is NOT portable:
--   * run_cl0_ms/run_cl1_ms split at cpu<4 (a fixed SQL partition, not a topology lookup);
--     downstream tools (factors_report.py, hypothesis_tests.py) remap this partition's
--     semantics against the device's actual little/big cluster membership (see
--     device_probe.py's little_cpus) rather than assuming cpu<4 == little everywhere.
--   * freq_cl0_mhz/freq_cl1_mhz sample cpu0 and cpu4 as cluster representatives; on a
--     device whose clusters do not split 4+4 these name two cpus in the same cluster.
--   * freq_limit_cl0/cl1 read 'Cpu 0/4 Max Freq Limit' counter tracks, which some kernels
--     do not emit at all — a NULL there means "not exported", not "unthrottled".
-- Read the probe's cluster map before interpreting any of these four.
-- One result-returning statement (UNION ALL); rows are (what, k, val).
WITH w AS (
  SELECT s.ts AS ws, s.ts + s.dur AS we, tt.utid AS utid
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name = 'emb-sdk-start'
  ORDER BY s.ts LIMIT 1
),
me AS (SELECT th.upid AS upid FROM thread th, w WHERE th.utid = w.utid),
msched AS (
  SELECT sched.ts AS ts, sched.cpu AS cpu,
         MIN(sched.ts + sched.dur, w.we) - MAX(sched.ts, w.ws) AS odur
  FROM sched, w
  WHERE sched.utid = w.utid AND sched.ts < w.we AND sched.ts + sched.dur > w.ws
),
mfreq AS (
  SELECT m.odur AS odur, m.cpu AS cpu,
         COALESCE(
           (SELECT c.value FROM counter c JOIN cpu_counter_track t ON c.track_id = t.id
            WHERE t.name = 'cpufreq' AND t.cpu = m.cpu AND c.ts <= m.ts
            ORDER BY c.ts DESC LIMIT 1),
           (SELECT c.value FROM counter c JOIN cpu_counter_track t ON c.track_id = t.id
            WHERE t.name = 'cpufreq' AND t.cpu = m.cpu AND c.ts > m.ts
            ORDER BY c.ts ASC LIMIT 1)
         ) AS freq
  FROM msched m
)
SELECT 'window_ms' AS what, '' AS k, CAST((we - ws) / 1e6 AS REAL) AS val FROM w
UNION ALL
SELECT 'eff_mhz' AS what, '' AS k,
       CAST(SUM(odur * freq) / SUM(odur) / 1000.0 AS REAL) AS val
FROM mfreq WHERE freq IS NOT NULL
UNION ALL
SELECT 'run_cl0_ms' AS what, '' AS k,
       CAST(SUM(CASE WHEN cpu < 4 THEN odur ELSE 0 END) / 1e6 AS REAL) AS val FROM msched
UNION ALL
SELECT 'run_cl1_ms' AS what, '' AS k,
       CAST(SUM(CASE WHEN cpu >= 4 THEN odur ELSE 0 END) / 1e6 AS REAL) AS val FROM msched
UNION ALL
SELECT 'freq_cl0_mhz' AS what, '' AS k,
       CAST((SELECT c.value / 1000.0 FROM counter c
             JOIN cpu_counter_track t ON c.track_id = t.id
             WHERE t.name = 'cpufreq' AND t.cpu = 0 AND c.ts <= w.ws
             ORDER BY c.ts DESC LIMIT 1) AS REAL) AS val FROM w
UNION ALL
SELECT 'freq_cl1_mhz' AS what, '' AS k,
       CAST((SELECT c.value / 1000.0 FROM counter c
             JOIN cpu_counter_track t ON c.track_id = t.id
             WHERE t.name = 'cpufreq' AND t.cpu = 4 AND c.ts <= w.ws
             ORDER BY c.ts DESC LIMIT 1) AS REAL) AS val FROM w
UNION ALL
SELECT 'state:' || ts.state || CASE WHEN ts.io_wait = 1 THEN '+io' ELSE '' END AS what,
       COALESCE(ts.blocked_function, '') AS k,
       CAST(SUM(MIN(ts.ts + ts.dur, w.we) - MAX(ts.ts, w.ws)) / 1e6 AS REAL) AS val
FROM thread_state ts, w
WHERE ts.utid = w.utid AND ts.ts < w.we AND ts.ts + ts.dur > w.ws
GROUP BY ts.state, ts.io_wait, ts.blocked_function
UNION ALL
SELECT 'art_verify_ms' AS what, '' AS k,
       CAST(COALESCE(SUM(MIN(s.ts + s.dur, w.we) - MAX(s.ts, w.ws)), 0) / 1e6 AS REAL) AS val
FROM slice s JOIN thread_track tt ON s.track_id = tt.id, w
WHERE tt.utid = w.utid AND s.ts < w.we AND s.ts + s.dur > w.ws
  AND s.name LIKE 'VerifyClass%'
UNION ALL
-- NOTE: per-class 'Lfoo;' load slices only exist on newer ART generations. On older ones
-- (around the Android 10 era, including Go editions) this returns 0 by construction — the
-- runtime emits only VerifyClass + lock-contention slices there. A 0 here is "not
-- instrumented on this ART generation", NOT "no class loading happened": check whether the
-- trace contains any 'L%;' slices at all before comparing this across devices.
SELECT 'art_classload_ms' AS what, '' AS k,
       CAST(COALESCE(SUM(MIN(s.ts + s.dur, w.we) - MAX(s.ts, w.ws)), 0) / 1e6 AS REAL) AS val
FROM slice s JOIN thread_track tt ON s.track_id = tt.id, w
WHERE tt.utid = w.utid AND s.ts < w.we AND s.ts + s.dur > w.ws
  AND s.name LIKE 'L%;' AND s.depth > 0
UNION ALL
SELECT 'lock_contention_ms' AS what, '' AS k,
       CAST(COALESCE(SUM(MIN(s.ts + s.dur, w.we) - MAX(s.ts, w.ws)), 0) / 1e6 AS REAL) AS val
FROM slice s JOIN thread_track tt ON s.track_id = tt.id, w
WHERE tt.utid = w.utid AND s.ts < w.we AND s.ts + s.dur > w.ws
  AND s.name LIKE 'Lock contention%'
UNION ALL
SELECT 'binder_txn_cnt' AS what, '' AS k, CAST(COUNT(*) AS REAL) AS val
FROM ftrace_event f, w
WHERE f.name = 'binder_transaction' AND f.utid = w.utid
  AND f.ts >= w.ws AND f.ts <= w.we
UNION ALL
-- Name-matching GC takes TWO guards, and each one has already produced a wrong published
-- number here:
--   1. GLOB, not LIKE. SQLite's LIKE is case-INSENSITIVE, so '%GC%' also matches class-load
--      slices for obfuscated classes. Measured: on every ART-12+ device in this repo's
--      corpus, 'Lgc;' (x147) and 'Lgc0;' (x134) fabricated a 100%-false own-GC presence
--      flag - 130 windows sampled, 0 real collections, and every trace flagged. The
--      durations were trivial (0.02-0.35 ms), so no duration claim moved, but every
--      *presence* claim built on it was void.
--   2. Match the collection, not the substring. Even case-sensitively, 41% of in-window
--      '*GC*' matches on an ART-10 Go device are 'Lock contention on GC barrier lock' -
--      contention ON the collector, not a collection. ART names a real collection so that
--      it ENDS in 'GC' ('Background concurrent copying GC', 'Explicit ... GC'), which is
--      what the predicate below requires.
-- Requires atrace_categories: "dalvik" in the trace config, otherwise this is empty by
-- construction rather than genuinely zero - a capability fact, not a measurement.
SELECT 'gc_slice_ms' AS what, '' AS k,
       CAST(COALESCE(SUM(MIN(s.ts + s.dur, w.we) - MAX(s.ts, w.ws)), 0) / 1e6 AS REAL) AS val
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread th ON tt.utid = th.utid, w, me
WHERE th.upid = me.upid AND s.ts < w.we AND s.ts + s.dur > w.ws
  AND s.name GLOB '*GC' AND s.name NOT GLOB 'Lock contention*'
UNION ALL
SELECT 'inproc' AS what, th.name AS k,
       CAST(SUM(MIN(sched.ts + sched.dur, w.we) - MAX(sched.ts, w.ws)) / 1e6 AS REAL) AS val
FROM sched JOIN thread th USING(utid), w, me
WHERE th.upid = me.upid AND th.utid != w.utid
  AND sched.ts < w.we AND sched.ts + sched.dur > w.ws
GROUP BY th.name
UNION ALL
SELECT 'othercpu' AS what, COALESCE(p.name, th.name, '?') AS k,
       CAST(SUM(MIN(sched.ts + sched.dur, w.we) - MAX(sched.ts, w.ws)) / 1e6 AS REAL) AS val
FROM sched JOIN thread th USING(utid) LEFT JOIN process p ON th.upid = p.upid, w, me
WHERE (th.upid IS NULL OR th.upid != me.upid)
  AND sched.ts < w.we AND sched.ts + sched.dur > w.ws
GROUP BY COALESCE(p.name, th.name, '?')
UNION ALL
SELECT 'freq_limit_cl0' AS what, '' AS k,
       CAST((SELECT AVG(c.value) FROM counter c
             JOIN counter_track t ON c.track_id = t.id
             WHERE t.name = 'Cpu 0 Max Freq Limit') AS REAL) AS val FROM w
UNION ALL
SELECT 'freq_limit_cl1' AS what, '' AS k,
       CAST((SELECT AVG(c.value) FROM counter c
             JOIN counter_track t ON c.track_id = t.id
             WHERE t.name = 'Cpu 4 Max Freq Limit') AS REAL) AS val FROM w
UNION ALL
SELECT 'mem_swap' AS what, '' AS k,
       CAST((SELECT c.value FROM counter c
             JOIN process_counter_track t ON c.track_id = t.id, me
             WHERE t.name = 'mem.swap' AND t.upid = me.upid AND c.ts <= w.we
             ORDER BY c.ts DESC LIMIT 1) AS REAL) AS val FROM w
UNION ALL
SELECT 'mem_available' AS what, '' AS k,
       CAST((SELECT AVG(c.value) FROM counter c
             JOIN counter_track t ON c.track_id = t.id
             WHERE t.name = 'MemAvailable') AS REAL) AS val FROM w;
