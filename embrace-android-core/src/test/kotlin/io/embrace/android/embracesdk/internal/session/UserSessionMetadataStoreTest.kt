package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.fakes.FakeKeyValueStore
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        maxDurationSecs = 3600L,
        inactivityTimeoutSecs = 1800L,
        partIndex = 1,
        lastActivityMs = 5000L,
    )
    private val metadata2 = UserSessionMetadata(
        startTimeMs = 2000L,
        userSessionId = "test-uuid-2",
        userSessionNumber = 5L,
        maxDurationSecs = 7200L,
        inactivityTimeoutSecs = 3600L,
        partIndex = 2,
        lastActivityMs = 9000L,
    )
    private val metadataBackgroundOnly = UserSessionMetadata(
        startTimeMs = 3000L,
        userSessionId = "test-uuid-3",
        userSessionNumber = 7L,
        maxDurationSecs = 3600L,
        inactivityTimeoutSecs = 1800L,
        partIndex = 4,
        lastActivityMs = 3000L,
        isBackgroundOnly = true,
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
        metadata.assertMetadataEquals(metadataStore.load())
    }

    @Test
    fun `save and load background only metadata`() {
        metadataStore.save(metadataBackgroundOnly)
        val loaded = metadataStore.load()
        metadataBackgroundOnly.assertMetadataEquals(loaded)
        assertEquals(true, loaded?.isBackgroundOnly)
    }

    @Test
    fun `session persisted without the background marker loads as a regular session`() {
        metadataStore.save(metadata)
        val loaded = checkNotNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART))
        metadata.assertMetadataEquals(loaded)
        assertFalse(loaded.isBackgroundOnly)
    }

    @Test
    fun `values can be overwritten`() {
        metadataStore.save(metadata)
        metadataStore.save(metadata2)
        metadata2.assertMetadataEquals(metadataStore.load())
    }

    @Test
    fun `clear causes load to return null`() {
        metadataStore.save(metadata)
        metadataStore.clear()
        assertNull(metadataStore.load())
    }

    @Test
    fun `load returns null when session id is missing`() {
        assertNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_USER_SESSION_ID))
    }

    @Test
    fun `load returns null when start timestamp is missing`() {
        assertNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_USER_SESSION_START_TS))
    }

    @Test
    fun `load returns null when session number is missing`() {
        assertNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_USER_SESSION_NUMBER))
    }

    @Test
    fun `load returns null when max duration is missing`() {
        assertNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_USER_SESSION_MAX_DURATION_SECONDS))
    }

    @Test
    fun `load returns null when inactivity timeout is missing`() {
        assertNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_USER_SESSION_INACTIVITY_TIMEOUT_SECONDS))
    }

    @Test
    fun `load returns null when part index is missing`() {
        assertNull(saveAndRemoveFromRawMap(metadata, EmbSessionAttributes.EMB_USER_SESSION_PART_INDEX))
    }

    private fun getRawMap(): MutableMap<String, String> = checkNotNull(kvStore.getStringMap("embrace.user_session")).toMutableMap()

    private fun saveAndRemoveFromRawMap(
        sessionMetadata: UserSessionMetadata,
        attribute: String,
    ): UserSessionMetadata? {
        metadataStore.save(sessionMetadata)
        kvStore.edit {
            putStringMap(
                "embrace.user_session",
                getRawMap().apply {
                    remove(attribute)
                }
            )
        }
        return metadataStore.load()
    }

    private fun UserSessionMetadata.assertMetadataEquals(other: UserSessionMetadata?) {
        checkNotNull(other)
        assertEquals(startTimeMs, other.startTimeMs)
        assertEquals(userSessionId, other.userSessionId)
        assertEquals(userSessionNumber, other.userSessionNumber)
        assertEquals(maxDurationSecs, other.maxDurationSecs)
        assertEquals(inactivityTimeoutSecs, other.inactivityTimeoutSecs)
        assertEquals(partIndex, other.partIndex)
        assertEquals(lastActivityMs, other.lastActivityMs)
        assertEquals(isBackgroundOnly, other.isBackgroundOnly)
    }
}
