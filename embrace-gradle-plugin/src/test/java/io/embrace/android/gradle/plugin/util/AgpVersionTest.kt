package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.agp.AgpVersion.AGP_8_0_0
import io.embrace.android.gradle.plugin.agp.AgpVersion.AGP_8_3_0
import org.junit.Assert.assertEquals
import org.junit.Test

class AgpVersionTest {

    @Test
    fun testComparator() {
        assertEquals(0, AGP_8_0_0.compareTo(AGP_8_0_0))
        assertEquals(1, AGP_8_3_0.compareTo(AGP_8_0_0))
        assertEquals(-1, AGP_8_0_0.compareTo(AGP_8_3_0))
    }
}
