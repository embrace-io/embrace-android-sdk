package io.embrace.android.embracesdk.testcases.persistence

import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getSessionPartId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeProjectConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testframework.OtelSdkMode
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbracePayloadAssertionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.assertions.Placeholder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Shared setup for the tests that verify that a session payload reaches the server, and looks the same, whether it
 * was persisted in single-file mode or in multi-file mode.
 *
 * Every case runs under both modes, and under each [OtelSdkMode]. Only one of them may deliver a given session part -
 * see [deliveredParts]. The cases are split across subclasses so that Gradle can run them in different forks.
 */
internal abstract class MultiFilePersistenceParityTestBase(
    protected val persistenceMode: PersistenceMode,
    otelSdkMode: OtelSdkMode,
) {

    internal enum class PersistenceMode { SINGLE_FILE, MULTI_FILE }

    private val backgroundActivityEnabled = BackgroundActivityRemoteConfig(100f)

    /**
     * The remote config for the mode under test.
     */
    protected fun remoteConfig(
        backgroundActivity: BackgroundActivityRemoteConfig = backgroundActivityEnabled,
    ) = RemoteConfig(
        pctMultiFilePersistenceEnabled = when (persistenceMode) {
            PersistenceMode.MULTI_FILE -> 100.0f
            PersistenceMode.SINGLE_FILE -> 0.0f
        },
        backgroundActivityConfig = backgroundActivity,
    )

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule(otelSdkMode) {
        EmbraceSetupInterface(
            workersToFake = listOf(
                Worker.Background.SessionPersistenceWorker,
                Worker.Background.PeriodicCacheWorker,
                Worker.Background.NonIoRegWorker,
                Worker.Background.IoRegWorker,
            ),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.SessionPersistenceWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.IoRegWorker).blockingMode = false
        }
    }

    /**
     * Returns the envelope delivered for each session part.
     *
     * Exactly one envelope is expected per part: only one persistence mode may own a part, so a
     * second envelope for the same part means both modes delivered it. `getSessionEnvelopes`
     * waits for an exact count, so a duplicate delivery fails here rather than passing silently.
     */
    protected fun EmbracePayloadAssertionInterface.deliveredParts(
        expectedParts: Int = 1,
        state: ProcessState = ProcessState.FOREGROUND,
    ): List<Envelope<SessionPartPayload>> {
        val envelopes = getSessionEnvelopes(expectedParts, state, assertOrdering = false)
        assertEquals(
            "wrong number of distinct session parts delivered",
            expectedParts,
            envelopes.map(Envelope<SessionPartPayload>::getSessionPartId).distinct().size,
        )
        return envelopes
    }

    protected fun EmbracePayloadAssertionInterface.assertMatchesGoldenFile(
        envelope: Envelope<SessionPartPayload>,
    ) {
        val sessionSpan = envelope.findSessionPartSpan()
        validatePayloadAgainstGoldenFile(
            payload = sessionSpan.copy(attributes = sessionSpan.attributes?.sorted()),
            goldenFileName = GOLDEN_FILE,
            placeholders = mapOf(
                Placeholder.USER_SESSION_ID to envelope.getUserSessionId(),
                Placeholder.SESSION_PART_ID to envelope.getSessionPartId(),
            ),
        )
    }

    /**
     * Asserts the basic shape of a delivered session payload. Both persistence paths must satisfy
     * this identically.
     */
    protected fun assertMinimalSessionPayload(envelope: Envelope<SessionPartPayload>) {
        assertEquals("spans", envelope.type)
        assertEquals("2.5.1", envelope.resource?.appVersion)

        val sessionSpan = envelope.findSessionPartSpan()
        assertEquals(SESSION_SPAN_NAME, sessionSpan.name)
        assertNotNull(sessionSpan.endTimeNanos)
        assertEquals("foreground", sessionSpan.attributes?.findAttributeValue(EmbSessionAttributes.EMB_STATE))
    }

    /**
     * Changes everything in the envelope that a session part persists outside its spans: the user
     * info, and the envelope resource via the hosted SDK version.
     */
    @Suppress("DEPRECATION")
    protected fun EmbraceActionInterface.changeEnvelopeMetadata(token: String) {
        embrace.setUserIdentifier("$token-id")
        embrace.setUsername("$token-name")
        embrace.setUserEmail("$token@domain.com")
        embrace.clearAllUserPersonas()
        embrace.addUserPersona("${token}_persona")
        EmbraceInternalApi.flutterInternalInterface.setEmbraceFlutterSdkVersion("$token-sdk")
    }

    protected fun Envelope<SessionPartPayload>.assertEnvelopeMetadata(token: String) {
        val metadata = checkNotNull(metadata)
        assertEquals("$token-id", metadata.userId)
        assertEquals("$token-name", metadata.username)
        assertEquals("$token@domain.com", metadata.email)
        assertEquals(
            setOf("${token}_persona"),
            metadata.personas.orEmpty().filter { it.endsWith("_persona") }.toSet(),
        )
        assertEquals("$token-sdk", resource?.hostedSdkVersion)
    }

    protected fun Envelope<SessionPartPayload>.allSpanNames(): List<String> =
        (data.spans.orEmpty() + data.spanSnapshots.orEmpty()).mapNotNull(Span::name)

    /**
     * Sorts a span's attributes: an attribute map is unordered by nature, so the golden file can
     * only be compared against a stable ordering.
     */
    private fun List<Attribute>.sorted(): List<Attribute> = sortedWith(compareBy({ it.key }, { it.data }))

    internal companion object {
        const val SESSION_SPAN_NAME = "emb-session"
        const val BEFORE = "before"
        const val AFTER = "after"

        val hostedSdkConfig = FakeInstrumentedConfig(
            project = FakeProjectConfig(appId = "abcde", appFramework = "flutter"),
        )
        private const val GOLDEN_FILE = "multi_file_parity_session_part_span.json"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun modes(): List<Array<Any>> = PersistenceMode.entries.flatMap { persistenceMode ->
            OtelSdkMode.entries.map { otelSdkMode -> arrayOf(persistenceMode, otelSdkMode) }
        }
    }
}
