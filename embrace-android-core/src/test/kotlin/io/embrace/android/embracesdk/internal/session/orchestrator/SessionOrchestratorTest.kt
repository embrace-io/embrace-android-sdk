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
import io.embrace.android.embracesdk.fakes.TestUuidSource
import io.embrace.android.embracesdk.fakes.behavior.FakeUserSessionBehavior
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.arch.startup.StartupType
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
import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.id.SessionPartTrackerImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.KeyValueStoreEditor
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
internal class SessionOrchestratorTest {

    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var payloadFactory: PayloadFactoryImpl
    private lateinit var payloadCollator: FakePayloadMessageCollator
    private lateinit var logEnvelopeSource: FakeLogEnvelopeSource
    private lateinit var appStateTracker: FakeAppStateTracker
    private lateinit var clock: FakeClock
    private lateinit var store: FakePayloadStore
    private lateinit var userSessionPropertiesService: UserSessionPropertiesService
    private lateinit var sessionTracker: SessionPartTracker
    private lateinit var payloadCachingService: PayloadCachingService
    private lateinit var sessionCacheExecutor: BlockingScheduledExecutorService
    private lateinit var inactivityWorkerExecutor: BlockingScheduledExecutorService
    private lateinit var instrumentationRegistry: InstrumentationRegistry
    private lateinit var fakeDataSource: FakeDataSource
    private lateinit var logger: FakeInternalLogger
    private lateinit var startupClassifier: StartupClassifierImpl
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private lateinit var destination: FakeTelemetryDestination
    private var orchestratorStartTimeMs: Long = 0

    private val maxDurationMs = TimeUnit.MINUTES.toMillis(10)
    private val inactivityMs = TimeUnit.MINUTES.toMillis(5)

    @Before
    fun setUp() {
        clock = FakeClock()
        logger = FakeInternalLogger(throwOnInternalError = false)
        startupClassifier = StartupClassifierImpl()
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestrator(AppState.BACKGROUND)
        assertEquals(orchestrator, appStateTracker.listeners.single())
        assertEquals(0, payloadCollator.sessionCount.get())
        assertEquals(1, payloadCollator.baCount.get())
        assertEquals(sessionTracker.getActiveSessionPartId(), currentSessionPartSpan.getSessionId())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
        with(checkNotNull(activeUserSession())) {
            assertEquals(1L, userSessionNumber)
            assertNull(isBackgroundOnly)
            assertNull(startupClassifier.startupType())
        }
    }

    @Test
    fun `test initial behavior in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        assertEquals(orchestrator, appStateTracker.listeners.single())
        assertEquals(1, payloadCollator.sessionCount.get())
        assertEquals(0, payloadCollator.baCount.get())
        assertEquals(sessionTracker.getActiveSessionPartId(), currentSessionPartSpan.getSessionId())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
        // starting in fg produces user session
        assertNotNull(orchestrator.currentUserSession())
    }

    @Test
    fun `test on foreground call after starting in background`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        val foregroundTime = clock.now()
        val sessionSpan = currentSessionPartSpan.sessionSpan
        val initialUserSession = activeUserSession()
        orchestrator.onForeground()
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = foregroundTime,
            endType = LifeEventType.BKGND_STATE
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        with(checkNotNull(activeUserSession())) {
            assertEquals(initialUserSession.userSessionId, userSessionId)
            assertEquals(false, isBackgroundOnly)
        }
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
    fun `end session with manual in background rotates user session and stores payload`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val firstUserSession = activeUserSession()

        clock.tick(1000)
        orchestrator.onBackground()
        val storedBefore = store.storedSessionPartPayloads.size

        clock.tick(10000)
        orchestrator.endSessionWithManual()

        assertEquals(storedBefore + 1, store.storedSessionPartPayloads.size)
        val secondUserSession = activeUserSession()
        assertNotEquals(firstUserSession.userSessionId, secondUserSession.userSessionId)
    }

    @Test
    fun `backgrounding with background activity enabled does not cache empty crash envelope`() {
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.onBackground()
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `backgrounding with background activity disabled caches empty crash envelope`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = FakeConfigService(backgroundActivityBehavior = backgroundActivityBehavior(false)),
        )
        orchestrator.onBackground()
        assertEquals(1, store.cachedEmptyCrashPayloads.size)
    }

    @Test
    fun `foregrounding with background activity disabled does not cache empty crash envelope`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = FakeConfigService(backgroundActivityBehavior = backgroundActivityBehavior(false)),
        )
        orchestrator.onForeground()
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `test manual session end disabled for session gating`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = FakeConfigService(
                sessionBehavior = FakeUserSessionBehavior(sessionControlEnabled = true)
            ),
        )

        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual()
        assertEquals(1, payloadCollator.sessionCount.get())
        assertTrue(store.storedSessionPartPayloads.isEmpty())
    }

    @Test
    fun `ending session manually above time threshold succeeds`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = FakeConfigService(),
        )
        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual()
        assertEquals(2, payloadCollator.sessionCount.get())
        checkNotNull(store.storedSessionPartPayloads.last().first)
    }

    @Test
    fun `first manual end always succeeds regardless of time since session start`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = FakeConfigService(),
        )
        clock.tick(1000)

        orchestrator.endSessionWithManual()
        assertEquals(2, payloadCollator.sessionCount.get())
        checkNotNull(store.storedSessionPartPayloads.last().first)
    }

    @Test
    fun `cool-off window is measured from last manual end`() {
        val maxDuration = 2000L
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(customMaxSessionDurationMs = maxDuration),
        )

        // first manual end
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val sessionAfterManualEnd = activeUserSession()

        // max duration exceeded
        runTimerThread(maxDuration)
        val sessionAfterRollover = activeUserSession()
        assertNotEquals(sessionAfterManualEnd.userSessionId, sessionAfterRollover.userSessionId)

        // 5001ms since last manual end should be allowed
        clock.tick(3001)
        orchestrator.endSessionWithManual()
        val sessionAfterSecondManual = activeUserSession()
        assertNotEquals(sessionAfterRollover.userSessionId, sessionAfterSecondManual.userSessionId)
    }

    @Test
    fun `rate limit of manual end`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = FakeConfigService(),
        )

        clock.tick(10000)
        orchestrator.endSessionWithManual()
        orchestrator.endSessionWithManual()
        assertEquals(2, payloadCollator.sessionCount.get())

        clock.tick(4000)
        orchestrator.endSessionWithManual()

        clock.tick(2000)
        orchestrator.endSessionWithManual()
        assertEquals(3, payloadCollator.sessionCount.get())
    }

    @Test
    fun `ending session manually when no session exists does not start a new session`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = FakeConfigService(),
        )
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
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val first = activeUserSession()
        assertEquals(1L, first.userSessionNumber)
        assertEquals(1, first.partIndex)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(maxDurationMs), first.maxDurationSecs)
        assertEquals(TimeUnit.MILLISECONDS.toSeconds(inactivityMs), first.inactivityTimeoutSecs)
        assertNotNull(first.userSessionId)

        // within max duration — user session stays the same
        clock.tick(maxDurationMs)
        orchestrator.onBackground()
        orchestrator.onForeground()
        val repeat = activeUserSession()
        assertEquals(first.userSessionId, repeat.userSessionId)
        assertEquals(3, repeat.partIndex)

        // exceeds max duration in the background, so the user session that will be created for the new part will be background-only
        clock.tick(1)
        orchestrator.onBackground()

        val backgroundOnlySession = activeUserSession()
        assertNotEquals(first.userSessionId, backgroundOnlySession.userSessionId)
        assertEquals(2L, backgroundOnlySession.userSessionNumber)
        assertEquals(true, backgroundOnlySession.isBackgroundOnly)

        // foregrounding ends the background-only user session and starts a regular one
        orchestrator.onForeground()
        val second = activeUserSession()
        assertNotEquals(backgroundOnlySession.userSessionId, second.userSessionId)
        assertEquals(3L, second.userSessionNumber)
        assertEquals(false, second.isBackgroundOnly)
        assertEquals(1, second.partIndex)
    }

    @Test
    fun `user session manual end`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
        )

        val first = activeUserSession()
        assertEquals(1L, first.userSessionNumber)
        assertEquals(1, first.partIndex)

        // manual end always terminates and starts a new user session
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val second = activeUserSession()
        assertEquals(2L, second.userSessionNumber)
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(1, second.partIndex)
    }

    @Test
    fun `part index increments within user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        assertEquals(1, activeUserSession().partIndex)

        orchestrator.onBackground()
        assertEquals(2, activeUserSession().partIndex)

        orchestrator.onForeground()
        assertEquals(3, activeUserSession().partIndex)

        // new user session resets part index to 1
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        assertEquals(1, activeUserSession().partIndex)
    }

    @Test
    fun `user session ordinal persistence`() {
        val sharedOrdinalStore = FakeOrdinalStore()

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            ordinalStoreOverride = sharedOrdinalStore,
        )
        assertEquals(1L, activeUserSession().userSessionNumber)

        // New orchestrator with shared ordinal store but fresh metadata store
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            ordinalStoreOverride = sharedOrdinalStore,
            metadataStoreOverride = UserSessionMetadataStore(FakeKeyValueStore()),
        )
        assertEquals(2L, activeUserSession().userSessionNumber)
    }

    @Test
    fun `user session metadata attributes`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
        )

        val session = activeUserSession()
        val attrs = session.attributes
        assertEquals(session.startTimeMs, attrs[EmbSessionAttributes.EMB_USER_SESSION_START_TS])
        assertEquals(session.userSessionId, attrs[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals(session.userSessionNumber, attrs[EmbSessionAttributes.EMB_USER_SESSION_NUMBER])
        assertEquals(session.maxDurationSecs, attrs[EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS])
        assertEquals(session.inactivityTimeoutSecs, attrs[EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS])
    }

    @Test
    fun `restores active session from metadata store`() {
        val prePopulated = storeWithUserSession(userSessionId = "restored-id", userSessionNumber = 7L)

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = prePopulated,
        )

        val session = activeUserSession()
        assertEquals("restored-id", session.userSessionId)
        assertEquals(7L, session.userSessionNumber)
        assertEquals(2, session.partIndex)
    }

    @Test
    fun `exception in loadPersistedUserSession falls back to NoActiveSession`() {
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

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = throwingStore,
        )
        assertNotNull(orchestrator.currentUserSession())
    }

    @Test
    fun `previous session beyond max duration from metadata store`() {
        val expiredStore = storeWithUserSession(userSessionId = "old-id", startTimeMs = 0L, userSessionNumber = 3L)

        clock.tick(maxDurationMs)
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = expiredStore,
        )

        // Expired session is discarded; a fresh user session is created instead
        val session = activeUserSession()
        assertNotEquals("old-id", session.userSessionId)
    }

    @Test
    fun `process killed in background after inactivity timeout - new user session on restart`() {
        val store = storeWithUserSession(userSessionId = "bg-killed-id", partIndex = 2)

        // simulate process restart after inactivity timeout
        clock.tick(inactivityMs + 1)
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = store,
        )

        val session = activeUserSession()
        assertNotEquals("bg-killed-id", session.userSessionId)
    }

    @Test
    fun `process killed in background before inactivity timeout - session is restored`() {
        val store = storeWithUserSession(userSessionId = "bg-killed-id", partIndex = 2)

        // simulate process restart before inactivity timeout
        clock.tick(inactivityMs - 1)
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = store,
        )

        val session = activeUserSession()
        assertEquals("bg-killed-id", session.userSessionId)
    }

    @Test
    fun `session is persisted when started`() {
        val freshStore = UserSessionMetadataStore(FakeKeyValueStore())
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = freshStore,
        )

        val session = activeUserSession()
        val stored = checkNotNull(freshStore.load())
        assertEquals(session.userSessionId, stored.userSessionId)
        assertEquals(session.startTimeMs, stored.startTimeMs)
        assertEquals(session.userSessionNumber, stored.userSessionNumber)
    }

    @Test
    fun `store is overridden for new user session`() {
        val sharedStore = UserSessionMetadataStore(FakeKeyValueStore())
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = sharedStore,
        )

        val firstId = activeUserSession().userSessionId
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val secondId = activeUserSession().userSessionId
        assertNotEquals(firstId, secondId)

        val stored = checkNotNull(sharedStore.load())
        assertEquals(secondId, stored.userSessionId)
    }

    @Test
    fun `crash does not advance user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
        )

        val initialId = activeUserSession().userSessionId
        orchestrator.handleCrash("crash-id")
        // crash does not produce a new session part, so user session is unchanged
        assertEquals(initialId, orchestrator.currentUserSession()?.userSessionId)
    }

    @Test
    fun `persisted session uses stored max duration`() {
        val configMaxMs = TimeUnit.MINUTES.toMillis(5)
        val persistedMaxDurationMs = TimeUnit.MINUTES.toMillis(10)
        val persistedStore = storeWithUserSession(
            userSessionId = "persisted-id",
            userSessionNumber = 5L,
            persistedMaxDurationMs = persistedMaxDurationMs,
        )

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(customMaxSessionDurationMs = configMaxMs),
            metadataStoreOverride = persistedStore,
        )

        // persisted 10-min duration used
        assertEquals("persisted-id", activeUserSession().userSessionId)

        // exceed duration
        clock.tick(TimeUnit.MINUTES.toMillis(11))
        orchestrator.onBackground()
        orchestrator.onForeground()
        val newSession = activeUserSession()
        assertNotEquals("persisted-id", newSession.userSessionId)
    }

    @Test
    fun `persisted inactivity timeout is adopted and new session reverts to config`() {
        val configInactivitySecs = TimeUnit.MINUTES.toSeconds(5)
        val persistedInactivitySecs = TimeUnit.MINUTES.toSeconds(10)
        val persistedStore = storeWithUserSession(
            userSessionId = "persisted-id",
            userSessionNumber = 3L,
            persistedInactivityTimeoutMs = TimeUnit.SECONDS.toMillis(persistedInactivitySecs),
        )

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(
                customInactivityTimeoutMs = TimeUnit.SECONDS.toMillis(configInactivitySecs)
            ),
            metadataStoreOverride = persistedStore,
        )

        // restored session retains persisted inactivity timeout
        val restored = activeUserSession()
        assertEquals("persisted-id", restored.userSessionId)
        assertEquals(persistedInactivitySecs, restored.inactivityTimeoutSecs)

        // new session should use current config inactivity timeout
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val newSession = activeUserSession()
        assertNotEquals("persisted-id", newSession.userSessionId)
        assertEquals(configInactivitySecs, newSession.inactivityTimeoutSecs)
    }

    @Test
    fun `clock shifted backwards discards restored session`() {
        val store = storeWithUserSession(
            userSessionId = "future-id",
            startTimeMs = clock.now() + 1_000L,
            userSessionNumber = 5L,
        )

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
            metadataStoreOverride = store,
        )

        val session = activeUserSession()
        assertNotEquals("future-id", session.userSessionId)

        val errors = logger.internalErrorMessages
        assertEquals(1, errors.size)
        assertEquals(InternalErrorType.ClockBackwardsShift.toString(), errors[0].msg)
    }

    @Test
    fun `clock shifted backwards during new session part terminates and restarts user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val first = activeUserSession()
        assertEquals(1L, first.userSessionNumber)

        clock.setCurrentTime(first.startTimeMs - 1_000L)
        orchestrator.onBackground()

        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
        assertEquals(1, second.partIndex)
        assertEquals(true, second.isBackgroundOnly)

        val errors = logger.internalErrorMessages
        assertEquals(1, errors.size)
        assertEquals(InternalErrorType.ClockBackwardsShift.toString(), errors[0].msg)
    }

    @Test
    fun `exceeding inactivity timeout creates a background-only user session that ends on foreground`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val firstSession = activeUserSession()
        assertEquals(1L, firstSession.userSessionNumber)
        assertEquals(false, firstSession.isBackgroundOnly)

        orchestrator.onBackground()

        // exceeds inactivity timeout. the user session ends and a background-only user session is created
        runTimerThread(inactivityMs)

        val backgroundOnlySession = activeUserSession()
        assertNotEquals(firstSession.userSessionId, backgroundOnlySession.userSessionId)
        assertEquals(2L, backgroundOnlySession.userSessionNumber)
        assertEquals(true, backgroundOnlySession.isBackgroundOnly)

        // check that a background-only user session won't time out due to inactivity - it's already considered to be inactive
        runTimerThread(inactivityMs * 10)
        assertEquals(backgroundOnlySession.userSessionId, activeUserSession().userSessionId)

        // foregrounding ends the background-only user session and starts a regular one
        orchestrator.onForeground()

        val sessionAfterFg = activeUserSession()
        assertNotEquals(backgroundOnlySession.userSessionId, sessionAfterFg.userSessionId)
        assertEquals(3L, sessionAfterFg.userSessionNumber)
        assertEquals(false, sessionAfterFg.isBackgroundOnly)

        // a regular user session will survive a foregrounding if it's within the inactivity grace period
        orchestrator.onBackground()
        clock.tick(1000)
        orchestrator.onForeground()
        assertEquals(sessionAfterFg.userSessionId, activeUserSession().userSessionId)
    }

    @Test
    fun `inactivity timeout not exceeded keeps existing user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundDisabledConfigService(),
        )

        val firstSession = activeUserSession()
        assertEquals(1L, firstSession.userSessionNumber)

        orchestrator.onBackground()

        // foreground before timer fires — timer is cancelled, same user session continues
        clock.tick(inactivityMs - 1)
        orchestrator.onForeground()
        clock.tick(inactivityMs + 1)

        val sessionAfterForeground = activeUserSession()
        assertEquals(firstSession.userSessionId, sessionAfterForeground.userSessionId)
        assertEquals(1L, sessionAfterForeground.userSessionNumber)
    }

    @Test
    fun `inactivity deadline elapsed before timer fires still creates new user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundDisabledConfigService(),
        )

        val firstSession = activeUserSession()
        assertEquals(1L, firstSession.userSessionNumber)

        orchestrator.onBackground()

        // deadline passes but the timer callback has NOT fired yet (simulates a race)
        clock.tick(inactivityMs + 1)

        orchestrator.onForeground()

        val secondSession = activeUserSession()
        assertNotEquals(firstSession.userSessionId, secondSession.userSessionId)
        assertEquals(2L, secondSession.userSessionNumber)
    }

    @Test
    fun `user session start time matches initial session part start time`() {
        createOrchestrator(AppState.FOREGROUND)
        val userSession = activeUserSession()
        val sessionPart = checkNotNull(sessionTracker.getActiveSessionPart())
        assertEquals(userSession.startTimeMs, sessionPart.startTime)
    }

    @Test
    fun `user session start time matches session part start time after manual end`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(5000)
        orchestrator.endSessionWithManual()
        val userSession = activeUserSession()
        val sessionPart = checkNotNull(sessionTracker.getActiveSessionPart())
        assertEquals(userSession.startTimeMs, sessionPart.startTime)
    }

    private fun assertHeartbeatMatchesClock() {
        val attr = checkNotNull(destination.attributes[EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO])
        assertEquals(clock.now(), attr.toLong().nanosToMillis())
    }

    private fun createOrchestrator(
        startingAppState: AppState,
        configService: FakeConfigService =
            FakeConfigService(backgroundActivityBehavior = backgroundActivityBehavior(true)),
        ordinalStoreOverride: FakeOrdinalStore? = null,
        metadataStoreOverride: UserSessionMetadataStore? = null,
    ) {
        store = FakePayloadStore()
        appStateTracker = FakeAppStateTracker(startingAppState)
        currentSessionPartSpan = FakeCurrentSessionPartSpan(clock).apply { initializeService(clock.now()) }
        destination = FakeTelemetryDestination()
        payloadCollator = FakePayloadMessageCollator(currentSessionPartSpan = currentSessionPartSpan)
        val payloadSourceModule = FakePayloadSourceModule()
        logEnvelopeSource = payloadSourceModule.logEnvelopeSource
        payloadFactory = PayloadFactoryImpl(
            payloadMessageCollator = payloadCollator,
            logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
            configService = configService,
            logger = logger,
        )
        userSessionPropertiesService = FakeUserSessionPropertiesService()
        sessionTracker = SessionPartTrackerImpl(
            activityManager = null,
            logger = logger
        )
        sessionCacheExecutor = BlockingScheduledExecutorService(clock, true)
        inactivityWorkerExecutor = BlockingScheduledExecutorService(clock, true)
        payloadCachingService = PayloadCachingServiceImpl(
            PeriodicSessionPartCacher(
                BackgroundWorker(sessionCacheExecutor),
                logger
            ),
            clock,
            object : SessionIdProvider {
                override fun getCurrentSessionPartId(): String = sessionTracker.getActiveSessionPartId() ?: ""
                override fun getCurrentUserSessionId(): String = ""
                override fun getActiveSessionIds(): SessionIdsSnapshot =
                    SessionIdsSnapshot(userSessionId = "", sessionPartId = sessionTracker.getActiveSessionPartId() ?: "")
            },
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
            BackgroundWorker(inactivityWorkerExecutor),
            TestUuidSource(),
            startupClassifier,
        ).apply {
            start()
        }
        orchestratorStartTimeMs = clock.now()
        userSessionPropertiesService.addProperty("key", "value", PropertyScope.USER_SESSION)
    }

    @Test
    fun `max duration timeout creates new session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val first = activeUserSession()
        assertEquals(1L, first.userSessionNumber)

        runTimerThread(maxDurationMs)

        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
    }

    @Test
    fun `max duration timer not scheduled when starting in background`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val initialUserSession = activeUserSession()

        // The background-startup window elapses, so the task run on the thread should classify it as background-only.
        // It should still be the active one as there should be no timers that end it.
        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS + 1)
        val current = activeUserSession()
        assertEquals(initialUserSession.userSessionId, current.userSessionId)
        assertEquals(true, current.isBackgroundOnly)
    }

    @Test
    fun `max duration timer cancelled on manual session end and rescheduled for new session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(),
        )

        val first = activeUserSession()
        assertEquals(1L, first.userSessionNumber)

        // manual end cancels the old timer and starts a new one
        clock.tick(10000)
        orchestrator.endSessionWithManual()
        val second = activeUserSession()
        assertEquals(2L, second.userSessionNumber)

        // old timer (from first session) fires but should be a no-op since the session changed
        runTimerThread()
        assertEquals(second.userSessionId, activeUserSession().userSessionId)

        // advance by the new session's full max duration and fire — now a new session should start
        runTimerThread(maxDurationMs)
        val third = activeUserSession()
        assertNotEquals(second.userSessionId, third.userSessionId)
        assertEquals(3L, third.userSessionNumber)
    }

    @Test
    fun `restored session schedules timer for remaining duration`() {
        val halfElapsed = maxDurationMs / 2
        val restoredStore = storeWithUserSession(
            userSessionId = "restored-id",
            startTimeMs = clock.now() - halfElapsed,
            lastActivityMs = clock.now(),
            userSessionNumber = 4L,
        )

        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = restoredStore,
        )

        val restored = activeUserSession()
        assertEquals("restored-id", restored.userSessionId)

        // advance to just before the remaining window expires — session should not rotate
        runTimerThread(halfElapsed - 1)
        assertEquals("restored-id", activeUserSession().userSessionId)

        // advance past the remaining window — timer fires, session rotates
        runTimerThread(1)
        val newSession = activeUserSession()
        assertNotEquals("restored-id", newSession.userSessionId)
    }

    @Test
    fun `max duration timer fires while backgrounded and rotates user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val first = activeUserSession()
        val storedBefore = store.storedSessionPartPayloads.size

        // Background well before max duration, then sit in BG past max duration without coming back to FG.
        clock.tick(1_000)
        orchestrator.onBackground()

        runTimerThread(maxDurationMs)

        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
        assertEquals(1, second.partIndex)

        // created while backgrounded, so the new uer session is background-only
        assertEquals(true, second.isBackgroundOnly)

        // The session part created by the BG max-duration rotation must remain BG.
        val activePart = checkNotNull(sessionTracker.getActiveSessionPart())
        assertEquals(AppState.BACKGROUND, activePart.appState)

        // Two extra parts are persisted: end of FG session at onBackground, and end of BG session at max-duration.
        assertEquals(storedBefore + 2, store.storedSessionPartPayloads.size)
    }

    @Test
    fun `max duration timer fires in background but does not create stranded part when capture disabled`() {
        // FakeConfigService's default backgroundActivityBehavior has bg capture disabled
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = sessionLimitsConfigService(customInactivityTimeoutMs = maxDurationMs * 4),
        )
        val first = activeUserSession()
        val baCountBefore = payloadCollator.baCount.get()

        clock.tick(1_000)
        orchestrator.onBackground()

        runTimerThread(maxDurationMs)

        // The user session must still rotate: max-duration is a hard limit independent of capture config.
        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)

        // No new BG session-part was created — capture is disabled, so the rotation produced no payload.
        assertEquals(baCountBefore, payloadCollator.baCount.get())
        assertNull(sessionTracker.getActiveSessionPart())
    }

    @Test
    fun `inactivity timer fires in background but no part is created when capture disabled`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundDisabledConfigService(),
        )
        val first = activeUserSession()

        clock.tick(1_000)
        orchestrator.onBackground()

        runTimerThread(inactivityMs)

        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
        assertEquals(true, second.isBackgroundOnly)
        assertNull(sessionTracker.getActiveSessionPart())

        orchestrator.onForeground()
        assertEquals(0, payloadCollator.baCount.get())
    }

    @Test
    fun `max duration timer not cancelled when transitioning to background`() {
        // Long inactivity so it can't fire before the max-duration timer and confound the result.
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = FakeConfigService(
                backgroundActivityBehavior = backgroundActivityBehavior(true),
                sessionBehavior = FakeUserSessionBehavior(
                    maxSessionDurationMs = maxDurationMs,
                    sessionInactivityTimeoutMs = maxDurationMs * 4,
                ),
            ),
        )

        val first = activeUserSession()

        clock.tick(maxDurationMs / 2)
        orchestrator.onBackground()

        // advance past the max duration
        runTimerThread(maxDurationMs / 2 + 1)

        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        assertEquals(2L, second.userSessionNumber)
    }

    @Test
    fun `max duration timer not rescheduled when foregrounding before it has fired`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val first = activeUserSession()

        // Multiple FG/BG transitions before max duration must NOT re-schedule the timer.
        // A leaked timer would fire twice and rotate the user session prematurely.
        repeat(3) {
            clock.tick(maxDurationMs / 8)
            orchestrator.onBackground()
            clock.tick(maxDurationMs / 8)
            orchestrator.onForeground()
        }

        // Now advance past the original max-duration deadline and let the timer fire.
        runTimerThread(maxDurationMs)

        val second = activeUserSession()
        assertNotEquals(first.userSessionId, second.userSessionId)
        // Exactly one rotation: number 2, not 3+.
        assertEquals(2L, second.userSessionNumber)
    }

    @Test
    fun `max duration timer not scheduled for user session started in background`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        // trigger inactivity timeout to create new user session
        orchestrator.onBackground()
        runTimerThread(inactivityMs + 1)

        val rotatedInBg = activeUserSession()
        assertEquals(2L, rotatedInBg.userSessionNumber)
        assertEquals(1, rotatedInBg.partIndex)

        // tick past max duration threshold
        runTimerThread(maxDurationMs * 2)

        val current = activeUserSession()
        assertEquals(rotatedInBg.userSessionId, current.userSessionId)
        assertEquals(2L, current.userSessionNumber)
    }

    @Test
    fun `inactivity timer scheduled near end of FG session does not rotate BG session created by max duration`() {
        // Reproduces the screenshot scenario: inactivity < max-duration, user is FG for most of the session,
        // briefly backgrounds shortly before max-duration. Max-duration then fires in BG and rotates the user
        // session. The inactivity timer scheduled by the late onBackground anchors on the to-be-terminated
        // session — if it survives, it incorrectly rotates the freshly-created BG session.
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val first = activeUserSession()

        // Stay FG until just before max-duration, then background.
        val bgGapMs = inactivityMs / 4
        clock.tick(maxDurationMs - bgGapMs)
        orchestrator.onBackground()

        // Advance to max-duration; it fires in BG and rotates the user session.
        runTimerThread(bgGapMs + 1)

        val rotated = activeUserSession()
        assertNotEquals(first.userSessionId, rotated.userSessionId)
        assertEquals(2L, rotated.userSessionNumber)

        // Advance well past where the stale inactivity timer would have fired. A leaked timer would
        // rotate the BG session a second time and bump userSessionNumber to 3.
        runTimerThread(inactivityMs * 2)

        val current = activeUserSession()
        assertEquals(rotated.userSessionId, current.userSessionId)
        assertEquals(2L, current.userSessionNumber)
    }

    @Test
    fun `only background-only user session carries the marker attribute in its metadata`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val regular = activeUserSession()
        assertFalse(regular.attributes.containsKey(EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))

        orchestrator.onBackground()
        runTimerThread(inactivityMs)

        val backgroundOnlySession = activeUserSession()
        assertEquals(true, backgroundOnlySession.isBackgroundOnly)
        assertEquals("1", backgroundOnlySession.attributes[EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART])
    }

    @Test
    fun `manual end while in background creates a background-only user session`() {
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val first = activeUserSession()

        clock.tick(1000)
        orchestrator.onBackground()
        clock.tick(1000)
        orchestrator.endSessionWithManual()

        val backgroundOnlySession = activeUserSession()
        assertNotEquals(first.userSessionId, backgroundOnlySession.userSessionId)
        assertEquals(true, backgroundOnlySession.isBackgroundOnly)

        orchestrator.onForeground()
        val regular = activeUserSession()
        assertNotEquals(backgroundOnlySession.userSessionId, regular.userSessionId)
        assertEquals(false, regular.isBackgroundOnly)
    }

    @Test
    fun `restored background user session is reused when process starts in background`() {
        val store = storeWithUserSession(
            userSessionId = "bg-session-id",
            isBackgroundOnly = true
        )

        // Starting with the clock beyond the inactivity time should not result in a new background only session
        clock.tick(inactivityMs * 1)
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = store,
        )

        val restored = activeUserSession()
        assertEquals("bg-session-id", restored.userSessionId)
        assertEquals(true, restored.isBackgroundOnly)

        orchestrator.onForeground()
        val regular = activeUserSession()
        assertNotEquals("bg-session-id", regular.userSessionId)
        assertEquals(false, regular.isBackgroundOnly)
    }

    @Test
    fun `restored background user session past max duration is discarded`() {
        val store = storeWithUserSession(
            userSessionId = "bg-session-id",
            isBackgroundOnly = true
        )

        clock.tick(maxDurationMs + 1)
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = store,
        )

        // the expired session is not restored - a new unclassified one is active instead
        val initialUserSession = activeUserSession()
        assertNotEquals("bg-session-id", initialUserSession.userSessionId)
        assertNull(initialUserSession.isBackgroundOnly)

        // Foregrounding turns that new session into a regular, not background-only one
        orchestrator.onForeground()
        val regular = activeUserSession()
        assertEquals(initialUserSession.userSessionId, regular.userSessionId)
        assertEquals(false, regular.isBackgroundOnly)
    }

    @Test
    fun `restored background user session is replaced when process starts in foreground`() {
        val store = storeWithUserSession(
            userSessionId = "bg-session-id",
            isBackgroundOnly = true
        )

        clock.tick(1000)
        createOrchestrator(
            startingAppState = AppState.FOREGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = store,
        )

        val session = activeUserSession()
        assertNotEquals("bg-session-id", session.userSessionId)
        assertEquals(false, session.isBackgroundOnly)
    }

    @Test
    fun `background-startup window elapsing resolves unclassified session to background-only`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
        )

        val initialUserSession = activeUserSession()
        assertNull(initialUserSession.isBackgroundOnly)
        assertNull(startupClassifier.startupType())
        assertNull(destination.attributes[EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART])

        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS + 1)

        with(checkNotNull(activeUserSession())) {
            assertEquals(initialUserSession.userSessionId, userSessionId)
            assertEquals(true, isBackgroundOnly)
            assertEquals(StartupType.BACKGROUND, startupClassifier.startupType())
            assertEquals("1", destination.attributes[EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART])
        }

        // foregrounding ends the background-only session and starts a regular one
        orchestrator.onForeground()
        val regular = activeUserSession()
        assertNotEquals(initialUserSession.userSessionId, regular.userSessionId)
        assertEquals(false, regular.isBackgroundOnly)
    }

    @Test
    fun `foregrounding within background-startup window does not resolve the startup classification`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val initialUserSession = activeUserSession()

        clock.tick(1000)
        orchestrator.onForeground()

        with(checkNotNull(activeUserSession())) {
            assertEquals(initialUserSession.userSessionId, userSessionId)
            assertEquals(false, isBackgroundOnly)
            assertNull(startupClassifier.startupType())
        }

        // the previously scheduled timer should be cancelled and not classify the startup type
        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS)
        assertNull(startupClassifier.startupType())
    }

    @Test
    fun `unclassified user session is persisted as background-only during the window`() {
        val store = UserSessionMetadataStore(FakeKeyValueStore())
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = store,
        )

        val initialUserSession = activeUserSession()
        assertNull(initialUserSession.isBackgroundOnly)

        // persisted user session will presume it's background-only because it will only be read in this state if the process dies with
        // it being further classified
        val persisted = checkNotNull(store.load())
        assertEquals(initialUserSession.userSessionId, persisted.userSessionId)
        assertEquals(true, persisted.isBackgroundOnly)

        // foregrounding within the window rewrites the persisted copy as a regular session
        clock.tick(1000)
        orchestrator.onForeground()
        assertEquals(false, checkNotNull(store.load()).isBackgroundOnly)
    }

    @Test
    fun `manual end during the background-startup window terminates the pending session`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val pending = activeUserSession()

        clock.tick(1000)
        orchestrator.endSessionWithManual()

        // the replacement is created in the background, so it is committed background-only
        val replacement = activeUserSession()
        assertNotEquals(pending.userSessionId, replacement.userSessionId)
        assertEquals(true, replacement.isBackgroundOnly)

        // the previously scheduled timer should be cancelled and not classify the startup type
        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS + 1)
        assertNull(startupClassifier.startupType())
    }

    @Test
    fun `crash during the background-startup window leaves the pending session untouched`() {
        val store = UserSessionMetadataStore(FakeKeyValueStore())
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = store,
        )
        val initialUserSession = activeUserSession()

        orchestrator.handleCrash("crash-id")

        assertEquals(initialUserSession.userSessionId, activeUserSession().userSessionId)
        assertEquals(true, checkNotNull(store.load()).isBackgroundOnly)
    }

    @Test
    fun `restored regular user session for a new process continues in background as such past the background-startup window`() {
        val store = storeWithUserSession(userSessionId = "regular-id")

        clock.tick(1000)
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
            metadataStoreOverride = store,
        )

        with(checkNotNull(activeUserSession())) {
            assertEquals("regular-id", userSessionId)
            assertEquals(false, isBackgroundOnly)
            assertNull(startupClassifier.startupType())
        }

        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS + 1)

        with(checkNotNull(activeUserSession())) {
            assertEquals("regular-id", userSessionId)
            assertEquals(false, isBackgroundOnly)
            assertNull(startupClassifier.startupType())
        }
    }

    @Test
    fun `cold start foregrounding within the background-startup window is classified regular`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val initialUserSession = activeUserSession()

        // A slow Application.onCreate keeps the app in the background for most of the window before the first activity foregrounds.
        // As long as that happens within the background-startup window, the session created at process start should still
        // become regular user session.
        clock.tick(BACKGROUND_STARTUP_WINDOW_MS - 1)
        orchestrator.onForeground()

        with(checkNotNull(activeUserSession())) {
            assertEquals(initialUserSession.userSessionId, userSessionId)
            assertEquals(false, isBackgroundOnly)
        }

        // the previously scheduled timer should be cancelled
        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS + 1)
        with(checkNotNull(activeUserSession())) {
            assertEquals(initialUserSession.userSessionId, userSessionId)
            assertEquals(false, isBackgroundOnly)
            assertNull(startupClassifier.startupType())
        }
    }

    @Test
    fun `background startup timer doesn't reclassify startup if already classified`() {
        createOrchestrator(
            startingAppState = AppState.BACKGROUND,
            configService = backgroundEnabledConfigService(),
        )
        val initialUserSession = activeUserSession()

        startupClassifier.evaluateStartup(
            sdkInitEndMs = clock.now(),
            appInitEndMs = clock.now() + 1,
            postAppInitTimeMs = clock.now() + 2,
        )
        assertEquals(StartupType.COLD, startupClassifier.startupType())

        // The timer fires but doesn't reclassify the startup or classify the user session
        runTimerThread(BACKGROUND_STARTUP_WINDOW_MS + 1)
        assertNull(activeUserSession().isBackgroundOnly)
        assertNull(destination.attributes[EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART])

        // the imminent foregrounding that occurs after startup classification will classify the session
        orchestrator.onForeground()
        with(checkNotNull(activeUserSession())) {
            assertEquals(initialUserSession.userSessionId, userSessionId)
            assertEquals(false, isBackgroundOnly)
        }
    }

    private fun backgroundEnabledConfigService() = FakeConfigService(
        backgroundActivityBehavior = backgroundActivityBehavior(true),
        sessionBehavior = FakeUserSessionBehavior(
            maxSessionDurationMs = maxDurationMs,
            sessionInactivityTimeoutMs = inactivityMs,
        ),
    )

    private fun backgroundDisabledConfigService() = FakeConfigService(
        backgroundActivityBehavior = backgroundActivityBehavior(false),
        sessionBehavior = FakeUserSessionBehavior(
            maxSessionDurationMs = maxDurationMs,
            sessionInactivityTimeoutMs = inactivityMs,
        ),
    )

    private fun sessionLimitsConfigService(
        customMaxSessionDurationMs: Long = maxDurationMs,
        customInactivityTimeoutMs: Long = inactivityMs,
    ) = FakeConfigService(
        sessionBehavior = FakeUserSessionBehavior(
            maxSessionDurationMs = customMaxSessionDurationMs,
            sessionInactivityTimeoutMs = customInactivityTimeoutMs,
        )
    )

    private fun backgroundActivityBehavior(enabled: Boolean) = createBackgroundActivityBehavior(
        remoteCfg = RemoteConfig(
            backgroundActivityConfig = BackgroundActivityRemoteConfig(
                threshold = if (enabled) {
                    100f
                } else {
                    0f
                }
            )
        )
    )

    private fun storeWithUserSession(
        userSessionId: String,
        startTimeMs: Long = clock.now(),
        lastActivityMs: Long = startTimeMs,
        userSessionNumber: Long = 2L,
        partIndex: Int = 1,
        isBackgroundOnly: Boolean = false,
        persistedMaxDurationMs: Long = maxDurationMs,
        persistedInactivityTimeoutMs: Long = inactivityMs,
    ): UserSessionMetadataStore = UserSessionMetadataStore(FakeKeyValueStore()).apply {
        save(
            UserSessionMetadata.Classified(
                startTimeMs = startTimeMs,
                userSessionId = userSessionId,
                userSessionNumber = userSessionNumber,
                maxDurationSecs = TimeUnit.MILLISECONDS.toSeconds(persistedMaxDurationMs),
                inactivityTimeoutSecs = TimeUnit.MILLISECONDS.toSeconds(persistedInactivityTimeoutMs),
                partIndex = partIndex,
                lastActivityMs = lastActivityMs,
                isBackgroundOnly = isBackgroundOnly,
            )
        )
    }

    private fun activeUserSession(): UserSessionMetadata = checkNotNull(orchestrator.currentUserSession())

    private fun runTimerThread(timeIncrementBeforeUnblockMs: Long = 0L) {
        if (timeIncrementBeforeUnblockMs > 0L) {
            clock.tick(timeIncrementBeforeUnblockMs)
        }
        inactivityWorkerExecutor.runCurrentlyBlocked()
    }

    private fun validateSession(
        sessionSpan: EmbraceSdkSpan?,
        endTimeMs: Long,
        endType: LifeEventType,
    ) {
        assertEquals(
            endType.toString().lowercase(Locale.US),
            destination.attributes[EmbSessionAttributes.EMB_SESSION_END_TYPE],
        )
        assertEquals(endTimeMs, checkNotNull(sessionSpan).snapshot()?.endTimeNanos?.nanosToMillis())
    }
}
