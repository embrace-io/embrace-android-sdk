package io.embrace.android.embracesdk.capture.startup

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLoggerAction
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceEmitterTest {
    private var startupService: StartupService? = null
    private lateinit var clock: FakeClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var loggerAction: FakeLoggerAction
    private lateinit var logger: InternalEmbraceLogger
    private lateinit var backgroundWorker: BackgroundWorker
    private lateinit var appStartupTraceEmitter: AppStartupTraceEmitter

    @Before
    fun setUp() {
        clock = FakeClock()
        val initModule = FakeInitModule(clock = clock)
        backgroundWorker = BackgroundWorker(BlockableExecutorService())
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        startupService = StartupServiceImpl(
            spanService,
            backgroundWorker
        )
        clock.tick(100L)
        loggerAction = FakeLoggerAction()
        logger = InternalEmbraceLogger().apply { addLoggerAction(loggerAction) }
        appStartupTraceEmitter = AppStartupTraceEmitter(
            clock = initModule.openTelemetryClock,
            startupServiceProvider = { startupService },
            spanService = spanService,
            backgroundWorker = backgroundWorker,
            versionChecker = BuildVersionChecker,
            logger = logger
        )
        loggerAction.msgQueue.clear()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `no crashes if startup service not available in T`() {
        startupService = null
        appStartupTraceEmitter.firstFrameRendered()
        assertEquals(1, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with every event triggered in T`() {
        clock.tick(100L)
        appStartupTraceEmitter.applicationInitStart()
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        appStartupTraceEmitter.applicationInitEnd()
        val applicationInitEnd = clock.now()
        clock.tick(50L)
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed()
        clock.tick(199L)
        appStartupTraceEmitter.firstFrameRendered()
        val traceEnd = clock.now()

        assertEquals(7, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val processInit = checkNotNull(spanMap["emb-process-init"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        with(trace) {
            assertEquals(FakeClock.DEFAULT_FAKE_CURRENT_TIME, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }
        with(processInit) {
            assertEquals(FakeClock.DEFAULT_FAKE_CURRENT_TIME, startTimeNanos.nanosToMillis())
            assertEquals(applicationInitEnd, endTimeNanos.nanosToMillis())
        }
        with(embraceInit) {
            assertEquals(sdkInitStart, startTimeNanos.nanosToMillis())
            assertEquals(sdkInitEnd, endTimeNanos.nanosToMillis())
        }
        with(activityInitDelay) {
            assertEquals(applicationInitEnd, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityStart, endTimeNanos.nanosToMillis())
        }
        with(activityCreate) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityEnd, endTimeNanos.nanosToMillis())
        }
        with(firstRender) {
            assertEquals(startupActivityEnd, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }

        assertEquals(0, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace without application init start and end triggered in T`() {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed()
        clock.tick(199L)
        appStartupTraceEmitter.firstFrameRendered()
        val traceEnd = clock.now()

        assertEquals(6, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        with(trace) {
            assertEquals(FakeClock.DEFAULT_FAKE_CURRENT_TIME, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }
        with(embraceInit) {
            assertEquals(sdkInitStart, startTimeNanos.nanosToMillis())
            assertEquals(sdkInitEnd, endTimeNanos.nanosToMillis())
        }
        with(activityInitDelay) {
            assertEquals(sdkInitEnd, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityStart, endTimeNanos.nanosToMillis())
        }
        with(activityCreate) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityEnd, endTimeNanos.nanosToMillis())
        }
        with(firstRender) {
            assertEquals(startupActivityEnd, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }

        assertEquals(0, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace without application init start and end triggered in T`() {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd)
        clock.tick(60001L)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed()
        clock.tick(199L)
        appStartupTraceEmitter.firstFrameRendered()
        val traceEnd = clock.now()

        assertEquals(4, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-warm-time-to-initial-display"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        with(trace) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
            assertEquals("60001", attributes["emb.activity-init-gap-ms"])
            assertEquals("30", attributes["emb.embrace-init-duration-ms"])
        }
        with(activityCreate) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityEnd, endTimeNanos.nanosToMillis())
        }
        with(firstRender) {
            assertEquals(startupActivityEnd, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }

        assertEquals(0, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `no crashes if startup service not available in L`() {
        startupService = null
        appStartupTraceEmitter.startupActivityResumed()
        assertEquals(1, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with every event triggered in L`() {
        clock.tick(100L)
        appStartupTraceEmitter.applicationInitStart()
        val applicationInitStart = clock.now()
        clock.tick(10L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        appStartupTraceEmitter.applicationInitEnd()
        val applicationInitEnd = clock.now()
        clock.tick(50L)
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed()
        val traceEnd = clock.now()

        assertEquals(7, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val processInit = checkNotNull(spanMap["emb-process-init"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val activityResume = checkNotNull(spanMap["emb-activity-resume"])

        with(trace) {
            assertEquals(applicationInitStart, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }
        with(processInit) {
            assertEquals(applicationInitStart, startTimeNanos.nanosToMillis())
            assertEquals(applicationInitEnd, endTimeNanos.nanosToMillis())
        }
        with(embraceInit) {
            assertEquals(sdkInitStart, startTimeNanos.nanosToMillis())
            assertEquals(sdkInitEnd, endTimeNanos.nanosToMillis())
        }
        with(activityInitDelay) {
            assertEquals(applicationInitEnd, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityStart, endTimeNanos.nanosToMillis())
        }
        with(activityCreate) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityEnd, endTimeNanos.nanosToMillis())
        }
        with(activityResume) {
            assertEquals(startupActivityEnd, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }

        assertEquals(0, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace without application init start and end triggered in L`() {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed()
        val traceEnd = clock.now()

        assertEquals(6, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val activityResume = checkNotNull(spanMap["emb-activity-resume"])

        with(trace) {
            assertEquals(sdkInitStart, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }
        with(embraceInit) {
            assertEquals(sdkInitStart, startTimeNanos.nanosToMillis())
            assertEquals(sdkInitEnd, endTimeNanos.nanosToMillis())
        }
        with(activityInitDelay) {
            assertEquals(sdkInitEnd, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityStart, endTimeNanos.nanosToMillis())
        }
        with(activityCreate) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityEnd, endTimeNanos.nanosToMillis())
        }
        with(activityResume) {
            assertEquals(startupActivityEnd, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }

        assertEquals(0, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify warm start trace without application init start and end triggered in L`() {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd)
        clock.tick(60001L)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed()
        val traceEnd = clock.now()

        assertEquals(4, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-warm-time-to-initial-display"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val activityResume = checkNotNull(spanMap["emb-activity-resume"])

        with(trace) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
            assertEquals("60001", attributes["emb.activity-init-gap-ms"])
            assertEquals("30", attributes["emb.embrace-init-duration-ms"])
        }
        with(activityCreate) {
            assertEquals(startupActivityStart, startTimeNanos.nanosToMillis())
            assertEquals(startupActivityEnd, endTimeNanos.nanosToMillis())
        }
        with(activityResume) {
            assertEquals(startupActivityEnd, startTimeNanos.nanosToMillis())
            assertEquals(traceEnd, endTimeNanos.nanosToMillis())
        }

        assertEquals(0, loggerAction.msgQueue.size)
    }
}
