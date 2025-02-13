package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.gradle.GradleVersion
import org.junit.Assert.assertEquals
import org.junit.Test

class GradleVersionTest {

    @Test
    fun testCompileVersion() {
        assertEquals("8.12.1", GradleVersion.CURRENT.toString())
    }

    @Test
    fun testComparator() {
        assertEquals(0, GradleVersion.GRADLE_8_0.compareTo(GradleVersion.GRADLE_8_0))
        assertEquals(1, GradleVersion.CURRENT.compareTo(GradleVersion.GRADLE_8_0))
        assertEquals(-1, GradleVersion.GRADLE_8_0.compareTo(GradleVersion.CURRENT))
    }
}
