package io.embrace.android.embracesdk.internal.arch.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class StartupClassifierImplTest {

    private lateinit var classifier: StartupClassifierImpl

    @Before
    fun setUp() {
        classifier = StartupClassifierImpl()
    }

    @Test
    fun `unresolved before any signal`() {
        assertNull(classifier.startupType())
    }

    @Test
    fun `cold when activity init gap is at the threshold`() {
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = INIT_END_MS,
            postAppInitTimeMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS,
        )
        assertEquals(StartupType.COLD, classifier.startupType())
    }

    @Test
    fun `warm when activity init gap exceeds the threshold`() {
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = INIT_END_MS,
            postAppInitTimeMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS + 1,
        )
        assertEquals(StartupType.WARM, classifier.startupType())
    }

    @Test
    fun `gap is measured from the sdk init end when the application init end is unknown`() {
        classifier.evaluateStartup(
            sdkInitEndMs = INIT_END_MS,
            appInitEndMs = null,
            postAppInitTimeMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS,
        )
        assertEquals(StartupType.COLD, classifier.startupType())
    }

    @Test
    fun `gap is measured from the application init end when both init ends are known`() {
        classifier.evaluateStartup(
            sdkInitEndMs = INIT_END_MS,
            appInitEndMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS,
            postAppInitTimeMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS + 1,
        )
        assertEquals(StartupType.COLD, classifier.startupType())
    }

    @Test
    fun `unresolved when no app or SDK init end time is known`() {
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = null,
            postAppInitTimeMs = INIT_END_MS,
        )
        assertNull(classifier.startupType())
    }

    @Test
    fun `startup type is immutable once resolved`() {
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = INIT_END_MS,
            postAppInitTimeMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS,
        )
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = INIT_END_MS,
            postAppInitTimeMs = INIT_END_MS + MAX_COLD_STARTUP_INIT_GAP_MS + 1,
        )
        assertEquals(StartupType.COLD, classifier.startupType())
    }

    @Test
    fun `assumption of background startup is immutable once set`() {
        classifier.assumeBackgroundStartup()
        assertEquals(StartupType.BACKGROUND, classifier.startupType())
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = INIT_END_MS,
            postAppInitTimeMs = INIT_END_MS + 1,
        )
        assertEquals(StartupType.BACKGROUND, classifier.startupType())
    }

    @Test
    fun `cannot set startup to background if already set`() {
        classifier.evaluateStartup(
            sdkInitEndMs = null,
            appInitEndMs = INIT_END_MS,
            postAppInitTimeMs = INIT_END_MS + 1,
        )
        classifier.assumeBackgroundStartup()
        assertEquals(StartupType.COLD, classifier.startupType())
    }

    private companion object {
        const val INIT_END_MS = 100_000L
    }
}
