package io.embrace.android.embracesdk.internal.comms.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class EmbraceUrlTest {

    @Test
    fun `test endpoint()`() {
        val embraceUrlLogs = EmbraceUrl.create("https://embrace.io/logs")
        val embraceUrlOther = EmbraceUrl.create("https://embrace.io/other")

        assertEquals(Endpoint.LOGS, embraceUrlLogs.endpoint())
        assertEquals(Endpoint.UNKNOWN, embraceUrlOther.endpoint())
    }

    @Test
    fun `test equality`() {
        val embraceUrl1 = EmbraceUrl.create("https://embrace.io")
        val embraceUrl2 = EmbraceUrl.create("https://embrace.io")
        val embraceUrl3 = EmbraceUrl.create("http://embrace.io")
        val embraceUrl4 = EmbraceUrl.create("https://embrace.wtf")
        val embraceUrl5 = EmbraceUrl.create("https://embrace.io?err=1")
        val embraceUrl6 = EmbraceUrl.create("https://embrace.io:8080")

        assertEquals(embraceUrl1, embraceUrl1)
        assertEquals(embraceUrl1, embraceUrl2)
        assertNotEquals(embraceUrl1, embraceUrl3)
        assertNotEquals(embraceUrl1, embraceUrl4)
        assertNotEquals(embraceUrl1, embraceUrl5)
        assertNotEquals(embraceUrl1, embraceUrl6)
    }
}
