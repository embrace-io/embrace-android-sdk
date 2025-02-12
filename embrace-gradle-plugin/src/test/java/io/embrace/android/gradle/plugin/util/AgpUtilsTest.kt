package io.embrace.android.gradle.plugin.util

import io.embrace.android.gradle.plugin.agp.AgpUtils
import io.mockk.every
import io.mockk.mockk
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgpUtilsTest {

    @Test
    fun `desugaring should not be required for min sdk level greater than 23`() {
        assertFalse(AgpUtils.isDesugaringRequired(24))
    }

    @Test
    fun `desugaring should be required for min sdk level less than 24`() {
        assertTrue(AgpUtils.isDesugaringRequired(23))
    }

    @Test
    fun `verify task is dexguard`() {
        val task = mockk<TaskProvider<Task>> {
            every { name } returns "dexguardApkDebug"
        }

        assertTrue(AgpUtils.isDexguard(task))

        every { task.name } returns "transformClassesAndResourcesWithProguardForDebug"

        assertFalse(AgpUtils.isDexguard(task))
    }
}
