package io.embrace.android.embracesdk.internal.capture.startup

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertDoesNotHaveEmbraceAttribute
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
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
import io.embrace.android.embracesdk.internal.opentelemetry.embStartupActivityName
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.sdk.common.Clock
import org.junit.Assert.assertEquals
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
 *  - 7 to 9:      cold start trace from process create to activity resume, process requested + activity pre/post create times NOT available
 *  - 5 to 6.0.1:  cold start trace application init if available (sdk init if not) to activity resume
 */
@RunWith(AndroidJUnit4::class)
internal class AppStartupTraceEmitterTest {
    private val processInitTime: Long = DEFAULT_FAKE_CURRENT_TIME
    private var startupService: StartupService? = null
    private var dataCollectionCompletedCallbackInvokedCount = 0
    private lateinit var clock: FakeClock
    private lateinit var otelClock: Clock
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var logger: FakeEmbLogger
    private lateinit var backgroundWorker: BackgroundWorker

    @Before
    fun setUp() {
        clock = FakeClock(processInitTime)
        backgroundWorker = fakeBackgroundWorker()
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
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `no crashes if startup service not available in T`() {
        startupService = null
        createTraceEmitter().firstFrameRendered(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with every event triggered in T`() {
        createTraceEmitter().verifyAppStartupTrace()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace without application init start and end triggered in T`() {
        createTraceEmitter().verifyAppStartupTrace(hasAppInitEvents = false)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace aborted activity creation in T`() {
        createTraceEmitter().verifyAppStartupTrace(abortFirstActivityLoad = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with splash screen in T`() {
        createTraceEmitter().verifyAppStartupTrace(loadSplashScreen = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with manual end in T`() {
        createTraceEmitter(manualEnd = true).verifyAppStartupTrace(manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace without application init start and end triggered in T`() {
        createTraceEmitter().verifyAppStartupTrace(isColdStart = false, hasAppInitEvents = false)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace with manual end in T`() {
        createTraceEmitter(manualEnd = true).verifyAppStartupTrace(isColdStart = false, manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `trace end callback will not be invoked twice`() {
        startupService = null
        val appStartupTraceEmitter = createTraceEmitter()
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        appStartupTraceEmitter.firstFrameRendered(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `no crashes if startup service not available in S`() {
        startupService = null
        createTraceEmitter().firstFrameRendered(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with every event triggered in S`() {
        createTraceEmitter().verifyAppStartupTrace()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace without application init start and end triggered in S`() {
        createTraceEmitter().verifyAppStartupTrace(hasAppInitEvents = false)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace aborted activity creation in S`() {
        createTraceEmitter().verifyAppStartupTrace(abortFirstActivityLoad = true)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with splash screen in S`() {
        createTraceEmitter().verifyAppStartupTrace(loadSplashScreen = true)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with manual end in S`() {
        createTraceEmitter(manualEnd = true).verifyAppStartupTrace(manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace without application init start and end triggered in S`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                isColdStart = false,
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace aborted activity creation S`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                isColdStart = false,
                hasAppInitEvents = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace with manual end in S`() {
        createTraceEmitter(manualEnd = true).verifyAppStartupTrace(isColdStart = false, manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `no crashes if startup service not available in P`() {
        startupService = null
        createTraceEmitter().startupActivityResumed(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with every event triggered in P`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace without application init start and end triggered in P`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace aborted activity creation in P`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with splash screen in P`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                loadSplashScreen = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with manual end in P`() {
        createTraceEmitter(manualEnd = true)
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify warm start trace without application init start and end triggered in P`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                isColdStart = false,
                firePreAndPostCreate = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify warm start trace with manual end in P`() {
        createTraceEmitter(manualEnd = true)
            .verifyAppStartupTrace(
                isColdStart = false,
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `no crashes if startup service not available in M`() {
        startupService = null
        createTraceEmitter().startupActivityResumed(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace with every event triggered in M`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace without application init start and end triggered in M`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace aborted activity creation in M`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace with splash screen in M`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                loadSplashScreen = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace with manual end in M`() {
        createTraceEmitter(manualEnd = true)
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify warm start trace without application init start and end triggered in M`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                isColdStart = false,
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify warm start trace with manual end in M`() {
        createTraceEmitter(manualEnd = true)
            .verifyAppStartupTrace(
                isColdStart = false,
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `no crashes if startup service not available in L`() {
        startupService = null
        createTraceEmitter().startupActivityResumed(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        assertEquals(1, dataCollectionCompletedCallbackInvokedCount)
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with every event triggered in L`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace without application init start and end triggered in L`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace aborted activity creation in L`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with splash screen in L`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                loadSplashScreen = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with manual end in L`() {
        createTraceEmitter(manualEnd = true)
            .verifyAppStartupTrace(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify warm start trace without application init start and end triggered in L`() {
        createTraceEmitter()
            .verifyAppStartupTrace(
                isColdStart = false,
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify warm start trace with manual end in L`() {
        createTraceEmitter(manualEnd = true)
            .verifyAppStartupTrace(
                isColdStart = false,
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    private fun createTraceEmitter(manualEnd: Boolean = false) =
        AppStartupTraceEmitter(
            clock = otelClock,
            startupServiceProvider = { startupService },
            spanService = spanService,
            backgroundWorker = backgroundWorker,
            versionChecker = BuildVersionChecker,
            logger = logger,
            manualEnd = manualEnd,
        )

    @Suppress("CyclomaticComplexMethod", "ComplexMethod")
    private fun AppStartupTraceEmitter.verifyAppStartupTrace(
        isColdStart: Boolean = true,
        firePreAndPostCreate: Boolean = true,
        trackProcessStart: Boolean = true,
        hasAppInitEvents: Boolean = true,
        hasRenderEvent: Boolean = true,
        manualEnd: Boolean = false,
        abortFirstActivityLoad: Boolean = false,
        loadSplashScreen: Boolean = false,
    ) {
        val applicationInitStart = if (hasAppInitEvents) {
            clock.tick(100L)
            applicationInitStart()
            clock.now()
        } else {
            null
        }

        val (sdkInitStart, sdkInitEnd) = startSdk().run {
            if (isColdStart) {
                this
            } else {
                null to null
            }
        }

        val customSpanStartMs = clock.now()
        addAttribute("custom-attribute", "true")
        val customSpanEndMs = clock.tick(15L)
        addTrackedInterval("custom-span", customSpanStartMs, customSpanEndMs)

        val applicationInitEnd = if (hasAppInitEvents) {
            clock.tick(44L)
            applicationInitEnd()
            clock.now()
        } else {
            null
        }

        if (isColdStart) {
            clock.tick(50L)
        } else {
            clock.tick(AppStartupTraceEmitter.SDK_AND_ACTIVITY_INIT_GAP + 1)
        }

        val activityCreateEvents = launchActivity(
            firePreAndPostCreate = firePreAndPostCreate,
            loadSplashScreen = loadSplashScreen,
            abortFirstLoad = abortFirstActivityLoad
        )
        val uiLoadEnd = startupActivityRender(hasRenderEvent).run {
            if (hasRenderEvent) {
                second
            } else {
                first
            }
        }

        val firstActivityInit = activityCreateEvents.firstEvent ?: error("No first Activity init time")
        val startupActivityStart = activityCreateEvents.preCreate ?: activityCreateEvents.create ?: error("No activity create time")
        val startupActivityEnd = checkNotNull((activityCreateEvents.finished))

        val traceStart = if (isColdStart) {
            if (trackProcessStart) {
                processInitTime
            } else {
                applicationInitStart ?: sdkInitStart ?: firstActivityInit
            }
        } else {
            firstActivityInit
        }

        val traceEnd = if (manualEnd) {
            invokeAppReady()
        } else {
            uiLoadEnd
        }

        StartupTimestamps(
            traceStart = traceStart,
            sdkInitStart = sdkInitStart,
            sdkInitEnd = sdkInitEnd,
            applicationInitEnd = applicationInitEnd,
            customSpanStart = customSpanStartMs,
            customSpanEnd = customSpanEndMs,
            firstActivityInit = firstActivityInit,
            startupActivityStart = startupActivityStart,
            startupActivityEnd = startupActivityEnd,
            uiLoadEnd = uiLoadEnd,
            traceEnd = traceEnd
        ).verifyTrace(
            isColdStart = isColdStart,
            hasAppInitEvents = hasAppInitEvents,
            hasRenderEvent = hasRenderEvent,
            manualEnd = manualEnd
        )
    }

    private fun StartupTimestamps.verifyTrace(
        isColdStart: Boolean = true,
        hasAppInitEvents: Boolean = true,
        hasRenderEvent: Boolean = true,
        manualEnd: Boolean = false,
    ) {
        val spanMap = spanSink.completedSpans().associateBy { it.name }
        val trace = if (isColdStart) {
            assertChildSpan(spanMap.embraceInitSpan(), sdkInitStart, sdkInitEnd)
            val gapStart = if (hasAppInitEvents) {
                applicationInitEnd
            } else {
                sdkInitEnd
            }
            assertChildSpan(spanMap.initGapSpan(), gapStart, firstActivityInit)
            spanMap.coldAppStartupRootSpan()
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
            assertChildSpan(spanMap.processInitSpan(), traceStart, applicationInitEnd)
        } else {
            assertNull(spanMap.processInitSpan())
        }
        assertChildSpan(spanMap.customSpan(), customSpanStart, customSpanEnd)
        assertChildSpan(spanMap.activityInitSpan(), startupActivityStart, startupActivityEnd)

        if (hasRenderEvent) {
            assertChildSpan(spanMap.firstFrameRenderSpan(), startupActivityEnd, uiLoadEnd)
            assertNull(spanMap.activityResumeSpan())
        } else {
            assertChildSpan(spanMap.activityResumeSpan(), startupActivityEnd, uiLoadEnd)
            assertNull(spanMap.firstFrameRenderSpan())
        }

        if (manualEnd) {
            assertChildSpan(spanMap.appReadySpan(), uiLoadEnd, traceEnd)
        } else {
            assertNull(spanMap.appReadySpan())
        }

        assertEquals(0, logger.internalErrorMessages.size)
    }

    private fun dataCollectionCompletedCallback() {
        dataCollectionCompletedCallbackInvokedCount++
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

    private fun AppStartupTraceEmitter.launchActivity(
        firePreAndPostCreate: Boolean,
        loadSplashScreen: Boolean,
        abortFirstLoad: Boolean,
    ): ActivityCreateEvents {
        val activityCreateEvents = ActivityCreateEvents()
        firstActivityInit()
        activityCreateEvents.firstEvent = clock.now()

        if (loadSplashScreen) {
            clock.tick(400L)
        }

        if (firePreAndPostCreate) {
            startupActivityPreCreated()
            activityCreateEvents.preCreate = clock.now()
            clock.tick()
        }
        startupActivityInitStart()
        activityCreateEvents.create = clock.now()
        clock.tick(180)

        if (abortFirstLoad) {
            startupActivityPreCreated()
            activityCreateEvents.preCreate = clock.now()
            clock.tick()
            startupActivityInitStart()
            activityCreateEvents.create = clock.now()
            clock.tick(230)
        }

        if (firePreAndPostCreate) {
            startupActivityPostCreated()
            activityCreateEvents.postCreate = clock.now()
            clock.tick()
        }
        startupActivityInitEnd()
        activityCreateEvents.finished = clock.now()
        clock.tick(15L)
        return activityCreateEvents
    }

    private fun AppStartupTraceEmitter.startupActivityRender(renderFrame: Boolean): Pair<Long, Long> {
        startupActivityResumed(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        val resumed = clock.now()
        if (renderFrame) {
            clock.tick(199L)
            firstFrameRendered(STARTUP_ACTIVITY_NAME, ::dataCollectionCompletedCallback)
        }
        return Pair(resumed, clock.now())
    }

    private fun AppStartupTraceEmitter.invokeAppReady(): Long {
        clock.tick(1000L)
        appReady(collectionCompleteCallback = ::dataCollectionCompletedCallback)
        return clock.now()
    }

    private fun assertTraceRoot(
        input: EmbraceSpanData?,
        expectedStartTimeMs: Long,
        expectedEndTimeMs: Long,
        expectedCustomAttributes: Map<String, String> = emptyMap(),
    ) {
        checkNotNull(input)
        val trace = input.toNewPayload()
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
    private fun Map<String, EmbraceSpanData?>.firstFrameRenderSpan() = this["emb-${ACTIVITY_RENDER_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.activityResumeSpan() = this["emb-${ACTIVITY_LOAD_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.appReadySpan() = this["emb-${APP_READY_SPAN}"]
    private fun Map<String, EmbraceSpanData?>.customSpan() = this["custom-span"]

    private data class StartupTimestamps(
        val traceStart: Long,
        val sdkInitStart: Long? = null,
        val sdkInitEnd: Long? = null,
        val applicationInitEnd: Long? = null,
        val customSpanStart: Long? = null,
        val customSpanEnd: Long? = null,
        val firstActivityInit: Long,
        val startupActivityStart: Long,
        val startupActivityEnd: Long,
        val uiLoadEnd: Long,
        val traceEnd: Long,
    )

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
