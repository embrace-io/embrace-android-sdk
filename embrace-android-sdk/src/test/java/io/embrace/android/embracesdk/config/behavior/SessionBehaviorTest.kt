package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SessionBehaviorTest {

    private val local = SessionLocalConfig(
        maxSessionSeconds = 120,
        asyncEnd = true,
        sessionComponents = setOf("breadcrumbs"),
        fullSessionEvents = setOf("crash"),
        sessionEnableErrorLogStrictMode = true
    )

    private val remote = RemoteConfig(
        sessionConfig = SessionRemoteConfig(
            isEnabled = true,
            endAsync = false,
            pctStartMessageEnabled = 0f,
            sessionComponents = setOf("test"),
            fullSessionEvents = setOf("test2")
        ),
        maxSessionProperties = 57,
    )

    @Suppress("DEPRECATION")
    @Test
    fun testDefaults() {
        with(fakeSessionBehavior()) {
            assertNull(getMaxSessionSecondsAllowed())
            assertFalse(isAsyncEndEnabled())
            assertFalse(isSessionErrorLogStrictModeEnabled())
            assertEquals(emptySet<String>(), getFullSessionEvents())
            assertNull(getSessionComponents())
            assertFalse(isGatingFeatureEnabled())
            assertFalse(isSessionControlEnabled())
            assertTrue(isStartMessageEnabled())
            assertEquals(10, getMaxSessionProperties())
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testLocalOnly() {
        with(fakeSessionBehavior(localCfg = { local })) {
            assertEquals(120, getMaxSessionSecondsAllowed())
            assertTrue(isAsyncEndEnabled())
            assertTrue(isSessionErrorLogStrictModeEnabled())
            assertEquals(setOf("breadcrumbs"), getSessionComponents())
            assertEquals(setOf("crash"), getFullSessionEvents())
            assertTrue(isGatingFeatureEnabled())
            assertTrue(isStartMessageEnabled())
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun testRemoteAndLocal() {
        with(fakeSessionBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(isAsyncEndEnabled())
            assertTrue(isGatingFeatureEnabled())
            assertTrue(isSessionControlEnabled())
            assertEquals(setOf("test"), getSessionComponents())
            assertEquals(setOf("test2"), getFullSessionEvents())
            assertEquals(57, getMaxSessionProperties())
            assertFalse(isStartMessageEnabled())
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
