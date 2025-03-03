package io.embrace.android.embracesdk.internal.capture.startup

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.arch.assertDoesNotHaveEmbraceAttribute
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
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
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `no crashes if startup service not available in T`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(verifyTrace = false)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with every event triggered in T`() {
        createTraceEmitter().simulateAppStartup()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace without application init start and end triggered in T`() {
        createTraceEmitter().simulateAppStartup(hasAppInitEvents = false)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace aborted activity creation in T`() {
        createTraceEmitter().simulateAppStartup(abortFirstActivityLoad = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with splash screen in T`() {
        createTraceEmitter().simulateAppStartup(loadSplashScreen = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify cold start trace with manual end in T`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace without application init start and end triggered in T`() {
        createTraceEmitter().simulateAppStartup(isColdStart = false, hasAppInitEvents = false)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `verify warm start trace with manual end in T`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(isColdStart = false, manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
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

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `no crashes if startup service not available in S`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(verifyTrace = false)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with every event triggered in S`() {
        createTraceEmitter().simulateAppStartup()
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace without application init start and end triggered in S`() {
        createTraceEmitter().simulateAppStartup(hasAppInitEvents = false)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace aborted activity creation in S`() {
        createTraceEmitter().simulateAppStartup(abortFirstActivityLoad = true)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with splash screen in S`() {
        createTraceEmitter().simulateAppStartup(loadSplashScreen = true)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify cold start trace with manual end in S`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace without application init start and end triggered in S`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace aborted activity creation S`() {
        createTraceEmitter()
            .simulateAppStartup(
                isColdStart = false,
                hasAppInitEvents = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.S])
    @Test
    fun `verify warm start trace with manual end in S`() {
        createTraceEmitter(manualEnd = true).simulateAppStartup(isColdStart = false, manualEnd = true)
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `no crashes if startup service not available in P`() {
        startupService = null
        createTraceEmitter().simulateAppStartup(
            verifyTrace = false,
            firePreAndPostCreate = false,
            hasRenderEvent = false
        )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with every event triggered in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                firePreAndPostCreate = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace without application init start and end triggered in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                firePreAndPostCreate = false,
                hasAppInitEvents = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace aborted activity creation in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                abortFirstActivityLoad = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with splash screen in P`() {
        createTraceEmitter()
            .simulateAppStartup(
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                loadSplashScreen = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify cold start trace with manual end in P`() {
        createTraceEmitter(manualEnd = true)
            .simulateAppStartup(
                firePreAndPostCreate = false,
                hasRenderEvent = false,
                manualEnd = true
            )
    }

    @Config(sdk = [Build.VERSION_CODES.P])
    @Test
    fun `verify warm start trace without application init start and end triggered in P`() {
        createTraceEmitter()
            .simulateAppStartup(
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
            .simulateAppStartup(
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
        createTraceEmitter().simulateAppStartup(
            verifyTrace = false,
            firePreAndPostCreate = false,
            trackProcessStart = false,
            hasRenderEvent = false
        )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace with every event triggered in M`() {
        createTraceEmitter()
            .simulateAppStartup(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.M])
    @Test
    fun `verify cold start trace without application init start and end triggered in M`() {
        createTraceEmitter()
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
        createTraceEmitter().simulateAppStartup(
            verifyTrace = false,
            firePreAndPostCreate = false,
            trackProcessStart = false,
            hasRenderEvent = false
        )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace with every event triggered in L`() {
        createTraceEmitter()
            .simulateAppStartup(
                firePreAndPostCreate = false,
                trackProcessStart = false,
                hasRenderEvent = false
            )
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    @Test
    fun `verify cold start trace without application init start and end triggered in L`() {
        createTraceEmitter()
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            .simulateAppStartup(
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
            versionChecker = BuildVersionChecker,
            logger = logger,
            manualEnd = manualEnd,
        )

    private fun AppStartupTraceEmitter.simulateAppStartup(
        verifyTrace: Boolean = true,
        isColdStart: Boolean = true,
        firePreAndPostCreate: Boolean = true,
        trackProcessStart: Boolean = true,
        hasAppInitEvents: Boolean = true,
        hasRenderEvent: Boolean = true,
        manualEnd: Boolean = false,
        abortFirstActivityLoad: Boolean = false,
        loadSplashScreen: Boolean = false,
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
            firePreAndPostCreate = firePreAndPostCreate,
            renderFrame = hasRenderEvent,
            loadSplashScreen = loadSplashScreen,
            abortFirstLoad = abortFirstActivityLoad
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
                hasRenderEvent = hasRenderEvent,
                manualEnd = manualEnd
            )
        }
    }

    private fun StartupTimestamps.verifyTrace(
        isColdStart: Boolean = true,
        hasAppInitEvents: Boolean = true,
        hasRenderEvent: Boolean = true,
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
        firePreAndPostCreate: Boolean,
        renderFrame: Boolean,
        loadSplashScreen: Boolean,
        abortFirstLoad: Boolean,
    ): ActivityInitTimestamps {
        val activityInitTimestamps = ActivityInitTimestamps()
        with(activityInitTimestamps) {
            firstActivityInit = preActivityInit(loadSplashScreen)
            startupActivityStart = createActivity(firePreAndPostCreate)

            clock.tick(180)

            if (abortFirstLoad) {
                startupActivityStart = createActivity(firePreAndPostCreate)
            }

            if (firePreAndPostCreate) {
                startupActivityPostCreated()
                clock.tick()
            }
            startupActivityInitEnd()
            startupActivityEnd = clock.now()
            clock.tick(15L)

            startupActivityResumed(STARTUP_ACTIVITY_NAME)
            if (renderFrame) {
                clock.tick(199L)
                firstFrameRendered(STARTUP_ACTIVITY_NAME)
            }

            uiLoadEnd = clock.now()
        }
        return activityInitTimestamps
    }

    private fun AppStartupTraceEmitter.createActivity(firePreAndPostCreate: Boolean): Long {
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

    companion object {
        private const val STARTUP_ACTIVITY_NAME = "StartupActivity"
    }
}
