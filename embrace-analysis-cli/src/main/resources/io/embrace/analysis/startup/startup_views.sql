-- The views the startup analysis reads one trace through, created by StartupMetrics once the trace is loaded. Times are
-- in ns.
--   emb_first_sections   the first occurrence (Mode.First) of every emb-* slice: name, ts, dur
--   emb_startup_window   the SDK-init window, the emb-sdk-start slice, which wraps the whole Embrace.start() call
--                        (slightly wider, <1 ms, than the exported emb-embrace-init / private emb-sdk-init span): its
--                        ts, dur and src, the slice it came from; and, for the thread that runs SDK init, its runnable
--                        wait (R / R+, the scheduler-contention signal) and running time inside the window, and the
--                        distinct CPUs it ran on there
--   emb_trace_counters   trace_processor's non-zero data-loss and error counters: name, idx (the buffer, or NULL),
--                        severity ('data_loss' or 'error') and value
--   emb_trace_capture    one row on what the capture can vouch for: sched_rows, the scheduling rows it holds;
--                        sched_start, ftrace_latest_data_start_ns, from when every CPU's scheduling data is complete (0
--                        when no ring wrapped, NULL when the tracing service doesn't report it); window_start, the
--                        window's ts; loss_reported, whether every buffer's stats have traced_buf_data_loss_overwrite,
--                        the data a wrap overwrote before it was read (some captures' stats, and older tracing
--                        services', don't); buffer_wraps, the chunks a wrap overwrote, whether or not they had been
--                        read; and stats_after_data, whether the buffer stats were recorded after any data was written
--                        (a snapshot taken before the data arrived counts nothing at all)
-- The cold start's TTID comes from the android.startup module's android_startups. Its anchor differs from
-- macrobenchmark's timeToInitialDisplayMs by a few ms, consistently: fine for comparisons, not identical to the JSON
-- metric.
-- Older SDKs (.claude/skills/startup-analysis/references/section-history.md): without emb-sdk-start, the window is
-- composed from the first emb-modules-init start to the first emb-post-services-setup end, the exported span's interval.
INCLUDE PERFETTO MODULE android.startup.startups;

CREATE PERFETTO VIEW emb_first_sections AS
SELECT name, ts, dur FROM (
  SELECT s.name AS name, s.ts AS ts, s.dur AS dur,
         ROW_NUMBER() OVER (PARTITION BY s.name ORDER BY s.ts) AS rn
  FROM slice s
  WHERE s.name GLOB 'emb-*'
) WHERE rn = 1;

CREATE PERFETTO VIEW emb_startup_window AS
WITH w AS (
  SELECT ws, we, src FROM (
    SELECT ts AS ws, ts + dur AS we, 'emb-sdk-start' AS src, 0 AS pri
    FROM emb_first_sections WHERE name = 'emb-sdk-start'
    UNION ALL
    -- Older SDKs (see the header): the composed window, used only when there is no emb-sdk-start
    SELECT m.ts AS ws, p.ts + p.dur AS we, 'composed' AS src, 1 AS pri
    FROM (SELECT ts FROM emb_first_sections WHERE name = 'emb-modules-init') m,
         (SELECT ts, dur FROM emb_first_sections WHERE name = 'emb-post-services-setup') p
  ) ORDER BY pri LIMIT 1
),
init_thread AS (
  SELECT tt.utid AS utid
  FROM slice s JOIN thread_track tt ON s.track_id = tt.id
  WHERE s.name = 'emb-modules-init'
  ORDER BY s.ts LIMIT 1
)
SELECT w.ws AS ts, w.we - w.ws AS dur, w.src AS src,
       COALESCE((SELECT SUM(MIN(t.ts + t.dur, w.we) - MAX(t.ts, w.ws))
                 FROM thread_state t, init_thread it
                 WHERE t.utid = it.utid AND t.state IN ('R', 'R+')
                   AND t.ts < w.we AND t.ts + t.dur > w.ws), 0) AS wait,
       COALESCE((SELECT SUM(MIN(t.ts + t.dur, w.we) - MAX(t.ts, w.ws))
                 FROM thread_state t, init_thread it
                 WHERE t.utid = it.utid AND t.state = 'Running'
                   AND t.ts < w.we AND t.ts + t.dur > w.ws), 0) AS running,
       (SELECT COUNT(DISTINCT c.cpu)
        FROM sched c, init_thread it
        WHERE c.utid = it.utid AND c.ts < w.we AND c.ts + c.dur > w.ws) AS cpus
FROM w;

CREATE PERFETTO VIEW emb_trace_counters AS
SELECT name, idx, severity, value
FROM stats
WHERE value > 0 AND severity IN ('data_loss', 'error');

CREATE PERFETTO VIEW emb_trace_capture AS
SELECT (SELECT COUNT(*) FROM sched) AS sched_rows,
       (SELECT int_value FROM metadata WHERE name = 'ftrace_latest_data_start_ns') AS sched_start,
       (SELECT ts FROM emb_startup_window) AS window_start,
       (SELECT COUNT(DISTINCT idx) FROM stats WHERE name = 'traced_buf_data_loss_overwrite')
         = (SELECT COUNT(DISTINCT idx) FROM stats WHERE name = 'traced_buf_chunks_overwritten') AS loss_reported,
       (SELECT COALESCE(SUM(value), 0) FROM stats WHERE name = 'traced_buf_chunks_overwritten') AS buffer_wraps,
       (SELECT COALESCE(SUM(value), 0) FROM stats WHERE name = 'traced_buf_bytes_written') > 0 AS stats_after_data;
