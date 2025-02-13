package io.embrace.android.gradle.plugin.config

import com.android.build.api.instrumentation.InstrumentationScope
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InstrumentationBehaviorImplTest {

    private lateinit var project: Project
    private lateinit var extension: SwazzlerExtension
    private lateinit var behavior: InstrumentationBehavior

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        extension = project.extensions.create("swazzler", SwazzlerExtension::class.java)
        behavior = InstrumentationBehaviorImpl(project, extension)
    }

    @Test
    fun `instrumentation scope default`() {
        assertEquals(InstrumentationScope.ALL, behavior.scope)
    }

    @Test
    fun `instrumentation scope valid`() {
        addGradleProperty(EMBRACE_INSTRUMENTATION_SCOPE, "project")
        assertEquals(InstrumentationScope.PROJECT, behavior.scope)
    }

    @Test
    fun `instrumentation scope invalid`() {
        addGradleProperty(EMBRACE_INSTRUMENTATION_SCOPE, "foo")
        assertEquals(InstrumentationScope.ALL, behavior.scope)
    }

    @Test
    fun `invalidateBytecode default`() {
        assertFalse(behavior.invalidateBytecode)
    }

    @Test
    fun `invalidateBytecode true`() {
        extension.forceIncrementalOverwrite.set(true)
        assertTrue(behavior.invalidateBytecode)
    }

    @Test
    fun `okHttpEnabled default`() {
        assertTrue(behavior.okHttpEnabled)
    }

    @Test
    fun `okHttpEnabled false`() {
        extension.instrumentOkHttp.set(false)
        assertFalse(behavior.okHttpEnabled)
    }

    @Test
    fun `onClickEnabled default`() {
        assertTrue(behavior.onClickEnabled)
    }

    @Test
    fun `onClickEnabled false`() {
        extension.instrumentOnClick.set(false)
        assertFalse(behavior.onClickEnabled)
    }

    @Test
    fun `onLongClickEnabled default`() {
        assertTrue(behavior.onLongClickEnabled)
    }

    @Test
    fun `onLongClickEnabled false`() {
        extension.instrumentOnLongClick.set(false)
        assertFalse(behavior.onLongClickEnabled)
    }

    @Test
    fun `webviewEnabled default`() {
        assertTrue(behavior.webviewEnabled)
    }

    @Test
    fun `webviewEnabled false`() {
        extension.instrumentWebview.set(false)
        assertFalse(behavior.webviewEnabled)
    }

    @Test
    fun `fcmPushNotificationsEnabled default`() {
        assertFalse(behavior.fcmPushNotificationsEnabled)
    }

    @Test
    fun `fcmPushNotificationsEnabled false`() {
        extension.instrumentFirebaseMessaging.set(true)
        assertTrue(behavior.fcmPushNotificationsEnabled)
    }

    @Test
    fun `ignoredClasses default`() {
        assertTrue(behavior.ignoredClasses.isEmpty())
    }

    @Test
    fun `ignoredClasses override`() {
        val values = listOf("foo", "bar")
        extension.classSkipList.set(values)
        assertEquals(values, behavior.ignoredClasses)
    }

    private fun addGradleProperty(key: String, value: String) {
        project.extensions.extraProperties[key] = value
    }
}
