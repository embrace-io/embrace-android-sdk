-- Per-trace catalogue of external/on-device factors inside the SDK-init window
-- (emb-sdk-start on the main thread): effective CPU frequency actually delivered,
-- per-cluster clocks at window start, thread-state with D-state blocked_function
-- attribution, ART runtime work (class verify/load, lock contention), GC, in-process
-- competitor threads, other-process CPU pressure, binder activity, swap level.
-- Driver: outlier_factors.py; correlation/report: factors_report.py.
-- run_cl0_ms/run_cl1_ms split at cpu<4 (a fixed SQL partition, not a topology lookup);
-- downstream tools (e.g. factors_report.py, hypothesis_tests.py) remap this partition's
-- semantics against the device's actual little/big cluster membership (see
-- device_probe.py's little_cpus) rather than assuming cpu<4 == little on every device.
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
-- NOTE: per-class 'Lfoo;' load slices exist on ART 12+ only. On ART 10 (e.g. Android 10
-- Go) this returns 0 — the runtime emits only VerifyClass + lock-contention slices there.
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
SELECT 'gc_slice_ms' AS what, '' AS k,
       CAST(COALESCE(SUM(MIN(s.ts + s.dur, w.we) - MAX(s.ts, w.ws)), 0) / 1e6 AS REAL) AS val
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread th ON tt.utid = th.utid, w, me
WHERE th.upid = me.upid AND s.ts < w.we AND s.ts + s.dur > w.ws
  AND s.name LIKE '%GC%'
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
