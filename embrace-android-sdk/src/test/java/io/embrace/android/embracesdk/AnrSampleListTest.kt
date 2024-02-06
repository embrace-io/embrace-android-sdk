package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.payload.AnrSample
import io.embrace.android.embracesdk.payload.AnrSampleList
import io.embrace.android.embracesdk.payload.extensions.size
import org.junit.Assert.assertEquals
import org.junit.Test

internal class AnrSampleListTest {

    @Test
    fun testIsEmpty() {
        val stacktraces = AnrSampleList(emptyList())
        assertEquals(0, stacktraces.size())

        val stacktraces2 = AnrSampleList(
            listOf(
                AnrSample(0, emptyList(), 0),
                AnrSample(1, emptyList(), 0),
                AnrSample(2, emptyList(), 0)
            )
        )
        assertEquals(3, stacktraces2.size())
        val expected = listOf(
            AnrSample(0, emptyList(), 0),
            AnrSample(1, emptyList(), 0),
            AnrSample(2, emptyList(), 0)
        )
        assertEquals(expected, stacktraces2.samples)
    }
}
