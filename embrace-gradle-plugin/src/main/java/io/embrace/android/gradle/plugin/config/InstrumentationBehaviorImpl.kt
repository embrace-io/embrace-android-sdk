package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.api.EmbraceExtension

class InstrumentationBehaviorImpl(
    private val embrace: EmbraceExtension,
) : InstrumentationBehavior {

    private val instrumentation by lazy {
        embrace.bytecodeInstrumentation
    }

    private val enabled by lazy {
        instrumentation.enabled.get()
    }

    override val okHttpEnabled: Boolean by lazy {
        enabled && (instrumentation.okhttpEnabled.orNull ?: true)
    }

    override val onClickEnabled: Boolean by lazy {
        enabled && (instrumentation.onClickEnabled.orNull ?: true)
    }

    override val onLongClickEnabled: Boolean by lazy {
        enabled && (instrumentation.onLongClickEnabled.orNull ?: true)
    }

    override val webviewEnabled: Boolean by lazy {
        enabled && (instrumentation.webviewOnPageStartedEnabled.orNull ?: true)
    }

    override val autoSdkInitializationEnabled: Boolean by lazy {
        enabled && (instrumentation.autoSdkInitializationEnabled.orNull ?: false)
    }

    override val applicationInitTimingEnabled: Boolean by lazy {
        enabled && (instrumentation.applicationInitTimingEnabled.orNull ?: true)
    }

    override val fcmPushNotificationsEnabled: Boolean by lazy {
        enabled && (instrumentation.firebasePushNotificationsEnabled.orNull ?: false)
    }

    override val ignoredClasses: List<String> by lazy {
        instrumentation.classIgnorePatterns.get()
    }
}
