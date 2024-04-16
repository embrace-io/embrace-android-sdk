package io.embrace.android.embracesdk.payload

import io.embrace.android.embracesdk.assertJsonMatchesGoldenFile
import io.embrace.android.embracesdk.deserializeEmptyJsonString
import io.embrace.android.embracesdk.deserializeJsonFromResource
import org.junit.Assert.assertNotNull
import org.junit.Test

internal class BreadcrumbsTest {

    private val info = Breadcrumbs(
        rnActionBreadcrumbs = listOf(
            RnActionBreadcrumb(
                "RnAction",
                1600000000,
                1600005000,
                emptyMap(),
                0,
                "output"
            )
        )
    )

    @Test
    fun testSerialization() {
        assertJsonMatchesGoldenFile("breadcrumbs_expected.json", info)
    }

    @Test
    fun testDeserialization() {
        val obj = deserializeJsonFromResource<Breadcrumbs>("breadcrumbs_expected.json")
        assertNotNull(obj)
        assertNotNull(obj.rnActionBreadcrumbs?.single())
    }

    @Test
    fun testEmptyObject() {
        val obj = deserializeEmptyJsonString<Breadcrumbs>()
        assertNotNull(obj)
    }
}
