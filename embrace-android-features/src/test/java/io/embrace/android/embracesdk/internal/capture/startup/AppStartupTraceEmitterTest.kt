package io.embrace.android.embracesdk.internal.capture.startup

import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertDoesNotHaveEmbraceAttribute
import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.capture.activity.hasPrePostEvents
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.ACTIVITY_FIRST_DRAW_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.ACTIVITY_INIT_DELAY_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.ACTIVITY_INIT_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.ACTIVITY_LOAD_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.ACTIVITY_RENDER_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.APP_READY_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.COLD_APP_STARTUP_ROOT_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.EMBRACE_INIT_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.PROCESS_INIT_SPAN
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter.Companion.WARM_APP_STARTUP_ROOT_SPAN
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.attrs.embStartupActivityName
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.ui.hasRenderEvent
import io.embrace.android.embracesdk.internal.ui.supportFrameCommitCallback
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The various combinations of OS capabilities mean we have to test the following Android versions:
 *
 *  - 14+:         cold start trace from process create to render, process requested time available, activity pre/post create available
 *  - 10 to 13:    cold start trace from process create to render, process requested time NOT available, activity pre/post create available
 *  - 7 to 9:      cold start trace from process create to render, process requested + activity pre/post create times NOT available
 *  - 6.x:         cold start trace application init if available (sdk init if not) to render
 *  - 5:           cold start trace application init if available (sdk init if not) to activity resume
 */
@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceEmitterTest {
    private val processInitTime: Long = DEFAULT_FAKE_CURRENT_TIME
    private var startupService: StartupService? = null
    private var dataCollectionCompletedCallbackInvokedCount = 0
    private var firePreAndPostCreate: Boolean = true
    private var trackProcessStart: Boolean = true
    private var hasRenderEvent = true
    private var hasFrameCommitEvent = true

    private lateinit var clock: FakeClock
    private lateinit var otelClock: OtelJavaClock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var logger: FakeEmbLogger

    @Before
    fun setUp() {
        clock = FakeClock(processInitTime)
        FakeInitModule(clock = clock).run {
            otelClock = openTelemetryModule.openTelemetryClock
            spanSink = openTelemetryModule.spanSink
            spanService = openTelemetryModule.spanService
        }
        spanService.initializeService(clock.now())
        startupService = StartupServiceImpl(
            spanService
        )
        clock.tick(100L)
        logger = FakeEmbLogger(false)
        firePreAndPostCreate = hasPrePostEvents(BuildVersionChecker)
        trackProcessStart = BuildVersionChecker.isAtLeast(VERSION_CODES.N)
        hasRenderEvent = hasRenderEvent(BuildVersionChecker)
        hasFrameCommitEvent = supportFrameCommitCallback(BuildVersionChecker)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `no crashes if startup service not available in T`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(verifyTrace = false)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with every event triggered in T`() {
        createTraceEmitter().simulateAppStartup()
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace without application init start and end triggered in T`() {
        createTraceEmitter().simulateAppStartup(hasAppInitEvents = false)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace aborted activity creation in T`() {
        createTraceEmitter().simulateAppStartup(abortFirstActivityLoad = true)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with splash screen in T`() {
        createTraceEmitter().simulateAppStartup(loadSplashScreen = true)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with manual end in T`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(manualEnd = true)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace without application init start and end triggered in T`() {
        createTraceEmitter().simulateAppStartup(isColdStart = false, hasAppInitEvents = false)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace with manual end in T`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(isColdStart = false, manualEnd = true)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `trace end callback will not be invoked twice`() {
        startupService = null
        createTraceEmitter().run {
            firstActivityInit(startupCompleteCallback = { dataCollectionCompletedCallbackInvokedCount++ })
            firstFrameRendered(STARTUP_ACTIVITY_NAME)
            firstFrameRendered(STARTUP_ACTIVITY_NAME)
        }
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `abandon cold start after app init`() {
        val emitter = createTraceEmitter().apply {
            initApp(
                hasAppInitEvents = true,
                isColdStart = true
            )
            preActivityInit(false)
        }
        val abandonTime = clock.tick()
        emitter.onBackground(abandonTime)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        with(checkNotNull(spanMap.coldAppStartupRootSpan())) {
            assertError(ErrorCode.USER_ABANDON)
            assertEquals(abandonTime, endTimeNanos.nanosToMillis())
        }

        assertNotNull(spanMap.processInitSpan())
        assertNotNull(spanMap.embraceInitSpan())
        assertNotNull(spanMap.initGapSpan())
        assertNull(spanMap.activityInitSpan())
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `abandon cold start after activity init`() {
        val emitter = createTraceEmitter().apply {
            initApp(
                hasAppInitEvents = true,
                isColdStart = true
            )
            preActivityInit(false)
            createActivity()
            clock.tick(200)
            startupActivityInitEnd()
        }

        val abandonTime = clock.tick()
        emitter.onBackground(abandonTime)
        with(spanSink.completedSpans().associateBy { it.name }) {
            with(checkNotNull(coldAppStartupRootSpan())) {
                assertError(ErrorCode.USER_ABANDON)
                assertEquals(abandonTime, endTimeNanos.nanosToMillis())
            }
            assertNotNull(processInitSpan())
            assertNotNull(embraceInitSpan())
            assertNotNull(initGapSpan())
            assertNotNull(activityInitSpan())
            assertNull(firstFrameRenderedSpan())
        }
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `abandon cold start after before manual end`() {
        val emitter = createTraceEmitter(true).apply {
            initApp(
                hasAppInitEvents = true,
                isColdStart = true
            )
            initActivity(
                loadSplashScreen = false,
                abortFirstLoad = false,
                activityInitOptions = ActivityInitOptions.NORMAL,
            )
        }
        val abandonTime = clock.tick()
        emitter.onBackground(abandonTime)
        with(spanSink.completedSpans().associateBy { it.name }) {
            with(checkNotNull(coldAppStartupRootSpan())) {
                assertError(ErrorCode.USER_ABANDON)
                assertEquals(abandonTime, endTimeNanos.nanosToMillis())
            }
            assertNotNull(processInitSpan())
            assertNotNull(embraceInitSpan())
            assertNotNull(initGapSpan())
            assertNotNull(activityInitSpan())
            assertNotNull(firstFrameRenderedSpan())
            assertNull(appReadySpan())
        }
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `abandon warm start after app init`() {
        val emitter = createTraceEmitter().apply {
            initApp(
                hasAppInitEvents = true,
                isColdStart = false
            )
            preActivityInit(false)
        }
        val abandonTime = clock.tick()
        emitter.onBackground(abandonTime)
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        with(checkNotNull(spanMap.warmAppStartupRootSpan())) {
            assertError(ErrorCode.USER_ABANDON)
            assertEquals(abandonTime, endTimeNanos.nanosToMillis())
        }
    }

    @Config(sdk = [VERSION_CODES.TIRAMISU])
    @Test
    fun `abandon warm start after before manual end`() {
        val emitter = createTraceEmitter(true).apply {
            initApp(
                hasAppInitEvents = true,
                isColdStart = false
            )
            initActivity(
                loadSplashScreen = false,
                abortFirstLoad = false,
                activityInitOptions = ActivityInitOptions.NORMAL
            )
        }
        val abandonTime = clock.tick()
        emitter.onBackground(abandonTime)
        with(spanSink.completedSpans().associateBy { it.name }) {
            with(checkNotNull(warmAppStartupRootSpan())) {
                assertError(ErrorCode.USER_ABANDON)
                assertEquals(abandonTime, endTimeNanos.nanosToMillis())
            }
            assertNotNull(activityInitSpan())
            assertNotNull(firstFrameRenderedSpan())
            assertNull(appReadySpan())
        }
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `no crashes if startup service not available in S`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(verifyTrace = false)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with every event triggered in S`() {
        createTraceEmitter().simulateAppStartup()
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace without application init start and end triggered in S`() {
        createTraceEmitter().simulateAppStartup(hasAppInitEvents = false)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace aborted activity creation in S`() {
        createTraceEmitter().simulateAppStartup(abortFirstActivityLoad = true)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with splash screen in S`() {
        createTraceEmitter().simulateAppStartup(loadSplashScreen = true)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with manual end in S`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(manualEnd = true)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with missing activity init events in S`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.OMIT)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with missing activity init end event in S`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.OMIT_END)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with delayed activity init events in S`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.DELAY)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify cold start trace with delayed activity init end event in S`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.DELAY_END)
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify warm start trace without application init start and end triggered in S`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify warm start trace aborted activity creation S`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [VERSION_CODES.S])
    @Test
    fun `verify warm start trace with manual end in S`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(isColdStart = false, manualEnd = true)
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `no crashes if startup service not available in P`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(
            verifyTrace = false
        )
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify cold start trace with every event triggered in P`() {
        createTraceEmitter()
            .simulateAppStartup()
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify cold start trace without application init start and end triggered in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify cold start trace aborted activity creation in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify cold start trace with splash screen in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                loadSplashScreen = true
            )
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify cold start trace with manual end in P`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                manualEnd = true
            )
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify warm start trace without application init start and end triggered in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.P])
    @Test
    fun `verify warm start trace with manual end in P`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                isColdStart = false,
                manualEnd = true
            )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `no crashes if startup service not available in M`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(
            verifyTrace = false
        )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with every event triggered in M`() {
        createTraceEmitter()
            .simulateAppStartup()
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace without application init start and end triggered in M`() {
        createTraceEmitter()
            .simulateAppStartup(
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace aborted activity creation in M`() {
        createTraceEmitter()
            .simulateAppStartup(
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with splash screen in M`() {
        createTraceEmitter()
            .simulateAppStartup(
                loadSplashScreen = true
            )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with manual end in M`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                manualEnd = true
            )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with missing activity init events in M`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.OMIT)
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with missing activity init end event in M`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.OMIT_END)
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with delayed activity init events in M`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.DELAY)
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify cold start trace with delayed activity init end event in M`() {
        createTraceEmitter().simulateAppStartup(activityInitOptions = ActivityInitOptions.DELAY_END)
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify warm start trace without application init start and end triggered in M`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.M])
    @Test
    fun `verify warm start trace with manual end in M`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                isColdStart = false,
                manualEnd = true
            )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `no crashes if startup service not available in L`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(
            verifyTrace = false
        )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with every event triggered in L`() {
        createTraceEmitter()
            .simulateAppStartup()
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace without application init start and end triggered in L`() {
        createTraceEmitter()
            .simulateAppStartup(
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace aborted activity creation in L`() {
        createTraceEmitter()
            .simulateAppStartup(
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with splash screen in L`() {
        createTraceEmitter()
            .simulateAppStartup(
                loadSplashScreen = true
            )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with manual end in L`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                manualEnd = true
            )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify warm start trace without application init start and end triggered in L`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify warm start trace with manual end in L`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                isColdStart = false,
                manualEnd = true
            )
    }

    private fun createTraceEmitter(manualEnd: Boolean = false) =
        AppStartupTraceEmitter(
            clock = otelClock,
            startupServiceProvider = { startupService },
            spanService = spanService,
            versionChecker = BuildVersionChecker,
            logger = logger,
            manualEnd = manualEnd,
        )

    private fun AppStartupTraceEmitter.simulateAppStartup(
        verifyTrace: Boolean = true,
        isColdStart: Boolean = true,
        hasAppInitEvents: Boolean = true,
        manualEnd: Boolean = false,
        abortFirstActivityLoad: Boolean = false,
        loadSplashScreen: Boolean = false,
        activityInitOptions: ActivityInitOptions = ActivityInitOptions.NORMAL,
    ) {
        val appInitTimestamps = initApp(
            hasAppInitEvents = hasAppInitEvents,
            isColdStart = isColdStart
        )

        val customSpanStartMs = clock.now()
        addAttribute("custom-attribute", "true")
        val customSpanEndMs = clock.tick(15L)
        addTrackedInterval("custom-span", customSpanStartMs, customSpanEndMs)

        val activityInitTimestamps = initActivity(
            loadSplashScreen = loadSplashScreen,
            abortFirstLoad = abortFirstActivityLoad,
            activityInitOptions = activityInitOptions
        )

        val traceStart = if (isColdStart) {
            if (trackProcessStart) {
                processInitTime
            } else {
                appInitTimestamps.applicationInitStart ?: appInitTimestamps.sdkInitStart ?: activityInitTimestamps.firstActivityInit
            }
        } else {
            activityInitTimestamps.firstActivityInit
        }

        val traceEnd = if (manualEnd) {
            invokeAppReady()
        } else {
            activityInitTimestamps.uiLoadEnd
        }

        if (verifyTrace) {
            StartupTimestamps(
                traceStart = checkNotNull(traceStart),
                appInitTimestamps = appInitTimestamps,
                customSpanTimeStamps = customSpanStartMs to customSpanEndMs,
                activityInitTimestamps = activityInitTimestamps,
                traceEnd = checkNotNull(traceEnd)
            ).verifyTrace(
                isColdStart = isColdStart,
                hasAppInitEvents = hasAppInitEvents,
                manualEnd = manualEnd
            )
        }
    }

    private fun StartupTimestamps.verifyTrace(
        isColdStart: Boolean = true,
        hasAppInitEvents: Boolean = true,
        manualEnd: Boolean = false,
    ) {
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = if (isColdStart) {
            with(appInitTimestamps) {
                assertChildSpan(spanMap.embraceInitSpan(), sdkInitStart, sdkInitEnd)
                val gapStart = if (hasAppInitEvents) {
                    applicationInitEnd
                } else {
                    sdkInitEnd
                }
                assertChildSpan(spanMap.initGapSpan(), gapStart, activityInitTimestamps.firstActivityInit)
                spanMap.coldAppStartupRootSpan()
            }
        } else {
            assertNull(spanMap.embraceInitSpan())
            assertNull(spanMap.initGapSpan())
            spanMap.warmAppStartupRootSpan()
        }

        assertTraceRoot(
            input = trace,
            expectedStartTimeMs = traceStart,
            expectedEndTimeMs = traceEnd,
            expectedCustomAttributes = mapOf("custom-attribute" to "true")
        )

        if (isColdStart && hasAppInitEvents) {
            assertChildSpan(spanMap.processInitSpan(), traceStart, appInitTimestamps.applicationInitEnd)
        } else {
            assertNull(spanMap.processInitSpan())
        }

        assertChildSpan(spanMap.customSpan(), customSpanTimeStamps.first, customSpanTimeStamps.second)

        with(activityInitTimestamps) {
            assertChildSpan(spanMap.activityInitSpan(), startupActivityStart, startupActivityEnd)
            if (hasRenderEvent) {
                val renderSpan = if (hasFrameCommitEvent) {
                    spanMap.firstFrameRenderedSpan()
                } else {
                    spanMap.firstFrameDrawSpan()
                }
                assertChildSpan(renderSpan, startupActivityEnd, uiLoadEnd)
                assertNull(spanMap.activityResumeSpan())
            } else {
                assertChildSpan(spanMap.activityResumeSpan(), startupActivityEnd, uiLoadEnd)
                assertNull(spanMap.firstFrameRenderedSpan())
            }

            if (manualEnd) {
                assertChildSpan(spanMap.appReadySpan(), uiLoadEnd, traceEnd)
            } else {
                assertNull(spanMap.appReadySpan())
            }
        }

        assertEquals(0, logger.internalErrorMessages.size)
    }

    private fun AppStartupTraceEmitter.initApp(hasAppInitEvents: Boolean, isColdStart: Boolean): AppInitTimestamps {
        val applicationInitStart = if (hasAppInitEvents) {
            clock.tick(100L)
            applicationInitStart()
            clock.now()
        } else {
            null
        }

        clock.tick(100L)
        val start = clock.now()
        clock.tick(30L)
        val end = clock.now()
        clock.tick(400L)
        startupService?.setSdkStartupInfo(
            startTimeMs = start,
            endTimeMs = end,
            endedInForeground = false,
            threadName = "main"
        )

        val applicationInitEnd = if (hasAppInitEvents) {
            clock.tick(44L)
            applicationInitEnd()
            clock.now()
        } else {
            null
        }

        val (sdkInitStart, sdkInitEnd) = if (isColdStart) {
            clock.tick(50L)
            start to end
        } else {
            clock.tick(AppStartupTraceEmitter.SDK_AND_ACTIVITY_INIT_GAP + 1)
            null to null
        }

        return AppInitTimestamps(
            applicationInitStart = applicationInitStart,
            applicationInitEnd = applicationInitEnd,
            sdkInitStart = sdkInitStart,
            sdkInitEnd = sdkInitEnd
        )
    }

    private fun AppStartupTraceEmitter.preActivityInit(loadSplashScreen: Boolean): Long {
        firstActivityInit(startupCompleteCallback = { dataCollectionCompletedCallbackInvokedCount++ })
        val timestamp = clock.now()
        if (loadSplashScreen) {
            clock.tick(400L)
        }
        return timestamp
    }

    private fun AppStartupTraceEmitter.initActivity(
        loadSplashScreen: Boolean,
        abortFirstLoad: Boolean,
        activityInitOptions: ActivityInitOptions,
    ): ActivityInitTimestamps {
        val activityInitTimestamps = ActivityInitTimestamps()
        with(activityInitTimestamps) {
            firstActivityInit = preActivityInit(loadSplashScreen)

            if (activityInitOptions.startTime == TimestampOption.NORMAL) {
                fireStartEvent(activityInitTimestamps = this, abortFirstLoad = abortFirstLoad)
            }

            if (activityInitOptions.endTime == TimestampOption.NORMAL) {
                fireEndEvent(activityInitTimestamps = this)
            }

            if (hasRenderEvent) {
                clock.tick(199L)
                firstFrameRendered(STARTUP_ACTIVITY_NAME)
            }

            uiLoadEnd = clock.now()

            if (activityInitOptions.startTime == TimestampOption.DELAY) {
                fireStartEvent(activityInitTimestamps = this, abortFirstLoad = abortFirstLoad)
            }

            if (activityInitOptions.endTime == TimestampOption.DELAY) {
                fireEndEvent(activityInitTimestamps = this)
            }

            if (activityInitOptions.startTime != TimestampOption.NORMAL) {
                startupActivityStart = uiLoadEnd
            }

            if (activityInitOptions.endTime != TimestampOption.NORMAL) {
                startupActivityEnd = uiLoadEnd
            }
        }
        return activityInitTimestamps
    }

    private fun AppStartupTraceEmitter.fireStartEvent(
        activityInitTimestamps: ActivityInitTimestamps,
        abortFirstLoad: Boolean,
    ) {
        activityInitTimestamps.startupActivityStart = createActivity()
        clock.tick(180)

        if (abortFirstLoad) {
            activityInitTimestamps.startupActivityStart = createActivity()
        }

        if (firePreAndPostCreate) {
            startupActivityPostCreated()
            clock.tick()
        }
    }

    private fun AppStartupTraceEmitter.fireEndEvent(
        activityInitTimestamps: ActivityInitTimestamps
    ) {
        startupActivityInitEnd()
        activityInitTimestamps.startupActivityEnd = clock.now()
        clock.tick(15L)
        startupActivityResumed(STARTUP_ACTIVITY_NAME)
    }

    private fun AppStartupTraceEmitter.createActivity(): Long {
        val preCreateTimestamp = if (firePreAndPostCreate) {
            clock.tick()
            startupActivityPreCreated()
            clock.now()
        } else {
            null
        }
        startupActivityInitStart()
        return preCreateTimestamp ?: clock.now()
    }

    private fun AppStartupTraceEmitter.invokeAppReady(): Long {
        clock.tick(1000L)
        appReady()
        return clock.now()
    }

    private fun assertTraceRoot(
        input: EmbraceSpanData?,
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedCustomAttributes: Map<String, String> = emptyMap(),
    ) {
        checkNotNull(input)
        val trace = input.toEmbracePayload()
        assertEquals(expectedStartTimeMs, trace.startTimeNanos?.nanosToMillis())
        assertEquals(expectedEndTimeMs, trace.endTimeNanos?.nanosToMillis())
        trace.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
        val attrs = checkNotNull(trace.attributes)
        assertEquals(STARTUP_ACTIVITY_NAME, attrs.findAttributeValue(embStartupActivityName.name))
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)

        expectedCustomAttributes.forEach { entry ->
            assertEquals(entry.value, trace.attributes?.findAttributeValue(entry.key))
        }
    }

    private fun assertChildSpan(span: EmbraceSpanData?, expectedStartTimeNanos: Long?, expectedEndTimeNanos: Long?) {
        checkNotNull(span)
        assertEquals(expectedStartTimeNanos, span.startTimeNanos.nanosToMillis())
        assertEquals(expectedEndTimeNanos, span.endTimeNanos.nanosToMillis())
        span.assertDoesNotHaveEmbraceAttribute(PrivateSpan)
    }

    private fun Map<String, EmbraceSpanData?>.coldAppStartupRootSpan() = this["emb-${COLD_APP_STARTUP_ROOT_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.warmAppStartupRootSpan() = this["emb-${WARM_APP_STARTUP_ROOT_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.processInitSpan() = this["emb-${PROCESS_INIT_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.embraceInitSpan() = this["emb-${EMBRACE_INIT_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.initGapSpan() = this["emb-${ACTIVITY_INIT_DELAY_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.activityInitSpan() = this["emb-${ACTIVITY_INIT_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.firstFrameRenderedSpan() = this["emb-${ACTIVITY_RENDER_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.firstFrameDrawSpan() = this["emb-${ACTIVITY_FIRST_DRAW_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.activityResumeSpan() = this["emb-${ACTIVITY_LOAD_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.appReadySpan() = this["emb-${APP_READY_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.customSpan() = this["custom-span"]

    private data class StartupTimestamps(
        val traceStart: Long,
        val appInitTimestamps: AppInitTimestamps,
        val activityInitTimestamps: ActivityInitTimestamps,
        val customSpanTimeStamps: Pair<Long, Long>,
        val traceEnd: Long,
    )

    private data class AppInitTimestamps(
        val applicationInitStart: Long?,
        val applicationInitEnd: Long?,
        val sdkInitStart: Long?,
        val sdkInitEnd: Long?,
    )

    private data class ActivityInitTimestamps(
        var firstActivityInit: Long? = null,
        var startupActivityStart: Long? = null,
        var startupActivityEnd: Long? = null,
        var uiLoadEnd: Long? = null,
    )

    private enum class ActivityInitOptions(
        val startTime: TimestampOption = TimestampOption.NORMAL,
        val endTime: TimestampOption = TimestampOption.NORMAL,
    ) {
        NORMAL,
        OMIT(startTime = TimestampOption.MISSING, endTime = TimestampOption.MISSING),
        OMIT_END(endTime = TimestampOption.MISSING),
        DELAY(startTime = TimestampOption.DELAY, endTime = TimestampOption.DELAY),
        DELAY_END(endTime = TimestampOption.DELAY)
    }

    private enum class TimestampOption {
        NORMAL,
        DELAY,
        MISSING
    }

    companion object {
        private const val STARTUP_ACTIVITY_NAME = "StartupActivity"
    }
}
