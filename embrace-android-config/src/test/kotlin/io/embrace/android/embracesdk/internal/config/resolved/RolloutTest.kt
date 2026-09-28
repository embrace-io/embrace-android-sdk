package io.embrace.android.embracesdk.internal.config.resolved

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class RolloutTest {

    @Test
    fun `short-circuits do not read bucket`() {
        assertNull(rolloutEnabled(null, unreadBucket))
        assertEquals(false, rolloutEnabled(0f, unreadBucket))
        assertEquals(false, rolloutEnabled(-1f, unreadBucket))
        assertEquals(false, rolloutEnabled(100.1f, unreadBucket))
        assertEquals(true, rolloutEnabled(100f, unreadBucket))
    }

    @Test
    fun `pct is compared against bucket`() {
        val bucket = lazy { 5.08f }
        assertEquals(false, rolloutEnabled(5f, bucket))
        assertEquals(true, rolloutEnabled(5.08f, bucket))
        assertEquals(true, rolloutEnabled(50f, bucket))
    }
}
