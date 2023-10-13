package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSpansBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SpansBehaviorTest {
    private val remote = SpansRemoteConfig(
        100.0f
    )

    @Test
    fun testDefaults() {
        with(fakeSpansBehavior()) {
            assertFalse(isSpansEnabled())
        }
    }

    @Test
    fun testRemote() {
        with(fakeSpansBehavior(remoteConfig = { remote })) {
            assertTrue(isSpansEnabled())
        }
    }
}
