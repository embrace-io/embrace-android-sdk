package io.embrace.android.gradle.plugin

import io.embrace.android.gradle.plugin.gradle.isTaskRegistered
import io.embrace.android.gradle.plugin.gradle.tryGetTaskProvider
import io.embrace.android.gradle.plugin.model.AndroidCompactedVariantData
import io.embrace.android.gradle.plugin.util.capitalizedString
import io.mockk.every
import io.mockk.mockk
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRegistrationUtilsTest {

    private val project = ProjectBuilder.builder().build()

    @Test
    fun `verify task is registered`() {
        val variantName = "the-variant"
        val taskName = "the-task"
        project.tasks.register("$taskName${variantName.capitalizedString()}")

        assertTrue(project.isTaskRegistered(taskName, variantName))
    }

    @Test
    fun `verify task is not registered`() {
        val variantName = "the-variant"
        val taskName = "the-task"

        assertFalse(project.isTaskRegistered(taskName, variantName))
    }

    @Test
    fun `verify task provider is registered`() {
        assertTrue(isTaskRegistered(mockk()))
    }

    @Test
    fun `verify task provider is not registered`() {
        assertFalse(isTaskRegistered(null))
    }

    @Test
    fun `get task provider successfully`() {
        val variantName = "the-variant"
        val variantData = mockk<AndroidCompactedVariantData> {
            every { name } returns variantName
        }
        val taskName = "the-task"
        project.tasks.register("$taskName${variantData.name.capitalizedString()}")

        assertNotNull(project.tryGetTaskProvider("$taskName${variantData.name.capitalizedString()}"))
    }

    @Test
    fun `for unknown task it should return null task provider`() {
        assertNull(project.tryGetTaskProvider("unknown-task"))
    }
}
