@file:Suppress("DEPRECATION")

package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.api.EmbraceExtension
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
    private lateinit var embrace: EmbraceExtension
    private lateinit var behavior: InstrumentationBehavior

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().build()
        extension = project.extensions.create("swazzler", SwazzlerExtension::class.java)
        embrace = project.extensions.create("embrace", EmbraceExtension::class.java)
        behavior = InstrumentationBehaviorImpl(extension, embrace)
    }

    @Test
    fun `okHttpEnabled default`() {
        assertTrue(behavior.okHttpEnabled)
    }

    @Test
    fun `okHttpEnabled disabled via swazzler`() {
        extension.instrumentOkHttp.set(false)
        assertFalse(behavior.okHttpEnabled)
    }

    @Test
    fun `okHttpEnabled disabled via embrace`() {
        embrace.bytecodeInstrumentation.okhttpEnabled.set(false)
        assertFalse(behavior.okHttpEnabled)
    }

    @Test
    fun `okHttpEnabled disabled via global`() {
        embrace.bytecodeInstrumentation.enabled.set(false)
        assertFalse(behavior.okHttpEnabled)
    }

    @Test
    fun `onClickEnabled default`() {
        assertTrue(behavior.onClickEnabled)
    }

    @Test
    fun `onClickEnabled via swazzler`() {
        extension.instrumentOnClick.set(false)
        assertFalse(behavior.onClickEnabled)
    }

    @Test
    fun `onClickEnabled via embrace`() {
        embrace.bytecodeInstrumentation.onClickEnabled.set(false)
        assertFalse(behavior.onClickEnabled)
    }

    @Test
    fun `onClickEnabled disabled via global`() {
        embrace.bytecodeInstrumentation.enabled.set(false)
        assertFalse(behavior.onClickEnabled)
    }

    @Test
    fun `onLongClickEnabled default`() {
        assertTrue(behavior.onLongClickEnabled)
    }

    @Test
    fun `onLongClickEnabled via swazzler`() {
        extension.instrumentOnLongClick.set(false)
        assertFalse(behavior.onLongClickEnabled)
    }

    @Test
    fun `onLongClickEnabled via embrace`() {
        embrace.bytecodeInstrumentation.onLongClickEnabled.set(false)
        assertFalse(behavior.onLongClickEnabled)
    }

    @Test
    fun `onLongClickEnabled disabled via global`() {
        embrace.bytecodeInstrumentation.enabled.set(false)
        assertFalse(behavior.onLongClickEnabled)
    }

    @Test
    fun `webviewEnabled default`() {
        assertTrue(behavior.webviewEnabled)
    }

    @Test
    fun `webviewEnabled via swazzler`() {
        extension.instrumentWebview.set(false)
        assertFalse(behavior.webviewEnabled)
    }

    @Test
    fun `webviewEnabled via embrace`() {
        embrace.bytecodeInstrumentation.webviewOnPageStartedEnabled.set(false)
        assertFalse(behavior.webviewEnabled)
    }

    @Test
    fun `webviewEnabled disabled via global`() {
        embrace.bytecodeInstrumentation.enabled.set(false)
        assertFalse(behavior.webviewEnabled)
    }

    @Test
    fun `fcmPushNotificationsEnabled default`() {
        assertFalse(behavior.fcmPushNotificationsEnabled)
    }

    @Test
    fun `fcmPushNotificationsEnabled via swazzler`() {
        extension.instrumentFirebaseMessaging.set(true)
        assertTrue(behavior.fcmPushNotificationsEnabled)
    }

    @Test
    fun `fcmPushNotificationsEnabled via embrace`() {
        embrace.bytecodeInstrumentation.firebasePushNotificationsEnabled.set(true)
        assertTrue(behavior.fcmPushNotificationsEnabled)
    }

    @Test
    fun `fcmPushNotificationsEnabled disabled via global`() {
        embrace.bytecodeInstrumentation.firebasePushNotificationsEnabled.set(true)
        embrace.bytecodeInstrumentation.enabled.set(false)
        assertFalse(behavior.fcmPushNotificationsEnabled)
    }

    @Test
    fun `ignoredClasses default`() {
        assertTrue(behavior.ignoredClasses.isEmpty())
    }

    @Test
    fun `ignoredClasses override via swazzler`() {
        val values = listOf("foo", "bar")
        extension.classSkipList.set(values)
        assertEquals(values, behavior.ignoredClasses)
    }

    @Test
    fun `ignoredClasses override via embrace`() {
        val values = listOf("foo", "bar")
        embrace.bytecodeInstrumentation.classIgnorePatterns.set(values)
        assertEquals(values, behavior.ignoredClasses)
    }
}
