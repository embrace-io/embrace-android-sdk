package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.gradle.GradleVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class GradleVersionTest {

    @Test
    fun testComparator() {
        assertEquals(0, GradleVersion.MIN_VERSION.compareTo(GradleVersion.MIN_VERSION))
        assertEquals(1, GradleVersion.CURRENT.compareTo(GradleVersion.MIN_VERSION))
        assertEquals(-1, GradleVersion.MIN_VERSION.compareTo(GradleVersion.CURRENT))
    }
}
