@file:Suppress("DEPRECATION")

package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension

class InstrumentationBehaviorImpl(
    private val extension: SwazzlerExtension,
    private val embrace: EmbraceExtension,
) : InstrumentationBehavior {

    private val instrumentation by lazy {
        embrace.bytecodeInstrumentation
    }

    private val enabled by lazy {
        instrumentation.enabled.get()
    }

    override val okHttpEnabled: Boolean by lazy {
        enabled && (instrumentation.okhttpEnabled.orNull ?: extension.instrumentOkHttp.orNull ?: true)
    }

    override val onClickEnabled: Boolean by lazy {
        enabled && (instrumentation.onClickEnabled.orNull ?: extension.instrumentOnClick.orNull ?: true)
    }

    override val onLongClickEnabled: Boolean by lazy {
        enabled && (instrumentation.onLongClickEnabled.orNull ?: extension.instrumentOnLongClick.orNull ?: true)
    }

    override val webviewEnabled: Boolean by lazy {
        enabled && (instrumentation.webviewOnPageStartedEnabled.orNull ?: extension.instrumentWebview.orNull ?: true)
    }

    override val autoSdkInitializationEnabled: Boolean by lazy {
        enabled && (instrumentation.autoSdkInitializationEnabled.orNull ?: false)
    }

    override val applicationInitTimeStartEnabled: Boolean by lazy {
        enabled && (instrumentation.applicationInitTimeStartEnabled.orNull ?: true)
    }

    override val applicationInitTimeEndEnabled: Boolean by lazy {
        enabled && (instrumentation.applicationInitTimeEndEnabled.orNull ?: true)
    }

    override val fcmPushNotificationsEnabled: Boolean by lazy {
        enabled && (instrumentation.firebasePushNotificationsEnabled.orNull ?: extension.instrumentFirebaseMessaging.orNull ?: false)
    }

    override val ignoredClasses: List<String> by lazy {
        instrumentation.classIgnorePatterns.get().plus(extension.classSkipList.get())
    }
}
