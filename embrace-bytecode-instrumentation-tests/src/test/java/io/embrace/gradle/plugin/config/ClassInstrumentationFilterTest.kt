package io.embrace.gradle.plugin.config

import io.embrace.android.gradle.plugin.instrumentation.ClassInstrumentationFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassInstrumentationFilterTest {

    @Test
    fun `it should not skip if there are no skip pattern for jar`() {
        val filter = ClassInstrumentationFilter(emptyList())
        assertFalse(filter.shouldSkip("whatever"))
    }

    @Test
    fun `it should not skip if there are no skip pattern for class`() {
        val filter = ClassInstrumentationFilter(emptyList())
        assertFalse(filter.shouldSkip("whatever"))
    }

    @Test
    fun `it should not skip if name does not match any pattern for jar`() {
        val filter = ClassInstrumentationFilter(listOf("jarToSkip"))
        assertFalse(filter.shouldSkip("jarNotToSkip"))
    }

    @Test
    fun `it should not skip if name does not match any pattern for class`() {
        val filter = ClassInstrumentationFilter(listOf("com.myclass.toSkip"))
        assertFalse(filter.shouldSkip("com.myclass.notToSkip"))
    }

    @Test
    fun `it should skip if name matches a pattern for jar`() {
        val filter = ClassInstrumentationFilter(listOf("com.myclass.toSkip"))
        assertTrue(filter.shouldSkip("com.myclass.toSkip"))
    }

    @Test
    fun `it should skip if name matches a pattern for class`() {
        val filter = ClassInstrumentationFilter(listOf("com.myclass.toSkip"))
        assertTrue(filter.shouldSkip("com.myclass.toSkip"))
    }

    @Test
    fun skipClassWhenRegexAffectsClassName() {
        // given
        val filter = ClassInstrumentationFilter(listOf("Hel.*"))

        // when
        val shouldSkipInstrumentation = filter.shouldSkip("HelloFragment.class")

        // then
        assertTrue(shouldSkipInstrumentation)
    }

    @Test
    fun skipClassWhenRegexAffectsJarName() {
        // given
        val filter = ClassInstrumentationFilter(listOf("Hel.*"))

        // when
        val shouldSkipInstrumentation =
            filter.shouldSkip("Hello-Library.jar")

        // then
        assertTrue(shouldSkipInstrumentation)
    }

    @Test
    fun skipMultipleJars() {
        // given
        val filter = ClassInstrumentationFilter(listOf("Hello", "Go.*bye", "Hey"))

        // when
        val skipContainingWord = filter.shouldSkip("Hello-Library.jar")
        val skipRegex = filter.shouldSkip("Goodbye.jar")
        val doesNotSkip = filter.shouldSkip("Another.jar")

        // then
        assertTrue(skipContainingWord)
        assertTrue(skipRegex)
        assertFalse(doesNotSkip)
    }

    @Test
    fun skipMultipleClasses() {
        // given
        val filter = ClassInstrumentationFilter(listOf("Hello", "Go.*bye", "Hey"))

        // when
        val skipContainingWord = filter.shouldSkip("HelloFragment.class")
        val skipRegex = filter.shouldSkip("GoodbyeActivity.class")
        val doesNotSkip = filter.shouldSkip("AnotherClass.class")

        // then
        assertTrue(skipContainingWord)
        assertTrue(skipRegex)
        assertFalse(doesNotSkip)
    }
}
