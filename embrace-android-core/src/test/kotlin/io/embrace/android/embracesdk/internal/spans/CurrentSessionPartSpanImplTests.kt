package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.assertError
import io.embrace.android.embracesdk.assertions.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.assertions.assertIsType
import io.embrace.android.embracesdk.assertions.validatePreviousSessionPartLink
import io.embrace.android.embracesdk.assertions.validateSystemLink
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSpanFactory
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.behavior.FakeBreadcrumbBehavior
import io.embrace.android.embracesdk.fakes.createOtelBehavior
import io.embrace.android.embracesdk.fakes.createSensitiveKeysBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.config.behavior.BreadcrumbBehavior.Companion.DEFAULT_BREADCRUMB_LIMIT
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.behavior.DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior.Companion.DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class CurrentSessionPartSpanImplTests {

    private lateinit var spanRepository: SpanRepository
    private lateinit var otelLimitsConfig: OtelLimitsConfig
    private lateinit var telemetryService: FakeTelemetryService
    private lateinit var currentSessionPartSpan: CurrentSessionPartSpanImpl
    private lateinit var spanService: SpanService
    private lateinit var otelModule: OpenTelemetryModule
    private lateinit var tracer: Tracer
    private lateinit var openTelemetry: OpenTelemetry
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        telemetryService = FakeTelemetryService()
        val initModule = FakeInitModule(clock = clock, fakeTelemetryService = telemetryService)
        otelModule = initModule.openTelemetryModule
        spanRepository = initModule.openTelemetryModule.spanRepository
        currentSessionPartSpan = initModule.openTelemetryModule.currentSessionPartSpan as CurrentSessionPartSpanImpl
        tracer = initModule.openTelemetryModule.otelSdkWrapper.sdkTracer
        openTelemetry = initModule.openTelemetryModule.otelSdkWrapper.openTelemetryKotlin
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        otelLimitsConfig = initModule.instrumentedConfig.otelLimits
    }

    @Test
    fun `session part span ready when initialized`() {
        assertTrue(currentSessionPartSpan.initialized())
        currentSessionPartSpan.assertSessionPartSpan()
    }

    @Test
    fun `cannot create span before session is created`() {
        val uninitialized = FakeInitModule(clock = clock).openTelemetryModule.currentSessionPartSpan
        assertFalse(uninitialized.initialized())
        uninitialized.assertNoSessionPartSpan()
    }

    @Test
    fun `initializeService does not clobber a session part span already started by readySession`() {
        val sessionPartSpan = FakeInitModule(clock = clock).openTelemetryModule.currentSessionPartSpan
        assertFalse(sessionPartSpan.initialized())

        assertTrue(sessionPartSpan.readySession())
        val partId = sessionPartSpan.getId()
        val createdSpan = checkNotNull(sessionPartSpan.current())
        sessionPartSpan.initializeService(clock.now())

        assertTrue(sessionPartSpan.initialized())
        assertEquals(partId, sessionPartSpan.getId())
        assertSame(createdSpan, sessionPartSpan.current())
    }

    @Test
    fun `cannot create spans or add data to current span if no current span exists`() {
        currentSessionPartSpan.endSession(startNewSession = false)
        assertTrue(currentSessionPartSpan.initialized())
        currentSessionPartSpan.assertNoSessionPartSpan()
    }

    @Test
    fun `cannot create child if parent not started`() {
        assertFalse(currentSessionPartSpan.canStartNewSpan(FakeEmbraceSdkSpan(), false, EmbType.Performance.Default))
    }

    @Test
    fun `can create child if parent has stopped`() {
        assertTrue(currentSessionPartSpan.canStartNewSpan(FakeEmbraceSdkSpan.stopped(), false, EmbType.Performance.Default))
    }

    @Test
    fun `after ending session with app termination, spans cannot be recorded`() {
        currentSessionPartSpan.endSession(true, AppTerminationCause.UserTermination)
        assertFalse(currentSessionPartSpan.canStartNewSpan(null, true, EmbType.Performance.Default))
    }

    @Test
    fun `check trace limits with maximum not started traces`() {
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertNotNull(
                spanService.createSpan(
                    name = "spanzzz$it",
                    internal = false,
                ),
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            ),
        )
    }

    @Test
    fun `check trace limits with maximum non-network internal not started traces`() {
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertNotNull(
                spanService.createSpan(
                    name = "spanzzz$it",
                    internal = false,
                ),
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            ),
        )

        repeat(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART) {
            assertNotNull(spanService.createSpan(name = "internal$it", type = EmbType.Performance.Default))
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(name = "failed-span", type = EmbType.Performance.Default),
        )
    }

    @Test
    fun `network request spans have their own limit`() {
        repeat(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART) {
            assertNotEquals(NoopEmbraceSdkSpan, createNetworkSpan(it))
        }
        assertEquals(NoopEmbraceSdkSpan, createNetworkSpan(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART))
    }

    @Test
    fun `exhausting the network limit does not consume the internal span budget`() {
        exhaustNetworkSpanBudget()

        repeat(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART) {
            assertNotEquals(NoopEmbraceSdkSpan, createInternalSpan(it))
        }
        assertEquals(NoopEmbraceSdkSpan, createInternalSpan(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART))
    }

    @Test
    fun `exhausting the internal limit does not consume the network span budget`() {
        repeat(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART) {
            assertNotEquals(NoopEmbraceSdkSpan, createInternalSpan(it))
        }
        assertEquals(NoopEmbraceSdkSpan, createInternalSpan(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART))

        repeat(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART) {
            assertNotEquals(NoopEmbraceSdkSpan, createNetworkSpan(it))
        }
        assertEquals(NoopEmbraceSdkSpan, createNetworkSpan(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART))
    }

    @Test
    fun `exhausting the network limit does not consume the non-internal span budget`() {
        exhaustNetworkSpanBudget()

        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertNotEquals(NoopEmbraceSdkSpan, spanService.createSpan(name = "public$it", internal = false))
        }
        assertEquals(NoopEmbraceSdkSpan, spanService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `network span limit applies to completed spans and spans created with the span builder`() {
        repeat(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART) {
            assertTrue(
                spanService.recordCompletedSpan(
                    name = "GET /network$it",
                    startTimeMs = 100L,
                    endTimeMs = 200L,
                    type = EmbType.Performance.Network,
                ),
            )
        }
        assertFalse(
            spanService.recordCompletedSpan(
                name = "GET /failed",
                startTimeMs = 100L,
                endTimeMs = 200L,
                type = EmbType.Performance.Network,
            ),
        )
        assertEquals(NoopEmbraceSdkSpan, spanService.createSpan(networkSpanStartArgs()))

        // the internal budget is untouched by the exhausted network budget
        assertNotEquals(NoopEmbraceSdkSpan, createInternalSpan(0))
    }

    @Test
    fun `network span budget resets when a new session part starts`() {
        exhaustNetworkSpanBudget()

        currentSessionPartSpan.endSession(startNewSession = true)

        assertNotEquals(NoopEmbraceSdkSpan, createNetworkSpan(0))
    }

    @Test
    fun `dropped network spans are tracked separately from other dropped spans`() {
        val fakeTelemetryService = FakeTelemetryService()
        val initModule = FakeInitModule(clock = clock, fakeTelemetryService = fakeTelemetryService)
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())

        exhaustNetworkSpanBudget()
        createNetworkSpan(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART)

        assertEquals(listOf("network_span" to AppliedLimitType.DROP), fakeTelemetryService.appliedLimits)

        repeat(DEFAULT_MAX_INTERNAL_SPANS_PER_SESSION_PART + 1) { createInternalSpan(it) }

        assertEquals(
            listOf("network_span" to AppliedLimitType.DROP, "span" to AppliedLimitType.DROP),
            fakeTelemetryService.appliedLimits,
        )
    }

    @Test
    fun `remote config overrides the network span limit`() {
        otelModule.applyRemoteConfig(DataRemoteConfig(maxNetworkSpansPerSession = 2))

        repeat(2) { assertNotEquals(NoopEmbraceSdkSpan, createNetworkSpan(it)) }
        assertEquals(NoopEmbraceSdkSpan, createNetworkSpan(2))

        // the other budgets keep their defaults
        assertNotEquals(NoopEmbraceSdkSpan, createInternalSpan(0))
        assertNotEquals(NoopEmbraceSdkSpan, spanService.createSpan(name = "public", internal = false))
    }

    @Test
    fun `remote config overrides the internal span limit`() {
        otelModule.applyRemoteConfig(DataRemoteConfig(maxInternalSpansPerSession = 3))

        repeat(3) { assertNotEquals(NoopEmbraceSdkSpan, createInternalSpan(it)) }
        assertEquals(NoopEmbraceSdkSpan, createInternalSpan(3))

        // network spans keep their default budget
        assertNotEquals(NoopEmbraceSdkSpan, createNetworkSpan(0))
    }

    @Test
    fun `remote config overrides the non-internal span limit`() {
        otelModule.applyRemoteConfig(DataRemoteConfig(maxCustomSpansPerSession = 1))

        assertNotEquals(NoopEmbraceSdkSpan, spanService.createSpan(name = "public0", internal = false))
        assertEquals(NoopEmbraceSdkSpan, spanService.createSpan(name = "public1", internal = false))

        // internal spans keep their default budget
        assertNotEquals(NoopEmbraceSdkSpan, createInternalSpan(0))
    }

    @Test
    fun `a remote span limit takes effect as soon as config is applied`() {
        repeat(2) { assertNotEquals(NoopEmbraceSdkSpan, createNetworkSpan(it)) }

        otelModule.applyRemoteConfig(DataRemoteConfig(maxNetworkSpansPerSession = 2))

        assertEquals(NoopEmbraceSdkSpan, createNetworkSpan(2))
    }

    @Test
    fun `a remote span limit of zero drops every span of that type and is tracked`() {
        val fakeTelemetryService = FakeTelemetryService()
        val initModule = FakeInitModule(clock = clock, fakeTelemetryService = fakeTelemetryService)
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        initModule.openTelemetryModule.applyRemoteConfig(
            DataRemoteConfig(
                maxCustomSpansPerSession = 0,
                maxInternalSpansPerSession = 0,
                maxNetworkSpansPerSession = 0,
            ),
        )

        assertEquals(NoopEmbraceSdkSpan, createNetworkSpan(0))
        assertEquals(NoopEmbraceSdkSpan, createInternalSpan(0))
        assertEquals(NoopEmbraceSdkSpan, spanService.createSpan(name = "public", internal = false))

        assertEquals(
            listOf(
                "network_span" to AppliedLimitType.DROP,
                "span" to AppliedLimitType.DROP,
                "span" to AppliedLimitType.DROP,
            ),
            fakeTelemetryService.appliedLimits,
        )
    }

    /**
     * Hands the module a remote config, mimicking how the SDK supplies config to the OTel module after it has
     * already been constructed.
     */
    private fun OpenTelemetryModule.applyRemoteConfig(dataConfig: DataRemoteConfig) {
        applyConfiguration(
            sensitiveKeysBehavior = createSensitiveKeysBehavior(),
            bypassValidation = false,
            otelBehavior = createOtelBehavior(remoteCfg = RemoteConfig(dataConfig = dataConfig)),
            breadcrumbBehavior = FakeBreadcrumbBehavior(),
        )
    }

    private fun createNetworkSpan(index: Int) =
        spanService.createSpan(name = "GET /network$index", type = EmbType.Performance.Network)

    private fun createInternalSpan(index: Int) =
        spanService.createSpan(name = "internal$index", type = EmbType.Performance.Default)

    private fun exhaustNetworkSpanBudget() {
        repeat(DEFAULT_MAX_NETWORK_SPANS_PER_SESSION_PART) { createNetworkSpan(it) }
    }

    private fun networkSpanStartArgs() = OtelSpanStartArgs(
        name = "GET /builder",
        type = EmbType.Performance.Network,
        internal = true,
        private = false,
        tracer = tracer,
        openTelemetry = openTelemetry,
    )

    @Test
    fun `check non-breadcrumb span event limit`() {
        repeat(DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART) {
            assertTrue(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
        }
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertTrue(telemetryService.appliedLimits.contains("span_event" to AppliedLimitType.DROP))

        // the breadcrumb budget is untouched
        assertTrue(currentSessionPartSpan.canAddEvent(isBreadcrumb = true))
    }

    @Test
    fun `check breadcrumb span event limit`() {
        repeat(DEFAULT_BREADCRUMB_LIMIT) {
            assertTrue(currentSessionPartSpan.canAddEvent(isBreadcrumb = true))
        }
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = true))
        assertTrue(telemetryService.appliedLimits.contains("span_event" to AppliedLimitType.DROP))

        // the non-breadcrumb budget is untouched
        repeat(DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART) {
            assertTrue(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
        }
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
    }

    @Test
    fun `span event limits reset when a new session part starts`() {
        repeat(DEFAULT_MAX_SPAN_EVENTS_PER_SESSION_PART) {
            currentSessionPartSpan.canAddEvent(isBreadcrumb = false)
        }
        repeat(DEFAULT_BREADCRUMB_LIMIT) {
            currentSessionPartSpan.canAddEvent(isBreadcrumb = true)
        }
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = true))

        currentSessionPartSpan.endSession(startNewSession = true)

        assertTrue(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertTrue(currentSessionPartSpan.canAddEvent(isBreadcrumb = true))
    }

    @Test
    fun `no span events can be added after the session part span ends`() {
        currentSessionPartSpan.endSession(startNewSession = false)
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertFalse(currentSessionPartSpan.canAddEvent(isBreadcrumb = true))
    }

    @Test
    fun `breadcrumb limit is read on each call so config changes take effect immediately`() {
        var limit = 1
        val sessionPartSpan = createSessionPartSpan(customBreadcrumbLimitSupplier = { limit })
        assertTrue(sessionPartSpan.canAddEvent(isBreadcrumb = true))
        assertFalse(sessionPartSpan.canAddEvent(isBreadcrumb = true))

        limit = 3
        assertTrue(sessionPartSpan.canAddEvent(isBreadcrumb = true))
        assertTrue(sessionPartSpan.canAddEvent(isBreadcrumb = true))
        assertFalse(sessionPartSpan.canAddEvent(isBreadcrumb = true))
    }

    @Test
    fun `span event limit is read on each call so config changes take effect immediately`() {
        var limit = 1
        val sessionPartSpan = createSessionPartSpan(
            otelBehaviorSupplier = {
                createOtelBehavior(remoteCfg = RemoteConfig(dataConfig = DataRemoteConfig(maxSpanEventsPerSessionPart = limit)))
            },
        )
        assertTrue(sessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertFalse(sessionPartSpan.canAddEvent(isBreadcrumb = false))

        limit = 3
        assertTrue(sessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertTrue(sessionPartSpan.canAddEvent(isBreadcrumb = false))
        assertFalse(sessionPartSpan.canAddEvent(isBreadcrumb = false))
    }

    /**
     * Creates a session part span that records into a repository of its own, so the limit counters are isolated from
     * the instance created in [setup].
     */
    private fun createSessionPartSpan(
        otelBehaviorSupplier: Provider<OtelBehavior?> = { null },
        customBreadcrumbLimitSupplier: Provider<Int> = { DEFAULT_BREADCRUMB_LIMIT },
    ): CurrentSessionPartSpanImpl {
        val repository = SpanRepository()
        val otelClock = FakeOtelKotlinClock()
        return CurrentSessionPartSpanImpl(
            openTelemetryClock = otelClock,
            telemetryService = telemetryService,
            spanRepository = repository,
            tracerSupplier = { tracer },
            openTelemetrySupplier = ::openTelemetry,
            embraceSpanFactorySupplier = {
                EmbraceSpanFactoryImpl(
                    openTelemetryClock = otelClock,
                    spanRepository = repository,
                    dataValidator = DataValidator(telemetryService = telemetryService),
                    telemetryService = telemetryService,
                )
            },
            uuidSource = TestUuidSource(),
            otelBehaviorSupplier = otelBehaviorSupplier,
            customBreadcrumbLimitSupplier = customBreadcrumbLimitSupplier,
        ).apply { initializeService(clock.now()) }
    }

    @Test
    fun `check trace limited applied to spans created with span builder`() {
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertNotNull(
                spanService.createSpan(
                    otelSpanStartArgs = OtelSpanStartArgs(
                        name = "external-span",
                        type = EmbType.Performance.Default,
                        internal = false,
                        private = false,
                        tracer = tracer,
                        openTelemetry = openTelemetry,
                    ),
                ),
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                otelSpanStartArgs = OtelSpanStartArgs(
                    name = "external-span",
                    type = EmbType.Performance.Default,
                    internal = false,
                    private = false,
                    tracer = tracer,
                    openTelemetry = openTelemetry,
                ),
            ),
        )
        assertNotNull(
            spanService.createSpan(
                otelSpanStartArgs = OtelSpanStartArgs(
                    name = "internal-span",
                    type = EmbType.Performance.Default,
                    internal = true,
                    private = false,
                    tracer = tracer,
                    openTelemetry = openTelemetry,
                ),
            ),
        )
    }

    @Test
    fun `check trace limits with maximum traces recorded around a lambda`() {
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertEquals(
                "derp",
                spanService.recordSpan(
                    name = "record$it",
                    internal = false,
                ) { "derp" },
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            ),
        )
    }

    @Test
    fun `check trace limits with maximum completed traces`() {
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertTrue(
                spanService.recordCompletedSpan(
                    name = "complete$it",
                    startTimeMs = 100L,
                    endTimeMs = 200L,
                    internal = false,
                ),
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            ),
        )
    }

    @Test
    fun `check internal traces and child spans don't count towards limit`() {
        val parent = checkNotNull(
            spanService.createSpan(
                name = "test-span",
                internal = false,
            ),
        )
        assertTrue(parent.start())
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART - 1) {
            assertNotNull(
                "Adding span $it failed",
                spanService.createSpan(
                    name = "spanzzz$it",
                    internal = false,
                ),
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            ),
        )
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "child-span",
                parent = parent,
                internal = false,
            ),
        )
        assertNotNull(
            spanService.createSpan(
                name = "internal-again",
            ),
        )
        assertNotNull(
            spanService.createSpan(
                name = "internal-child-span",
                parent = parent,
            ),
        )
    }

    @Test
    fun `check total limit can be reached with descendant spans`() {
        var parentSpan: EmbraceSpan? = null
        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            val span = spanService.createSpan(
                name = "spanzzz$it",
                parent = parentSpan,
                internal = false,
            )
            assertTrue(checkNotNull(span).start())
            parentSpan = span
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                parent = parentSpan,
                internal = false,
            ),
        )
        assertFalse(
            spanService.recordCompletedSpan(
                name = "failed-span",
                startTimeMs = 100L,
                endTimeMs = 200L,
                parent = parentSpan,
                internal = false,
            ),
        )
        spanRepository.flushOtelSpans()
        assertEquals(
            2,
            spanService.recordSpan(
                name = "failed-span",
                parent = parentSpan,
                internal = false,
            ) { 2 },
        )
        assertEquals(0, spanRepository.completedOtelSpans().size)
    }

    @Test
    fun `check internal child spans don't count towards limit`() {
        val parentSpan = checkNotNull(
            spanService.createSpan(
                name = "parent-span",
            ),
        )
        assertTrue(parentSpan.start())
        assertNotNull(
            spanService.createSpan(
                name = "failed-span",
                parent = parentSpan,
            ),
        )
        assertNotNull(
            spanService.recordSpan(
                name = "failed-span",
                parent = parentSpan,
            ) { },
        )
        assertTrue(
            spanService.recordCompletedSpan(
                name = "failed-span",
                startTimeMs = 100L,
                endTimeMs = 200L,
                parent = parentSpan,
            ),
        )

        repeat(DEFAULT_MAX_CUSTOM_SPANS_PER_SESSION_PART) {
            assertNotNull(
                spanService.createSpan(
                    name = "spanzzz$it",
                    parent = parentSpan,
                    internal = false,
                ),
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                parent = parentSpan,
                internal = false,
            ),
        )
        assertNotNull(
            spanService.createSpan(
                name = "internal-span",
                parent = parentSpan,
            ),
        )
    }

    @Test
    fun `flushing with app termination and termination reason flushes session part span with right termination type`() {
        AppTerminationCause::class.sealedSubclasses.forEach {
            val cause = checkNotNull(it.objectInstance)
            val module = FakeInitModule(clock = clock)
            val sessionPartSpan = module.openTelemetryModule.currentSessionPartSpan
            module.openTelemetryModule.spanService.initializeService(clock.now())
            val flushedSpans = sessionPartSpan.endSession(true, cause)
            assertEquals(1, flushedSpans.size)

            val lastFlushedSpan = flushedSpans[0]
            with(lastFlushedSpan) {
                assertEquals("emb-session", name)
                assertIsType(EmbType.Ux.Session)
                assertError(ErrorCode.FAILURE)
                assertHasEmbraceAttribute(cause)
            }

            assertEquals(0, module.openTelemetryModule.spanRepository.completedOtelSpans().size)
        }
    }

    @Test
    fun `crashing results in the session part span and active spans being terminated`() {
        val sessionStartTimeMs = clock.now()
        clock.tick(100)

        val crashedSpanName = "crashed-span"
        spanService.startSpan(name = crashedSpanName, internal = false)

        val crashSpanStartTimeMs = clock.now()
        clock.tick(500)

        val crashTimeMs = clock.now()
        val flushedSpans = currentSessionPartSpan.endSession(true, AppTerminationCause.Crash).associateBy { it.name }

        assertEmbraceSpanData(
            span = flushedSpans["emb-session"],
            expectedStartTimeMs = sessionStartTimeMs,
            expectedEndTimeMs = crashTimeMs,
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                AppTerminationCause.Crash.asPair(),
                EmbType.Ux.Session.asPair(),
            ),
        )

        assertEmbraceSpanData(
            span = flushedSpans[crashedSpanName],
            expectedStartTimeMs = crashSpanStartTimeMs,
            expectedEndTimeMs = crashTimeMs,
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.asPair(),
            ),
        )

        assertEquals(0, spanRepository.completedOtelSpans().size)
        assertEquals(0, spanRepository.getActiveEmbraceSpans().size)
    }

    @Test
    fun `new session started after ending has correct metadata`() {
        val originalSessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single().snapshot())
        val originalSessionId = currentSessionPartSpan.getId()
        currentSessionPartSpan.endSession(startNewSession = true)
        with(spanRepository.getActiveEmbraceSpans().single()) {
            assertTrue(hasEmbraceAttribute(EmbType.Ux.Session))
            assertNotEquals(originalSessionPartSpan.spanId, spanId)
            checkNotNull(snapshot()?.links?.single()).validatePreviousSessionPartLink(originalSessionPartSpan, originalSessionId)
        }
    }

    @Test
    fun `previous session part link includes user session and session part ids when set`() {
        val originalSessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single())
        val originalUserSessionId = "previous-user-session"
        val originalSessionPartId = "previous-session-part"
        originalSessionPartSpan.setSystemAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, originalUserSessionId)
        originalSessionPartSpan.setSystemAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, originalSessionPartId)
        val originalSnapshot = checkNotNull(originalSessionPartSpan.snapshot())

        currentSessionPartSpan.endSession(startNewSession = true)

        with(spanRepository.getActiveEmbraceSpans().single()) {
            checkNotNull(snapshot()?.links?.single()).validatePreviousSessionPartLink(
                previousSessionPartSpan = originalSnapshot,
                previousSessionPartId = originalSessionPartId,
                previousUserSessionId = originalUserSessionId,
            )
        }
    }

    @Test
    fun `previous session part link references the immediately preceding part`() {
        currentSessionPartSpan.endSession(startNewSession = true)
        val secondSessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single().snapshot())
        val secondSessionPartId = currentSessionPartSpan.getId()

        currentSessionPartSpan.endSession(startNewSession = true)

        with(spanRepository.getActiveEmbraceSpans().single()) {
            assertNotEquals(secondSessionPartSpan.spanId, spanId)
            checkNotNull(snapshot()?.links?.single())
                .validatePreviousSessionPartLink(secondSessionPartSpan, secondSessionPartId)
        }
    }

    @Test
    fun `previous session part link is applied after a delay`() {
        val originalSessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single().snapshot())
        val originalSessionPartId = currentSessionPartSpan.getId()

        currentSessionPartSpan.endSession(startNewSession = false)
        currentSessionPartSpan.assertNoSessionPartSpan()
        assertTrue(currentSessionPartSpan.readySession())

        with(spanRepository.getActiveEmbraceSpans().single()) {
            assertNotEquals(originalSessionPartSpan.spanId, spanId)
            checkNotNull(snapshot()?.links?.single())
                .validatePreviousSessionPartLink(originalSessionPartSpan, originalSessionPartId)
        }
    }

    @Test
    fun `new session will only start if told to`() {
        assertNotNull(spanRepository.getActiveEmbraceSpans().single())
        currentSessionPartSpan.endSession(startNewSession = false)
        assertTrue(spanRepository.getActiveEmbraceSpans().isEmpty())
    }

    @Test
    fun `calling readySession creates a session part span if not present`() {
        currentSessionPartSpan.endSession(startNewSession = false)
        currentSessionPartSpan.assertNoSessionPartSpan()
        assertTrue(currentSessionPartSpan.readySession())
        currentSessionPartSpan.assertSessionPartSpan()
    }

    @Test
    fun `readySession will not replace existing session part span`() {
        val originalSessionPartSpanId = spanRepository.getActiveEmbraceSpans().single().spanId
        assertTrue(currentSessionPartSpan.readySession())
        assertEquals(originalSessionPartSpanId, spanRepository.getActiveEmbraceSpans().single().spanId)
    }

    @Test
    fun `readySession will return false if session part span is not recording`() {
        val sessionPartSpan = CurrentSessionPartSpanImpl(
            openTelemetryClock = FakeOtelKotlinClock(),
            telemetryService = telemetryService,
            spanRepository = spanRepository,
            embraceSpanFactorySupplier = { FakeEmbraceSpanFactory() },
            tracerSupplier = { FakeTracer() },
            openTelemetrySupplier = ::openTelemetry,
            uuidSource = TestUuidSource(),
            otelBehaviorSupplier = { null },
            customBreadcrumbLimitSupplier = { DEFAULT_BREADCRUMB_LIMIT },
        )
        assertFalse(sessionPartSpan.readySession())
    }

    @Test
    fun `validate tracked spans update when session is ended`() {
        val embraceSpan =
            checkNotNull(spanService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val embraceSpanId = checkNotNull(embraceSpan.spanId)
        val parentSpan =
            checkNotNull(spanService.createSpan(name = "parent-span"))
        assertTrue(parentSpan.start())
        val parentSpanId = checkNotNull(parentSpan.spanId)
        val parentSpanFromService = checkNotNull(spanRepository.getEmbraceSpan(parentSpanId))
        assertTrue(parentSpanFromService.stop())
        currentSessionPartSpan.endSession(startNewSession = true)

        // completed span not available after flush
        assertNull(spanRepository.getEmbraceSpan(parentSpanId))

        // existing reference to completed span can still be used
        checkNotNull(
            spanService.createSpan(
                name = "child-span",
                parent = parentSpan,
            ),
        )

        // active span from before flush is still available and working
        val activeSpanFromBeforeFlush = checkNotNull(spanRepository.getEmbraceSpan(embraceSpanId))
        assertTrue(activeSpanFromBeforeFlush.stop())
        val currentSpans = spanRepository.completedOtelSpans()
        assertEquals(1, currentSpans.size)
        assertEquals("emb-test-span", currentSpans[0].name)
    }

    @Test
    fun `span stop callback creates the correct span links`() {
        val sessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single())
        val userSessionId = "user-session-uuid"
        val sessionPartId = "session-part-uuid"
        sessionPartSpan.setSystemAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
        sessionPartSpan.setSystemAttribute(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionPartId)
        val span = spanService.startSpan("test").apply {
            stop()
        }

        // a stopped span releases its retained link data, so read it back from the exported payload
        val spanSnapshot = span.exportedSpan()
        val sessionPartSpanSnapshot = checkNotNull(sessionPartSpan.snapshot())

        val expectedPartLinkAttrs = mapOf(
            EmbSessionAttributes.EMB_SESSION_PART_ID to sessionPartId,
            EmbSessionAttributes.EMB_USER_SESSION_ID to userSessionId,
        )
        checkNotNull(spanSnapshot.links).single().validateSystemLink(
            linkedSpan = sessionPartSpanSnapshot,
            type = LinkType.EndSessionPart,
            expectedAttributes = expectedPartLinkAttrs,
        )
        checkNotNull(sessionPartSpanSnapshot.links).single().validateSystemLink(
            linkedSpan = spanSnapshot,
            type = LinkType.EndedIn,
            expectedAttributes = expectedPartLinkAttrs,
        )
    }

    @Test
    fun `span stop callback link attrs pick up user session id set after an earlier span stop`() {
        val sessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single())
        val sessionPartId = currentSessionPartSpan.getId()

        val earlySpan = spanService.startSpan("early").apply {
            stop()
        }
        val earlyLink = checkNotNull(earlySpan.exportedSpan().links).single()
        earlyLink.validateSystemLink(
            linkedSpan = checkNotNull(sessionPartSpan.snapshot()),
            type = LinkType.EndSessionPart,
            expectedAttributes = mapOf(EmbSessionAttributes.EMB_SESSION_PART_ID to sessionPartId),
        )
        assertTrue(
            checkNotNull(earlyLink.attributes).none {
                it.key == EmbSessionAttributes.EMB_USER_SESSION_ID
            },
        )

        val userSessionId = "user-session-uuid"
        sessionPartSpan.setSystemAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)

        val lateSpan = spanService.startSpan("late").apply {
            stop()
        }
        checkNotNull(lateSpan.exportedSpan().links).single().validateSystemLink(
            linkedSpan = checkNotNull(sessionPartSpan.snapshot()),
            type = LinkType.EndSessionPart,
            expectedAttributes = mapOf(
                EmbSessionAttributes.EMB_SESSION_PART_ID to sessionPartId,
                EmbSessionAttributes.EMB_USER_SESSION_ID to userSessionId,
            ),
        )
    }

    @Test
    fun `span stop callback link attrs are identical for consecutive stops after user session id is set`() {
        val sessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single())
        val userSessionId = "user-session-uuid"
        sessionPartSpan.setSystemAttribute(EmbSessionAttributes.EMB_USER_SESSION_ID, userSessionId)
        val expectedPartLinkAttrs = mapOf(
            EmbSessionAttributes.EMB_SESSION_PART_ID to currentSessionPartSpan.getId(),
            EmbSessionAttributes.EMB_USER_SESSION_ID to userSessionId,
        )

        repeat(2) { count ->
            val span = spanService.startSpan("test-$count").apply {
                stop()
            }
            checkNotNull(span.exportedSpan().links).single().validateSystemLink(
                linkedSpan = checkNotNull(sessionPartSpan.snapshot()),
                type = LinkType.EndSessionPart,
                expectedAttributes = expectedPartLinkAttrs,
            )
        }
    }

    @Test
    fun `session ending will not create span link to its own session part span`() {
        val sessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single())
        currentSessionPartSpan.endSession(startNewSession = true)
        val sessionPartSpanSnapshot = checkNotNull(sessionPartSpan.snapshot())
        assertEquals(0, sessionPartSpanSnapshot.links?.size)
    }

    @Test
    fun `span stop callback will not create links for untracked span`() {
        val sessionPartSpan = checkNotNull(spanRepository.getActiveEmbraceSpans().single())
        currentSessionPartSpan.spanStopCallback(checkNotNull(FakeEmbraceSdkSpan.started().spanId))

        val sessionPartSpanSnapshot = checkNotNull(sessionPartSpan.snapshot())
        assertEquals(0, sessionPartSpanSnapshot.links?.size)
    }

    @Test
    fun `span stop callback will not create links if there's no active session`() {
        val span = spanService.startSpan("test").apply {
            currentSessionPartSpan.endSession(false)
            stop()
        }

        val spanSnapshot = checkNotNull(span.snapshot())
        assertEquals(0, spanSnapshot.links?.size)
    }

    private fun CurrentSessionPartSpan.assertNoSessionPartSpan() {
        assertEquals("", getId())
        assertFalse(canStartNewSpan(parent = null, internal = true, type = EmbType.Performance.Default))
        assertTrue(endSession(true).isEmpty())
    }

    private fun CurrentSessionPartSpan.assertSessionPartSpan() {
        assertTrue(getId().isNotBlank())
        assertTrue(canStartNewSpan(parent = null, internal = true, type = EmbType.Performance.Default))
    }

    /**
     * Retrieves the exported payload for a stopped span. A stopped span releases its retained
     * event/link collections, so the data must be read back from the exported span rather than
     * a live [EmbraceSpan.snapshot].
     */
    private fun EmbraceSpan.exportedSpan(): Span {
        val id = checkNotNull(spanId)
        return checkNotNull(spanRepository.completedOtelSpans().singleOrNull { it.spanId == id }) {
            "No exported span found with id $id"
        }
    }
}
