package io.embrace.android.gradle.plugin.util

import com.android.build.api.AndroidPluginVersion
import io.embrace.android.gradle.plugin.agp.AgpVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class AgpVersionTest {

    @Test
    fun testComparator() {
        assertEquals(0, AgpVersion.MIN_VERSION.compareTo(AgpVersion.MIN_VERSION))
        val newer = AgpVersion.CURRENT(AndroidPluginVersion(9, 0, 0))
        assertEquals(1, newer.compareTo(AgpVersion.MIN_VERSION))
        assertEquals(-1, AgpVersion.MIN_VERSION.compareTo(newer))
    }
}
