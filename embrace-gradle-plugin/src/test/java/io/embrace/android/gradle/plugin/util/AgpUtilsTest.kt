package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.agp.AgpUtils
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgpUtilsTest {

    @Test
    fun `desugaring should not be required for min sdk level greater than 25`() {
        assertFalse(AgpUtils.isDesugaringRequired(26))
    }

    @Test
    fun `desugaring should be required for min sdk level less than 26`() {
        assertTrue(AgpUtils.isDesugaringRequired(25))
    }

    @Test
    fun `verify task is dexguard`() {
        val tasks = ProjectBuilder.builder().build().tasks

        assertTrue(AgpUtils.isDexguard(tasks.register("dexguardApkDebug")))
        assertFalse(AgpUtils.isDexguard(tasks.register("transformClassesAndResourcesWithProguardForDebug")))
    }
}
