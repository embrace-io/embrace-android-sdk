package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class UserSessionMetadataStoreTest {

    private lateinit var kvStore: FakeKeyValueStore
    private lateinit var metadataStore: UserSessionMetadataStore

    private val metadata = UserSessionMetadata(
        startTimeMs = 1000L,
        userSessionId = "test-uuid",
        userSessionNumber = 3L,
        maxDurationMins = 60L,
        inactivityTimeoutMins = 30L,
    )
    private val metadata2 = UserSessionMetadata(
        startTimeMs = 2000L,
        userSessionId = "test-uuid-2",
        userSessionNumber = 5L,
        maxDurationMins = 120L,
        inactivityTimeoutMins = 60L,
    )

    @Before
    fun setUp() {
        kvStore = FakeKeyValueStore()
        metadataStore = UserSessionMetadataStore(kvStore)
    }

    @Test
    fun `load returns null when store is empty`() {
        assertNull(metadataStore.load())
    }

    @Test
    fun `save and load round-trips all fields`() {
        metadataStore.save(metadata)
        val loaded = checkNotNull(metadataStore.load())

        assertEquals(metadata.startTimeMs, loaded.startTimeMs)
        assertEquals(metadata.userSessionId, loaded.userSessionId)
        assertEquals(metadata.userSessionNumber, loaded.userSessionNumber)
        assertEquals(metadata.maxDurationMins, loaded.maxDurationMins)
        assertEquals(metadata.inactivityTimeoutMins, loaded.inactivityTimeoutMins)
    }

    @Test
    fun `values can be overwritten`() {
        metadataStore.save(metadata)
        metadataStore.save(metadata2)
        val loaded = checkNotNull(metadataStore.load())

        assertEquals(metadata2.startTimeMs, loaded.startTimeMs)
        assertEquals(metadata2.userSessionId, loaded.userSessionId)
        assertEquals(metadata2.userSessionNumber, loaded.userSessionNumber)
        assertEquals(metadata2.maxDurationMins, loaded.maxDurationMins)
        assertEquals(metadata2.inactivityTimeoutMins, loaded.inactivityTimeoutMins)
    }

    @Test
    fun `clear causes load to return null`() {
        metadataStore.save(metadata)
        metadataStore.clear()
        assertNull(metadataStore.load())
    }

    @Test
    fun `load returns null when session id is missing`() {
        metadataStore.save(metadata)
        val stored = checkNotNull(kvStore.getStringMap("embrace.user_session")).toMutableMap()
        stored.remove(EmbSessionAttributes.EMB_USER_SESSION_ID)
        kvStore.edit { putStringMap("embrace.user_session", stored) }

        assertNull(metadataStore.load())
    }

    @Test
    fun `load returns null when start timestamp is missing`() {
        metadataStore.save(metadata)
        val stored = checkNotNull(kvStore.getStringMap("embrace.user_session")).toMutableMap()
        stored.remove(EmbSessionAttributes.EMB_USER_SESSION_START_TS)
        kvStore.edit { putStringMap("embrace.user_session", stored) }

        assertNull(metadataStore.load())
    }

    @Test
    fun `load returns null when session number is missing`() {
        metadataStore.save(metadata)
        val stored = checkNotNull(kvStore.getStringMap("embrace.user_session")).toMutableMap()
        stored.remove(EmbSessionAttributes.EMB_USER_SESSION_NUMBER)
        kvStore.edit { putStringMap("embrace.user_session", stored) }

        assertNull(metadataStore.load())
    }

    @Test
    fun `load returns null when max duration is missing`() {
        metadataStore.save(metadata)
        val stored = checkNotNull(kvStore.getStringMap("embrace.user_session")).toMutableMap()
        stored.remove(EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_MINUTES)
        kvStore.edit { putStringMap("embrace.user_session", stored) }

        assertNull(metadataStore.load())
    }

    @Test
    fun `load returns null when inactivity timeout is missing`() {
        metadataStore.save(metadata)
        val stored = checkNotNull(kvStore.getStringMap("embrace.user_session")).toMutableMap()
        stored.remove(EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_MINUTES)
        kvStore.edit { putStringMap("embrace.user_session", stored) }

        assertNull(metadataStore.load())
    }
}
