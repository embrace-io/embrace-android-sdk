package io.embrace.android.embracesdk.internal.session.orchestrator

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.UserSessionMetadataStore
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.id.SessionPartTrackerImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SessionOrchestratorTest {

    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var payloadFactory: PayloadFactoryImpl
    private lateinit var payloadCollator: FakePayloadMessageCollator
    private lateinit var logEnvelopeSource: FakeLogEnvelopeSource
    private lateinit var appStateTracker: FakeAppStateTracker
    private lateinit var clock: FakeClock
    private lateinit var configService: FakeConfigService
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
    fun `test initial behavior in background`() {
        createOrchestrator(AppState.BACKGROUND)
        assertEquals(orchestrator, appStateTracker.listeners.single())
        assertEquals(0, payloadCollator.sessionCount.get())
        assertEquals(1, payloadCollator.baCount.get())
        assertEquals(sessionTracker.getActiveSessionId(), currentSessionPartSpan.getSessionId())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test initial behavior in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        assertEquals(orchestrator, appStateTracker.listeners.single())
        assertEquals(1, payloadCollator.sessionCount.get())
        assertEquals(0, payloadCollator.baCount.get())
        assertEquals(sessionTracker.getActiveSessionId(), currentSessionPartSpan.getSessionId())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test on foreground call after starting in background`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        val foregroundTime = clock.now()
        val sessionSpan = currentSessionPartSpan.sessionSpan
        orchestrator.onForeground()
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = foregroundTime,
            endType = LifeEventType.BKGND_STATE
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `test on background call after starting in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick()
        val backgroundTime = clock.now()
        val sessionSpan = currentSessionPartSpan.sessionSpan
        orchestrator.onBackground()
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = backgroundTime,
            endType = LifeEventType.STATE
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `saved background activity save overridden after is sent`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        orchestrator.onSessionDataUpdate()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPartPayloads.size)
        orchestrator.onForeground()
        clock.tick()
        orchestrator.onBackground()
        assertEquals(1, store.cachedSessionPartPayloads.size)
        assertEquals(2, store.storedSessionPartPayloads.size)
    }

    @Test
    fun `background activity save invoked after ending will not save it again`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        orchestrator.onForeground()
        clock.tick()
        assertEquals(1, store.storedSessionPartPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.storedSessionPartPayloads.size)
    }

    @Test
    fun `saved session overridden after it is sent`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPartPayloads.size)
        orchestrator.onBackground()
        clock.tick()
        assertEquals(1, store.cachedSessionPartPayloads.size)
        assertEquals(1, store.storedSessionPartPayloads.size)
    }

    @Test
    fun `session save invoked after ending will not save it again`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick()
        orchestrator.onBackground()
        clock.tick()
        assertEquals(1, store.storedSessionPartPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.storedSessionPartPayloads.size)
    }

    @Test
    fun `end session with manual in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(10000)
        val endTimeMs = clock.now()
        val sessionSpan = currentSessionPartSpan.sessionSpan
        orchestrator.endSessionWithManual()
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = endTimeMs,
            endType = LifeEventType.MANUAL
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `end session with manual in background`() {
        createOrchestrator(AppState.BACKGROUND)
        appStateTracker.state = AppState.BACKGROUND
        orchestrator.endSessionWithManual()
        assertTrue(store.storedSessionPartPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `backgrounding with background activity enabled does not cache empty crash envelope`() {
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.onBackground()
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `backgrounding with background activity disabled caches empty crash envelope`() {
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 0f))
            )
        )
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.onBackground()
        assertEquals(1, store.cachedEmptyCrashPayloads.size)
    }

    @Test
    fun `foregrounding with background activity disabled does not cache empty crash envelope`() {
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 0f))
            )
        )
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `test manual session end disabled for session gating`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(sessionControlEnabled = true)
        )
        createOrchestrator(AppState.FOREGROUND)

        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual()
        assertEquals(1, payloadCollator.sessionCount.get())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
    }

    @Test
    fun `ending session manually above time threshold succeeds`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual()
        assertEquals(2, payloadCollator.sessionCount.get())
        checkNotNull(store.storedSessionPartPayloads.last().first)
    }

    @Test
    fun `ending session manually below time threshold fails`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(1000)

        orchestrator.endSessionWithManual()
        assertEquals(1, payloadCollator.sessionCount.get())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
    }

    @Test
    fun `ending session manually when no session exists does not start a new session`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.BACKGROUND)
        clock.tick(1000)
        orchestrator.endSessionWithManual()
        assertEquals(0, payloadCollator.baCount.get())
    }

    @Test
    fun `end with crash in background`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.handleCrash("crashId")
        assertEquals("crashId", destination.attributes[EmbSessionAttributes.EMB_CRASH_ID])
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `end with crash in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.handleCrash("crashId")
        assertEquals("crashId", destination.attributes[EmbSessionAttributes.EMB_CRASH_ID])
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `periodic caching started with initial session`() {
        createOrchestrator(AppState.FOREGROUND)
        assertEquals(0, store.cachedSessionPartPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPartPayloads.size)
    }

    @Test
    fun `test session span cold start`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        checkNotNull(store.storedSessionPartPayloads.last().first)
    }

    @Test
    fun `test session span non cold start`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        orchestrator.onBackground()
        checkNotNull(store.storedSessionPartPayloads.last().first)
    }

    @Test
    fun `test session span with crash`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        orchestrator.handleCrash("my-crash-id")
        checkNotNull(store.storedSessionPartPayloads.last().first)
    }

    @Test
    fun `test foreground session span heartbeat`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes[EmbSessionAttributes.EMB_TERMINATED])

        // run periodic cache
        clock.tick(2000)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes[EmbSessionAttributes.EMB_TERMINATED])

        // end with crash
        orchestrator.handleCrash("my-crash-id")
        assertEquals("false", destination.attributes[EmbSessionAttributes.EMB_TERMINATED])
    }

    @Test
    fun `test background session span heartbeat`() {
        createOrchestrator(AppState.BACKGROUND)
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes[EmbSessionAttributes.EMB_TERMINATED])

        // run periodic cache
        clock.tick(6000)
        orchestrator.onSessionDataUpdate()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes[EmbSessionAttributes.EMB_TERMINATED])

        // end with crash
        orchestrator.handleCrash("my-crash-id")
        assertEquals("false", destination.attributes[EmbSessionAttributes.EMB_TERMINATED])
    }

    @Test
    fun `user session max duration boundary`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        createOrchestrator(AppState.FOREGROUND)

        val first = checkNotNull(orchestrator.currentUserSession())
        assertEquals(1L, first.userSessionNumber)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(maxDurationMs), first.maxDurationSecs)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(inactivityMs), first.inactivityTimeoutSecs)
        assertNotNull(first.userSessionId)

        // within max duration — user session stays the same
        clock.tick(maxDurationMs - 1)
        orchestrator.onBackground()
        orchestrator.onForeground()
        val repeat = checkNotNull(orchestrator.currentUserSession())
        assertEquals(first.userSessionId, repeat.userSessionId)

        // at/past max duration — new user session starts
        clock.tick(1)
        orchestrator.onBackground()
        orchestrator.onForeground()
        val second = checkNotNull(orchestrator.currentUserSession())
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
    }

    @Test
    fun `user session manual end`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        createOrchestrator(AppState.FOREGROUND)

        val first = checkNotNull(orchestrator.currentUserSession())
        assertEquals(1L, first.userSessionNumber)

        // manual end always terminates and starts a new user session
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val second = checkNotNull(orchestrator.currentUserSession())
        assertEquals(2L, second.userSessionNumber)
        assertNotEquals(first.userSessionId, second.userSessionId)
    }

    @Test
    fun `user session ordinal persistence`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val sharedOrdinalStore = FakeOrdinalStore()

        createOrchestrator(AppState.FOREGROUND, ordinalStoreOverride = sharedOrdinalStore)
        assertEquals(1L, checkNotNull(orchestrator.currentUserSession()).userSessionNumber)

        // New orchestrator with shared ordinal store but fresh metadata store
        createOrchestrator(
            AppState.FOREGROUND,
            ordinalStoreOverride = sharedOrdinalStore,
            metadataStoreOverride = UserSessionMetadataStore(FakeKeyValueStore()),
        )
        assertEquals(2L, checkNotNull(orchestrator.currentUserSession()).userSessionNumber)
    }

    @Test
    fun `user session metadata attributes`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        createOrchestrator(AppState.FOREGROUND)

        val session = checkNotNull(orchestrator.currentUserSession())
        val attrs = session.attributes
        assertEquals(session.startTimeMs, attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS])
        assertEquals(session.userSessionId, attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals(session.userSessionNumber, attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER])
        assertEquals(session.maxDurationSecs, attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS])
        assertEquals(session.inactivityTimeoutSecs, attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS])
    }

    @Test
    fun `restores active session from metadata store`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val prePopulated = UserSessionMetadataStore(FakeKeyValueStore())
        prePopulated.save(
            UserSessionMetadata(
                startTimeMs = clock.now(),
                userSessionId = "restored-id",
                userSessionNumber = 7L,
                maxDurationSecs = TimeUnit.MILLISECONDS.toSeconds(maxDurationMs),
                inactivityTimeoutSecs = TimeUnit.MILLISECONDS.toSeconds(inactivityMs),
            )
        )

        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = prePopulated)

        val session = checkNotNull(orchestrator.currentUserSession())
        assertEquals("restored-id", session.userSessionId)
        assertEquals(7L, session.userSessionNumber)
    }

    @Test
    fun `exception in loadPersistedUserSession falls back to NoActiveSession`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val throwingStore = UserSessionMetadataStore(
            object : KeyValueStore {
                override fun getString(key: String): String? = null
                override fun getInt(key: String): Int? = null
                override fun getLong(key: String): Long? = null
                override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
                override fun getStringSet(key: String): Set<String>? = null
                override fun getStringMap(key: String): Map<String, String> =
                    error("simulated store failure")

                override fun edit(action: KeyValueStoreEditor.() -> Unit) = FakeKeyValueStore().edit(action)
                override fun incrementAndGet(key: String): Int = 0
            }
        )

        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = throwingStore)
        assertNotNull(orchestrator.currentUserSession())
    }

    @Test
    fun `previous session beyond max duration from metadata store`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val expiredStore = UserSessionMetadataStore(FakeKeyValueStore())
        expiredStore.save(
            UserSessionMetadata(
                startTimeMs = 0L,
                userSessionId = "old-id",
                userSessionNumber = 3L,
                maxDurationSecs = TimeUnit.MILLISECONDS.toSeconds(maxDurationMs),
                inactivityTimeoutSecs = TimeUnit.MILLISECONDS.toSeconds(inactivityMs),
            )
        )

        clock.tick(maxDurationMs)
        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = expiredStore)

        // Expired session is discarded; a fresh user session is created instead
        val session = checkNotNull(orchestrator.currentUserSession())
        assertNotEquals("old-id", session.userSessionId)
    }

    @Test
    fun `session is persisted when started`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val freshStore = UserSessionMetadataStore(FakeKeyValueStore())
        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = freshStore)

        val session = checkNotNull(orchestrator.currentUserSession())
        val stored = checkNotNull(freshStore.load())
        assertEquals(session.userSessionId, stored.userSessionId)
        assertEquals(session.startTimeMs, stored.startTimeMs)
        assertEquals(session.userSessionNumber, stored.userSessionNumber)
    }

    @Test
    fun `store is overridden for new user session`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val sharedStore = UserSessionMetadataStore(FakeKeyValueStore())
        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = sharedStore)

        val firstId = checkNotNull(orchestrator.currentUserSession()).userSessionId
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val secondId = checkNotNull(orchestrator.currentUserSession()).userSessionId
        assertNotEquals(firstId, secondId)

        val stored = checkNotNull(sharedStore.load())
        assertEquals(secondId, stored.userSessionId)
    }

    @Test
    fun `crash does not advance user session`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        createOrchestrator(AppState.FOREGROUND)

        val initialId = checkNotNull(orchestrator.currentUserSession()).userSessionId
        orchestrator.handleCrash("crash-id")
        // crash does not produce a new session part, so user session is unchanged
        assertEquals(initialId, orchestrator.currentUserSession()?.userSessionId)
    }

    @Test
    fun `persisted session uses stored max duration`() {
        val configMaxMs = TimeUnit.MINUTES.toMillis(5)
        val persistedMaxSecs = TimeUnit.MINUTES.toSeconds(10)
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = configMaxMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val persistedStore = UserSessionMetadataStore(FakeKeyValueStore())
        persistedStore.save(
            UserSessionMetadata(
                startTimeMs = clock.now(),
                userSessionId = "persisted-id",
                userSessionNumber = 5L,
                maxDurationSecs = persistedMaxSecs,
                inactivityTimeoutSecs = TimeUnit.MILLISECONDS.toSeconds(inactivityMs),
            )
        )

        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = persistedStore)

        // persisted 10-min duration used
        assertEquals("persisted-id", checkNotNull(orchestrator.currentUserSession()).userSessionId)

        // exceed duration
        clock.tick(TimeUnit.MINUTES.toMillis(11))
        orchestrator.onBackground()
        orchestrator.onForeground()
        val newSession = checkNotNull(orchestrator.currentUserSession())
        assertNotEquals("persisted-id", newSession.userSessionId)
    }

    @Test
    fun `persisted inactivity timeout is adopted and new session reverts to config`() {
        val configInactivityMs = TimeUnit.MINUTES.toMillis(5)
        val persistedInactivitySecs = TimeUnit.MINUTES.toSeconds(10)
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = configInactivityMs,
            )
        )
        val persistedStore = UserSessionMetadataStore(FakeKeyValueStore())
        persistedStore.save(
            UserSessionMetadata(
                startTimeMs = clock.now(),
                userSessionId = "persisted-id",
                userSessionNumber = 3L,
                maxDurationSecs = TimeUnit.MILLISECONDS.toSeconds(maxDurationMs),
                inactivityTimeoutSecs = persistedInactivitySecs,
            )
        )

        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = persistedStore)

        // restored session retains persisted inactivity timeout
        val restored = checkNotNull(orchestrator.currentUserSession())
        assertEquals("persisted-id", restored.userSessionId)
        assertEquals(persistedInactivitySecs, restored.inactivityTimeoutSecs)

        // new session should use current config inactivity timeout
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val newSession = checkNotNull(orchestrator.currentUserSession())
        assertNotEquals("persisted-id", newSession.userSessionId)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(configInactivityMs), newSession.inactivityTimeoutSecs)
    }

    @Test
    fun `clock shifted backwards discards restored session`() {
        configService = FakeConfigService(
            sessionBehavior = FakeUserSessionBehavior(
                maxSessionDurationMs = maxDurationMs,
                sessionInactivityTimeoutMs = inactivityMs,
            )
        )
        val store = UserSessionMetadataStore(FakeKeyValueStore())
        store.save(
            UserSessionMetadata(
                startTimeMs = clock.now() + 1_000L,
                userSessionId = "future-id",
                userSessionNumber = 5L,
                maxDurationSecs = TimeUnit.MILLISECONDS.toSeconds(maxDurationMs),
                inactivityTimeoutSecs = TimeUnit.MILLISECONDS.toSeconds(inactivityMs),
            )
        )

        createOrchestrator(AppState.FOREGROUND, metadataStoreOverride = store)

        val session = checkNotNull(orchestrator.currentUserSession())
        assertNotEquals("future-id", session.userSessionId)

        val errors = logger.internalErrorMessages
        assertEquals(1, errors.size)
        assertEquals(InternalErrorType.CLOCK_BACKWARDS_SHIFT.toString(), errors[0].msg)
    }

    @Test
    fun `user session start time matches initial session part start time`() {
        createOrchestrator(AppState.FOREGROUND)
        val userSession = checkNotNull(orchestrator.currentUserSession())
        val sessionPart = checkNotNull(sessionTracker.getActiveSession())
        assertEquals(userSession.startTimeMs, sessionPart.startTime)
    }

    @Test
    fun `user session start time matches session part start time after manual end`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(5000)
        orchestrator.endSessionWithManual()
        val userSession = checkNotNull(orchestrator.currentUserSession())
        val sessionPart = checkNotNull(sessionTracker.getActiveSession())
        assertEquals(userSession.startTimeMs, sessionPart.startTime)
    }

    private fun assertHeartbeatMatchesClock() {
        val attr = checkNotNull(destination.attributes[EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO])
        assertEquals(clock.now(), attr.toLong().nanosToMillis())
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

    private fun validateSession(
        sessionSpan: EmbraceSdkSpan?,
        endTimeMs: Long,
        endType: LifeEventType,
    ) {
        assertEquals(endType, endType)
        assertEquals(endTimeMs, checkNotNull(sessionSpan).snapshot()?.endTimeNanos?.nanosToMillis())
    }
}
