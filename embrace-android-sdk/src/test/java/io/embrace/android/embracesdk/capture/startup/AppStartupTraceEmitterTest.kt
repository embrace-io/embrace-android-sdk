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
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.findSpanAttribute
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
 *  - 14+:         cold start trace from process create to render, process requested time available, activity pre/post create available
 *  - 10 to 13:    cold start trace from process create to render, process requested time NOT available, activity pre/post create available
 *  - 7 to 9:      cold start trace from process create to activity resume, process requested + activity pre/post create times NOT available
 *  - 5 to 6.0.1:  cold start trace application init if available (sdk init if not) to activity resume
 */
@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceEmitterTest {
    private var startupService: StartupService? = null
    private lateinit var clock: FakeClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var fakeInternalErrorService: FakeInternalErrorService
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
        fakeInternalErrorService = FakeInternalErrorService()
        logger = EmbLoggerImpl().apply { internalErrorService = fakeInternalErrorService }
        appStartupTraceEmitter = AppStartupTraceEmitter(
            clock = initModule.openTelemetryClock,
            startupServiceProvider = { startupService },
            spanService = spanService,
            backgroundWorker = backgroundWorker,
            versionChecker = BuildVersionChecker,
            logger = logger
        )
        fakeInternalErrorService.throwables.clear()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `no crashes if startup service not available in T`() {
        startupService = null
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with every event triggered in T`() {
        verifyColdStartWithRender(processCreateDelayMs = 0L)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace without application init start and end triggered in T`() {
        verifyColdStartWithRenderWithoutAppInitEvents(processCreateDelayMs = 0L)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace without application init start and end triggered in T`() {
        verifyWarmStartWithRenderWithoutAppInitEvents(processCreateDelayMs = 0L)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `no crashes if startup service not available in S`() {
        startupService = null
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME)
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
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
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
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
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
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
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

    private fun verifyColdStartWithRender(processCreateDelayMs: Long? = null) {
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
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd, false, "main")
        appStartupTraceEmitter.startupActivityPreCreated()
        val startupActivityPreCreated = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180)
        appStartupTraceEmitter.startupActivityPostCreated()
        val startupActivityPostCreated = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
        clock.tick(199L)
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME)
        val traceEnd = clock.now()

        assertEquals(7, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val processInit = checkNotNull(spanMap["emb-process-init"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        assertTraceRoot(
            trace = trace,
            expectedStartTimeMs = DEFAULT_FAKE_CURRENT_TIME,
            expectedEndTimeMs = traceEnd,
            expectedProcessCreateDelayMs = processCreateDelayMs,
            expectedActivityPreCreatedMs = startupActivityPreCreated,
            expectedActivityPostCreatedMs = startupActivityPostCreated,
        )
        assertChildSpan(processInit, DEFAULT_FAKE_CURRENT_TIME, applicationInitEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, applicationInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyColdStartWithRenderWithoutAppInitEvents(processCreateDelayMs: Long? = null) {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd, false, "main")
        appStartupTraceEmitter.startupActivityPreCreated()
        val startupActivityPreCreated = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180)
        appStartupTraceEmitter.startupActivityPostCreated()
        val startupActivityPostCreated = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
        clock.tick(199L)
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME)
        val traceEnd = clock.now()

        assertEquals(6, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        assertTraceRoot(
            trace = trace,
            expectedStartTimeMs = DEFAULT_FAKE_CURRENT_TIME,
            expectedEndTimeMs = traceEnd,
            expectedProcessCreateDelayMs = processCreateDelayMs,
            expectedActivityPreCreatedMs = startupActivityPreCreated,
            expectedActivityPostCreatedMs = startupActivityPostCreated,
        )

        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, sdkInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
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
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd, false, "main")
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
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

        assertTraceRoot(
            trace = trace,
            expectedStartTimeMs = traceStartMs,
            expectedEndTimeMs = traceEnd,
        )
        assertChildSpan(processInit, traceStartMs, applicationInitEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, applicationInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyColdStartWithResumeWithoutAppInitEvents(trackProcessStart: Boolean = true) {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd, false, "main")
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
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

        assertTraceRoot(
            trace = trace,
            expectedStartTimeMs = traceStartMs,
            expectedEndTimeMs = traceEnd,
        )
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, sdkInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyWarmStartWithRenderWithoutAppInitEvents(processCreateDelayMs: Long? = null) {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd, false, "main")
        clock.tick(60001L)
        appStartupTraceEmitter.startupActivityPreCreated()
        val startupActivityPreCreated = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180)
        appStartupTraceEmitter.startupActivityPostCreated()
        val startupActivityPostCreated = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
        clock.tick(199L)
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME)
        val traceEnd = clock.now()

        assertEquals(4, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-warm-time-to-initial-display"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        with(trace) {
            assertTraceRoot(
                trace = this,
                expectedStartTimeMs = startupActivityStart,
                expectedEndTimeMs = traceEnd,
                expectedProcessCreateDelayMs = processCreateDelayMs,
                expectedActivityPreCreatedMs = startupActivityPreCreated,
                expectedActivityPostCreatedMs = startupActivityPostCreated,
            )
            assertEquals((startupActivityStart - sdkInitEnd).toString(), attributes["activity-init-gap-ms"])
            assertEquals("30", attributes["embrace-init-duration-ms"])
        }
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyWarmStartWithResumeWithoutAppInitEvents() {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        checkNotNull(startupService).setSdkStartupInfo(sdkInitStart, sdkInitEnd, false, "main")
        clock.tick(60001L)
        appStartupTraceEmitter.startupActivityInitStart()
        val startupActivityStart = clock.now()
        clock.tick(180L)
        appStartupTraceEmitter.startupActivityInitEnd()
        val startupActivityEnd = clock.now()
        clock.tick(15L)
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
        val traceEnd = clock.now()

        assertEquals(4, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-warm-time-to-initial-display"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val activityResume = checkNotNull(spanMap["emb-activity-resume"])

        with(trace) {
            assertTraceRoot(
                trace = this,
                expectedStartTimeMs = startupActivityStart,
                expectedEndTimeMs = traceEnd,
            )
            assertEquals("60001", attributes["activity-init-gap-ms"])
            assertEquals("30", attributes["embrace-init-duration-ms"])
        }
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)
        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun assertTraceRoot(
        trace: EmbraceSpanData,
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedProcessCreateDelayMs: Long? = null,
        expectedActivityPreCreatedMs: Long? = null,
        expectedActivityPostCreatedMs: Long? = null,
    ) {
        assertEquals(expectedStartTimeMs, trace.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeMs, trace.endTimeNanos.nanosToMillis())
        trace.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        trace.assertIsKeySpan()
        assertEquals(STARTUP_ACTIVITY_NAME, trace.findSpanAttribute("startup-activity-name"))
        assertEquals(expectedProcessCreateDelayMs?.toString(), trace.attributes["process-create-delay-ms"])
        assertEquals(expectedActivityPreCreatedMs?.toString(), trace.attributes["startup-activity-pre-created-ms"])
        assertEquals(expectedActivityPostCreatedMs?.toString(), trace.attributes["startup-activity-post-created-ms"])
    }

    private fun assertChildSpan(span: EmbraceSpanData, expectedStartTimeNanos: Long, expectedEndTimeNanos: Long) {
        assertEquals(expectedStartTimeNanos, span.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeNanos, span.endTimeNanos.nanosToMillis())
        span.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        span.assertDoesNotHaveEmbraceAttribute(KeySpan)
    }

    companion object {
        private const val STARTUP_ACTIVITY_NAME = "StartupActivity"
    }
}
