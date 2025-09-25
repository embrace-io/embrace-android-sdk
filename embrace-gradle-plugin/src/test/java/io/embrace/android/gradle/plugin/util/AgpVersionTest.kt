package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.agp.AgpVersion
import io.embrace.android.gradle.plugin.agp.AgpVersion.AGP_8_3_0
import org.junit.Assert.assertEquals
import org.junit.Test

class AgpVersionTest {

    @Test
    fun testComparator() {
        assertEquals(0, AgpVersion.MIN_VERSION.compareTo(AgpVersion.MIN_VERSION))
        assertEquals(1, AGP_8_3_0.compareTo(AgpVersion.MIN_VERSION))
        assertEquals(-1, AgpVersion.MIN_VERSION.compareTo(AGP_8_3_0))
    }
}
