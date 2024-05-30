package io.embrace.android.embracesdk.payload

import com.squareup.moshi.JsonDataException
import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import io.embrace.android.embracesdk.fakeBackgroundActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class BackgroundActivityMessageTest {

    private val backgroundActivity = fakeBackgroundActivity()
    private val info = SessionMessage(backgroundActivity)

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("bg_activity_message_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<SessionMessage>("bg_activity_message_expected.json")
        assertNotNull(obj)
        assertEquals(backgroundActivity.startTime, obj.session.startTime)
    }

    @Test(expected = JsonDataException::class)
    fun testEmptyObject() {
        deserializeEmptyJsonString<SessionMessage>()
    }
}
