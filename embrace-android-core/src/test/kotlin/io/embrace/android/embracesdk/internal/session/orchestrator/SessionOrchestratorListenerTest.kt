package io.embrace.android.embracesdk.internal.session.orchestrator

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeLogLimitingService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakeOrdinalStore
import io.embrace.android.embracesdk.fakes.FakePayloadMessageCollator
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeUserSessionPropertiesService
import io.embrace.android.embracesdk.fakes.behavior.FakeUserSessionBehavior
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.capture.session.PropertyScope
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.UserSessionMetadataStore
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.id.SessionPartTrackerImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SessionOrchestratorListenerTest {

    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var payloadFactory: PayloadFactoryImpl
    private lateinit var payloadCollator: FakePayloadMessageCollator
    private lateinit var logEnvelopeSource: FakeLogEnvelopeSource
    private lateinit var appStateTracker: FakeAppStateTracker
    private lateinit var clock: FakeClock
    private lateinit var configService: FakeConfigService
    private lateinit var userService: FakeUserService
    private lateinit var store: FakePayloadStore
    private lateinit var userSessionPropertiesService: UserSessionPropertiesService
    private lateinit var sessionTracker: SessionPartTracker
    private lateinit var payloadCachingService: PayloadCachingService
    private lateinit var sessionCacheExecutor: BlockingScheduledExecutorService
    private lateinit var instrumentationRegistry: InstrumentationRegistry
    private lateinit var fakeDataSource: FakeDataSource
    private lateinit var logger: FakeInternalLogger
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private lateinit var destination: FakeTelemetryDestination
    private var orchestratorStartTimeMs: Long = 0

    private val maxDurationMs = TimeUnit.MINUTES.toMillis(10)
    private val inactivityMs = TimeUnit.MINUTES.toMillis(5)

    @Before
    fun setUp() {
        clock = FakeClock()
        logger = FakeInternalLogger(throwOnInternalError = false)
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f))
            )
        )
    }

    @Test
    fun `USER_SESSION_ACTIVE fired on fresh session transitions`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND)
        // registration fires immediately since a session is already active
        val events = collectEvents(SessionStateEvent.UserSessionActive::class)
        assertEquals(1, events.size)

        // manual end always starts a new user session
        clock.tick(10000)
        orchestrator.endSessionWithManual(false)
        assertEquals(2, events.size)

        // foreground/background within max duration keeps same user session
        orchestrator.onBackground()
        orchestrator.onForeground()
        assertEquals(2, events.size)

        // past max duration — new user session
        clock.tick(maxDurationMs)
        orchestrator.onBackground()
        orchestrator.onForeground()
        assertEquals(3, events.size)
    }

    @Test
    fun `USER_SESSION_ACTIVE not fired by session part transitions when restored session continues`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = restoredMetadataStore())
        // registration fires immediately since the restored session is still active
        val events = collectEvents(SessionStateEvent.UserSessionActive::class)
        assertEquals(1, events.size)

        // session part transitions within max duration do not fire USER_SESSION_ACTIVE again
        orchestrator.onBackground()
        orchestrator.onForeground()
        assertEquals(1, events.size)
    }

    @Test
    fun `USER_SESSION_ENDED fired when user session terminates`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND)
        val events = collectEvents(SessionStateEvent.UserSessionEnded::class)

        // manual end terminates then creates a new session
        clock.tick(10000)
        orchestrator.endSessionWithManual(false)
        assertEquals(1, events.size)

        // foreground/background within max duration
        orchestrator.onBackground()
        orchestrator.onForeground()
        assertEquals(1, events.size)

        // past max duration — old session terminates
        clock.tick(maxDurationMs)
        orchestrator.onBackground()
        orchestrator.onForeground()
        assertEquals(2, events.size)
    }

    @Test
    fun `event order on manual end is USER_SESSION_ACTIVE then USER_SESSION_ENDED then USER_SESSION_ACTIVE then NEW_SESSION_PART`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND)
        val events = mutableListOf<SessionStateEvent>()
        // registration fires USER_SESSION_ACTIVE immediately
        orchestrator.addUserSessionListener { event -> events.add(event) }

        clock.tick(10000)
        orchestrator.endSessionWithManual(false)

        assertEquals(
            listOf(
                SessionStateEvent.UserSessionActive::class,
                SessionStateEvent.UserSessionEnded::class,
                SessionStateEvent.UserSessionActive::class,
            ),
            events.map { it::class }
        )
    }

    @Test
    fun `multiple listeners all notified`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND)

        val events1 = mutableListOf<SessionStateEvent>()
        val events2 = mutableListOf<SessionStateEvent>()
        orchestrator.addUserSessionListener { event -> events1.add(event) }
        orchestrator.addUserSessionListener { event -> events2.add(event) }

        clock.tick(10000)
        orchestrator.endSessionWithManual(false)
        assertEquals(events1.map { it::class to it.userSessionId }, events2.map { it::class to it.userSessionId })
        assertTrue(events1.any { it is SessionStateEvent.UserSessionActive })
    }

    @Test
    fun `adding a listener fires USER_SESSION_ACTIVE immediately when a session is active`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND)
        val events = mutableListOf<SessionStateEvent>()
        orchestrator.addUserSessionListener { event -> events.add(event) }
        assertEquals(listOf(SessionStateEvent.UserSessionActive::class), events.map { it::class })
    }

    @Test
    fun `exception in listener is swallowed and does not prevent other listeners from being notified`() {
        configService = sessionBehaviorConfig()
        createOrchestrator(AppState.FOREGROUND)

        var secondListenerInvoked = false
        orchestrator.addUserSessionListener { error("simulated listener failure") }
        orchestrator.addUserSessionListener { secondListenerInvoked = true }

        clock.tick(10000)
        orchestrator.endSessionWithManual(false)
        assertTrue(secondListenerInvoked)
        assertTrue(logger.internalErrorMessages.any { it.msg == InternalErrorType.USER_SESSION_CALLBACK_FAIL.toString() })
    }

    private fun restoredMetadataStore() = UserSessionMetadataStore(FakeKeyValueStore()).also { store ->
        store.save(
            UserSessionMetadata(
                startTimeMs = clock.now(),
                userSessionId = "restored-id",
                userSessionNumber = 7L,
                maxDurationSecs = TimeUnit.MILLISECONDS.toSeconds(maxDurationMs),
                inactivityTimeoutSecs = TimeUnit.MILLISECONDS.toSeconds(inactivityMs),
            )
        )
    }

    private fun sessionBehaviorConfig() = FakeConfigService(
        backgroundActivityBehavior = createBackgroundActivityBehavior(
            remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f))
        ),
        sessionBehavior = FakeUserSessionBehavior(
            maxSessionDurationMs = maxDurationMs,
            sessionInactivityTimeoutMs = inactivityMs,
        )
    )

    private fun collectEvents(vararg targetTypes: kotlin.reflect.KClass<out SessionStateEvent>): MutableList<SessionStateEvent> {
        val events = mutableListOf<SessionStateEvent>()
        orchestrator.addUserSessionListener { event ->
            if (targetTypes.any { it.isInstance(event) }) {
                events.add(event)
            }
        }
        return events
    }

    private fun createOrchestrator(
        state: AppState,
        ordinalStoreOverride: FakeOrdinalStore? = null,
        metadataStoreOverride: UserSessionMetadataStore? = null,
    ) {
        store = FakePayloadStore()
        appStateTracker = FakeAppStateTracker(state)
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply { initializeService(clock.now()) }
        destination = FakeTelemetryDestination()
        payloadCollator = FakePayloadMessageCollator(currentSessionPartSpan = currentSessionPartSpan)
        val payloadSourceModule = FakePayloadSourceModule()
        logEnvelopeSource = payloadSourceModule.logEnvelopeSource
        payloadFactory = PayloadFactoryImpl(
            payloadMessageCollator = payloadCollator,
            logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
            configService = configService,
            logger = logger
        )
        userSessionPropertiesService = FakeUserSessionPropertiesService()
        userService = FakeUserService()
        sessionTracker = SessionPartTrackerImpl(
            activityManager = null,
            logger = logger
        )
        sessionCacheExecutor = BlockingScheduledExecutorService(clock, true)
        payloadCachingService = PayloadCachingServiceImpl(
            PeriodicSessionPartCacher(
                BackgroundWorker(sessionCacheExecutor),
                logger
            ),
            clock,
            sessionTracker,
            store
        )
        fakeDataSource = FakeDataSource(RuntimeEnvironment.getApplication())
        instrumentationRegistry = InstrumentationRegistryImpl(
            logger
        ).apply {
            add(
                DataSourceState(
                    factory = { fakeDataSource },
                    configGate = { true }
                )
            )
        }

        orchestrator = SessionOrchestratorImpl(
            appStateTracker,
            payloadFactory,
            clock,
            configService,
            sessionTracker,
            OrchestratorBoundaryDelegate(
                userService,
                userSessionPropertiesService
            ),
            store,
            payloadCachingService,
            instrumentationRegistry,
            destination,
            SessionPartSpanAttrPopulatorImpl(
                destination,
                { 0 },
                FakeLogLimitingService(),
                FakeMetadataService()
            ),
            ordinalStoreOverride ?: FakeOrdinalStore(),
            metadataStoreOverride ?: UserSessionMetadataStore(FakeKeyValueStore()),
            logger,
        )
        orchestratorStartTimeMs = clock.now()
        userSessionPropertiesService.addProperty("key", "value", PropertyScope.USER_SESSION)
    }
}
