package io.embrace.android.embracesdk.capture.startup

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertDoesNotHaveEmbraceAttribute
import io.embrace.android.embracesdk.arch.assertIsKeySpan
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.schema.KeySpan
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
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
    fun `verify cold start trace aborted activity creation T`() {
        verifyStartMultipleActivityCreated()
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

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace aborted activity creation S`() {
        verifyStartMultipleActivityCreated(isWarm = true)
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
        verifyColdStartWithResume(firePreAndPostCreate = false)
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
        verifyColdStartWithResume(trackProcessStart = false, firePreAndPostCreate = false)
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
        verifyColdStartWithResume(trackProcessStart = false, firePreAndPostCreate = false)
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
        val (sdkInitStart, sdkInitEnd) = startSdk()
        appStartupTraceEmitter.applicationInitEnd()
        val applicationInitEnd = clock.now()
        clock.tick(50L)

        val activityCreateEvents = createStartupActivity()
        val traceEnd = startupActivityRender().second

        assertEquals(7, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val processInit = checkNotNull(spanMap["emb-process-init"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = DEFAULT_FAKE_CURRENT_TIME,
            expectedEndTimeMs = traceEnd,
            expectedProcessCreateDelayMs = processCreateDelayMs,
            expectedActivityPreCreatedMs = activityCreateEvents.preCreate,
            expectedActivityPostCreatedMs = activityCreateEvents.postCreate,
            expectedFirstActivityLifecycleEventMs = activityCreateEvents.firstEvent,
        )
        assertChildSpan(processInit, DEFAULT_FAKE_CURRENT_TIME, applicationInitEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, applicationInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyColdStartWithRenderWithoutAppInitEvents(processCreateDelayMs: Long? = null) {
        val (sdkInitStart, sdkInitEnd) = startSdk()
        val activityCreateEvents = createStartupActivity()
        val traceEnd = startupActivityRender().second

        assertEquals(6, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-cold-time-to-initial-display"])
        val embraceInit = checkNotNull(spanMap["emb-embrace-init"])
        val activityInitDelay = checkNotNull(spanMap["emb-activity-init-gap"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = DEFAULT_FAKE_CURRENT_TIME,
            expectedEndTimeMs = traceEnd,
            expectedProcessCreateDelayMs = processCreateDelayMs,
            expectedActivityPreCreatedMs = activityCreateEvents.preCreate,
            expectedActivityPostCreatedMs = activityCreateEvents.postCreate,
            expectedFirstActivityLifecycleEventMs = activityCreateEvents.firstEvent,
        )

        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, sdkInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyColdStartWithResume(trackProcessStart: Boolean = true, firePreAndPostCreate: Boolean = true) {
        clock.tick(100L)
        appStartupTraceEmitter.applicationInitStart()
        val applicationInitStart = clock.now()
        clock.tick(10L)
        val (sdkInitStart, sdkInitEnd) = startSdk()
        appStartupTraceEmitter.applicationInitEnd()
        val applicationInitEnd = clock.now()
        clock.tick(50L)
        val activityCreateEvents = createStartupActivity(firePreAndPostCreate = firePreAndPostCreate)
        val traceEnd = startupActivityRender(renderFrame = false).first

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
        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = traceStartMs,
            expectedEndTimeMs = traceEnd,
            expectedFirstActivityLifecycleEventMs = startupActivityStart,
        )
        assertChildSpan(processInit, traceStartMs, applicationInitEnd)
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, applicationInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyColdStartWithResumeWithoutAppInitEvents(trackProcessStart: Boolean = true) {
        val (sdkInitStart, sdkInitEnd) = startSdk()
        val activityCreateEvents = createStartupActivity(firePreAndPostCreate = false)
        val traceEnd = startupActivityRender(renderFrame = false).first

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
        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = traceStartMs,
            expectedEndTimeMs = traceEnd,
            expectedFirstActivityLifecycleEventMs = startupActivityStart,
        )
        assertChildSpan(embraceInit, sdkInitStart, sdkInitEnd)
        assertChildSpan(activityInitDelay, sdkInitEnd, startupActivityStart)
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyWarmStartWithRenderWithoutAppInitEvents(processCreateDelayMs: Long? = null) {
        val sdkInitEnd = startSdk().second
        clock.tick(1601L)
        val activityCreateEvents = createStartupActivity()
        val traceEnd = startupActivityRender().second

        assertEquals(4, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-warm-time-to-initial-display"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val firstRender = checkNotNull(spanMap["emb-first-frame-render"])

        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        with(trace) {
            assertTraceRoot(
                input = this,
                expectedStartTimeMs = startupActivityStart,
                expectedEndTimeMs = traceEnd,
                expectedProcessCreateDelayMs = processCreateDelayMs,
                expectedActivityPreCreatedMs = activityCreateEvents.preCreate,
                expectedActivityPostCreatedMs = activityCreateEvents.postCreate,
                expectedFirstActivityLifecycleEventMs = startupActivityStart,
            )
            assertEquals((startupActivityStart - sdkInitEnd).toString(), attributes["activity-init-gap-ms"])
            assertEquals("30", attributes["embrace-init-duration-ms"])
        }
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(firstRender, startupActivityEnd, traceEnd)

        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyWarmStartWithResumeWithoutAppInitEvents() {
        startSdk()
        clock.tick(2001)
        val activityCreateEvents = createStartupActivity(firePreAndPostCreate = false)
        val traceEnd = startupActivityRender(renderFrame = false).first

        assertEquals(4, spanSink.completedSpans().size)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = checkNotNull(spanMap["emb-warm-time-to-initial-display"])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])
        val activityResume = checkNotNull(spanMap["emb-activity-resume"])

        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        with(trace) {
            assertTraceRoot(
                input = this,
                expectedStartTimeMs = startupActivityStart,
                expectedEndTimeMs = traceEnd,
                expectedFirstActivityLifecycleEventMs = startupActivityStart,
            )
            assertEquals("2401", attributes["activity-init-gap-ms"])
            assertEquals("30", attributes["embrace-init-duration-ms"])
        }
        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertChildSpan(activityResume, startupActivityEnd, traceEnd)
        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun verifyStartMultipleActivityCreated(isWarm: Boolean = false) {
        val sdkInitEnd = startSdk().second
        if (isWarm) {
            clock.tick(1601L)
        }
        val firstActivityCreateEvents = abortedActivityCreation()
        val activityCreateEvents = createStartupActivity()
        val traceEnd = startupActivityRender().second

        val firstActivityInit = checkNotNull(firstActivityCreateEvents.firstEvent)
        val startupActivityStart = checkNotNull(activityCreateEvents.create)
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        val traceStart = if (isWarm) {
            firstActivityInit
        } else {
            DEFAULT_FAKE_CURRENT_TIME
        }

        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val traceName = if (isWarm) {
            "emb-warm-time-to-initial-display"
        } else {
            assertChildSpan(checkNotNull(spanMap["emb-activity-init-gap"]), sdkInitEnd, firstActivityInit)
            "emb-cold-time-to-initial-display"
        }
        val trace = checkNotNull(spanMap[traceName])
        val activityCreate = checkNotNull(spanMap["emb-activity-create"])

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = traceStart,
            expectedEndTimeMs = traceEnd,
            expectedProcessCreateDelayMs = if (isWarm) null else 0,
            expectedActivityPreCreatedMs = activityCreateEvents.preCreate,
            expectedActivityPostCreatedMs = activityCreateEvents.postCreate,
            expectedFirstActivityLifecycleEventMs = firstActivityInit
        )

        assertChildSpan(activityCreate, startupActivityStart, startupActivityEnd)
        assertEquals(0, fakeInternalErrorService.throwables.size)
    }

    private fun startSdk(): Pair<Long, Long> {
        clock.tick(100L)
        val sdkInitStart = clock.now()
        clock.tick(30L)
        val sdkInitEnd = clock.now()
        clock.tick(400L)
        checkNotNull(startupService).setSdkStartupInfo(
            startTimeMs = sdkInitStart,
            endTimeMs = sdkInitEnd,
            endedInForeground = false,
            threadName = "main"
        )
        return Pair(sdkInitStart, sdkInitEnd)
    }

    private fun abortedActivityCreation(): ActivityCreateEvents {
        appStartupTraceEmitter.startupActivityPreCreated()
        val preCreate = clock.now()
        clock.tick()
        appStartupTraceEmitter.startupActivityInitStart()
        val create = clock.now()
        clock.tick(500)
        return ActivityCreateEvents(preCreate = preCreate, create = create, firstEvent = create)
    }

    private fun createStartupActivity(firePreAndPostCreate: Boolean = true): ActivityCreateEvents {
        val activityCreateEvents = ActivityCreateEvents()
        if (firePreAndPostCreate) {
            appStartupTraceEmitter.startupActivityPreCreated()
            activityCreateEvents.preCreate = clock.now()
            clock.tick()
        }
        appStartupTraceEmitter.startupActivityInitStart()
        activityCreateEvents.create = clock.now()
        activityCreateEvents.firstEvent = activityCreateEvents.create
        clock.tick(180)
        if (firePreAndPostCreate) {
            appStartupTraceEmitter.startupActivityPostCreated()
            activityCreateEvents.postCreate = clock.now()
            clock.tick()
        }
        appStartupTraceEmitter.startupActivityInitEnd()
        activityCreateEvents.finished = clock.now()
        clock.tick(15L)
        return activityCreateEvents
    }

    private fun startupActivityRender(renderFrame: Boolean = true): Pair<Long, Long> {
        appStartupTraceEmitter.startupActivityResumed(STARTUP_ACTIVITY_NAME)
        val resumed = clock.now()
        if (renderFrame) {
            clock.tick(199L)
            appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME)
        }
        return Pair(resumed, clock.now())
    }

    private fun assertTraceRoot(
        input: EmbraceSpanData,
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedProcessCreateDelayMs: Long? = null,
        expectedActivityPreCreatedMs: Long? = null,
        expectedActivityPostCreatedMs: Long? = null,
        expectedFirstActivityLifecycleEventMs: Long? = null
    ) {
        val trace = input.toNewPayload()
        assertEquals(expectedStartTimeMs, trace.startTimeNanos?.nanosToMillis())
        assertEquals(expectedEndTimeMs, trace.endTimeNanos?.nanosToMillis())
        trace.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        trace.assertIsKeySpan()
        val attrs = checkNotNull(trace.attributes)
        assertEquals(STARTUP_ACTIVITY_NAME, attrs.findAttributeValue("startup-activity-name"))
        assertEquals(expectedProcessCreateDelayMs?.toString(), attrs.findAttributeValue("process-create-delay-ms"))
        assertEquals(expectedActivityPreCreatedMs?.toString(), attrs.findAttributeValue("startup-activity-pre-created-ms"))
        assertEquals(expectedActivityPostCreatedMs?.toString(), attrs.findAttributeValue("startup-activity-post-created-ms"))
        assertEquals(expectedFirstActivityLifecycleEventMs?.toString(), attrs.findAttributeValue("first-activity-init-ms"))
        assertEquals("false", attrs.findAttributeValue("embrace-init-in-foreground"))
        assertEquals("main", attrs.findAttributeValue("embrace-init-thread-name"))
    }

    private fun assertChildSpan(span: EmbraceSpanData, expectedStartTimeNanos: Long, expectedEndTimeNanos: Long) {
        assertEquals(expectedStartTimeNanos, span.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeNanos, span.endTimeNanos.nanosToMillis())
        span.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        span.assertDoesNotHaveEmbraceAttribute(KeySpan)
    }

    private data class ActivityCreateEvents(
        var firstEvent: Long? = null,
        var preCreate: Long? = null,
        var create: Long? = null,
        var postCreate: Long? = null,
        var finished: Long? = null,
    )

    companion object {
        private const val STARTUP_ACTIVITY_NAME = "StartupActivity"
    }
}
