package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.internal.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionBehaviorImplImplTest {

    private val local = SessionLocalConfig(
        sessionComponents = setOf("breadcrumbs"),
        fullSessionEvents = setOf("crash")
    )

    private val remote = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            isEnabled = true,
            sessionComponents = setOf("test"),
            fullSessionEvents = setOf("test2")
        ),
        maxSessionProperties = 57,
    )

    @Test
    fun testDefaults() {
        with(fakeSessionBehavior()) {
            assertEquals(emptySet<String>(), getFullSessionEvents())
            assertNull(getSessionComponents())
            assertFalse(isGatingFeatureEnabled())
            assertFalse(isSessionControlEnabled())
            assertEquals(10, getMaxSessionProperties())
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeSessionBehavior(localCfg = { local })) {
            assertEquals(setOf("breadcrumbs"), getSessionComponents())
            assertEquals(setOf("crash"), getFullSessionEvents())
            assertTrue(isGatingFeatureEnabled())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(fakeSessionBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertTrue(isGatingFeatureEnabled())
            assertTrue(isSessionControlEnabled())
            assertEquals(setOf("test"), getSessionComponents())
            assertEquals(setOf("test2"), getFullSessionEvents())
            assertEquals(57, getMaxSessionProperties())
        }
    }

    @Test
    fun `test upper case full session events`() {
        val behavior = fakeSessionBehavior(
            remoteCfg = {
                buildGatingConfig(setOf("CRASHES", "ERRORS"))
            }
        )
        assertEquals(setOf("crashes", "errors"), behavior.getFullSessionEvents())
    }

    @Test
    fun `test lower case full session events`() {
        val behavior = fakeSessionBehavior(
            remoteCfg = {
                buildGatingConfig(setOf("crashes", "errors"))
            }
        )
        assertEquals(setOf("crashes", "errors"), behavior.getFullSessionEvents())
    }

    private fun buildGatingConfig(events: Set<String>) = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            fullSessionEvents = events
        )
    )
}
