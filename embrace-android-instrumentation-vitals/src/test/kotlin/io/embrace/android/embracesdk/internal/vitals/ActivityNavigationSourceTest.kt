package io.embrace.android.embracesdk.internal.vitals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ActivityNavigationSourceTest {

    private val callbacks = FakeFocalInteractionCallbacks()
    private val source = ActivityNavigationSource(callbacks)

    @Test
    fun `the cold-start Activity is skipped`() {
        source.onActivityCreated("Home", recreated = false, eventTime = 0L)

        assertTrue("cold start is measured by the startup vital, not screen-load", callbacks.navigationStarts.isEmpty())
    }

    @Test
    fun `subsequent Activity creations report a navigation start`() {
        source.onActivityCreated("Home", recreated = false, eventTime = 0L) // cold start, skipped
        source.onActivityCreated("Detail", recreated = false, eventTime = 10L)
        source.onActivityCreated("Settings", recreated = false, eventTime = 20L)

        assertEquals(listOf("Detail" to 10L, "Settings" to 20L), callbacks.navigationStarts)
    }

    @Test
    fun `an Activity resume reports a navigation end`() {
        source.onActivityResumed("Detail", eventTime = 100L)

        assertEquals(listOf("Detail" to 100L), callbacks.navigationEnds)
    }

    @Test
    fun `a recreated Activity does not report a navigation start`() {
        source.onActivityCreated("Home", recreated = false, eventTime = 0L) // cold start, skipped
        source.onActivityCreated("Detail", recreated = false, eventTime = 10L) // reported

        // The same screen rebuilt (rotation, process-death restore) is not a forward navigation.
        source.onActivityCreated("Detail", recreated = true, eventTime = 20L)
        source.onActivityCreated("Settings", recreated = false, eventTime = 30L) // genuine in-app navigation, reported

        assertEquals(listOf("Detail" to 10L, "Settings" to 30L), callbacks.navigationStarts)
    }

    @Test
    fun `a cold start restored from saved state is skipped without eating the next navigation`() {
        source.onActivityCreated("Home", recreated = true, eventTime = 0L) // cold start from process-death restore, skipped
        source.onActivityCreated("Detail", recreated = false, eventTime = 10L) // first genuine navigation, reported

        assertEquals(listOf("Detail" to 10L), callbacks.navigationStarts)
    }
}
