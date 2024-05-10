package io.embrace.android.embracesdk.capture.startup

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertDoesNotHaveEmbraceAttribute
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.arch.schema.KeySpan
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeLogAction
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The various combinations of OS capabilities mean we have to test the following Android versions:
 *
 *  - 14+:         cold start trace from process create to render, process requested time available
 *  - 10 to 13:    cold start trace from process create to render, process requested time NOT available
 *  - 7 to 9:      cold start trace from process create to activity resume, process requested time NOT available
 *  - 5 to 6.0.1:  cold start trace application init if available (sdk init if not) to activity resume
 */
@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceEmitterTest {
    private var startupService: StartupService? = null
    private lateinit var clock: FakeClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var loggerAction: FakeLogAction
    private lateinit var logger: EmbLoggerImpl
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
        loggerAction = FakeLogAction()
        logger = EmbLoggerImpl().apply { addLoggerAction(loggerAction) }
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
        verifyColdStartWithRender()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace without application init start and end triggered in T`() {
        verifyColdStartWithRenderWithoutAppInitEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace without application init start and end triggered in T`() {
        verifyWarmStartWithRenderWithoutAppInitEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `no crashes if startup service not available in S`() {
        startupService = null
        appStartupTraceEmitter.firstFrameRendered()
        assertEquals(1, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with every event triggered in S`() {
        verifyColdStartWithRender()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace without application init start and end triggered in S`() {
        verifyColdStartWithRenderWithoutAppInitEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace without application init start and end triggered in S`() {
        verifyWarmStartWithRenderWithoutAppInitEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `no crashes if startup service not available in P`() {
        startupService = null
        appStartupTraceEmitter.startupActivityResumed()
        assertEquals(1, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with every event triggered in P`() {
        verifyColdStartWithResume()
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace without application init start and end triggered in P`() {
        verifyColdStartWithResumeWithoutAppInitEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify warm start trace without application init start and end triggered in O`() {
        verifyWarmStartWithResumeWithoutAppInitEvents()
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `no crashes if startup service not available in M`() {
        startupService = null
        appStartupTraceEmitter.startupActivityResumed()
        assertEquals(1, loggerAction.msgQueue.size)
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace with every event triggered in M`() {
        verifyColdStartWithResume(trackProcessStart = false)
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace without application init start and end triggered in M`() {
        verifyColdStartWithResumeWithoutAppInitEvents(trackProcessStart = false)
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify warm start trace without application init start and end triggered in M`() {
        verifyWarmStartWithResumeWithoutAppInitEvents()
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
        verifyColdStartWithResume(trackProcessStart = false)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace without application init start and end triggered in L`() {
        verifyColdStartWithResumeWithoutAppInitEvents(trackProcessStart = false)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify warm start trace without application init start and end triggered in L`() {
        verifyWarmStartWithResumeWithoutAppInitEvents()
    }

    private fun verifyColdStartWithRender() {
        clock.tick(100L)
        appStartupTraceEmitter.applicationInitStart()
        clock.tick(15L)
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

        assertTraceRoot(trace, DEFAULT_FAKE_CURRENT_TIME, traceEnd)
        assertChildSpan(processInit, DEFAULT_FAKE_CURRENT_TIME, applicationInitEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, applicationInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, loggerAction.msgQueue.size)
    }

    private fun verifyColdStartWithRenderWithoutAppInitEvents() {
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

        assertTraceRoot(trace, DEFAULT_FAKE_CURRENT_TIME, traceEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, sdkInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, loggerAction.msgQueue.size)
    }

    private fun verifyColdStartWithResume(trackProcessStart: Boolean = true) {
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

        val traceStartMs = if (trackProcessStart) {
            DEFAULT_FAKE_CURRENT_TIME
        } else {
            applicationInitStart
        }

        assertTraceRoot(trace, traceStartMs, traceEnd)
        assertChildSpan(processInit, traceStartMs, applicationInitEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, applicationInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)

        assertEquals(0, loggerAction.msgQueue.size)
    }

    private fun verifyColdStartWithResumeWithoutAppInitEvents(trackProcessStart: Boolean = true) {
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

        val traceStartMs = if (trackProcessStart) {
            DEFAULT_FAKE_CURRENT_TIME
        } else {
            sdkInitStart
        }

        assertTraceRoot(trace, traceStartMs, traceEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, sdkInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)

        assertEquals(0, loggerAction.msgQueue.size)
    }

    private fun verifyWarmStartWithRenderWithoutAppInitEvents() {
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
            assertTraceRoot(this, startupActivityStart, traceEnd)
            assertEquals("60001", attributes["emb.activity-init-gap-ms"])
            assertEquals("30", attributes["emb.embrace-init-duration-ms"])
        }
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, loggerAction.msgQueue.size)
    }

    private fun verifyWarmStartWithResumeWithoutAppInitEvents() {
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
            assertTraceRoot(this, startupActivityStart, traceEnd)
            assertEquals("60001", attributes["emb.activity-init-gap-ms"])
            assertEquals("30", attributes["emb.embrace-init-duration-ms"])
        }
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)
        assertEquals(0, loggerAction.msgQueue.size)
    }

    private fun assertTraceRoot(trace: EmbraceSpanData, expectedStartTimeNanos: Long, expectedEndTimeNanos: Long) {
        assertEquals(expectedStartTimeNanos, trace.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeNanos, trace.endTimeNanos.nanosToMillis())
        trace.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        trace.assertIsKeySpan()
    }

    private fun assertChildSpan(span: EmbraceSpanData, expectedStartTimeNanos: Long, expectedEndTimeNanos: Long) {
        assertEquals(expectedStartTimeNanos, span.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeNanos, span.endTimeNanos.nanosToMillis())
        span.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        span.assertDoesNotHaveEmbraceAttribute(KeySpan)
    }
}
