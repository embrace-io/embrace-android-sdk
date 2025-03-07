package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.plugin.api.EmbraceExtension
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension

@Suppress("DEPRECATION")
class InstrumentationBehaviorImpl(
    private val extension: SwazzlerExtension,
    private val embrace: EmbraceExtension,
) : InstrumentationBehavior {

    override val invalidateBytecode: Boolean by lazy {
        extension.forceIncrementalOverwrite.get()
    }

    private val instrumentation by lazy {
        embrace.bytecodeInstrumentation
    }

    override val okHttpEnabled: Boolean by lazy {
        instrumentation.okhttpEnabled.orNull ?: extension.instrumentOkHttp.orNull ?: true
    }

    override val onClickEnabled: Boolean by lazy {
        instrumentation.onClickEnabled.orNull ?: extension.instrumentOnClick.orNull ?: true
    }

    override val onLongClickEnabled: Boolean by lazy {
        instrumentation.onLongClickEnabled.orNull ?: extension.instrumentOnLongClick.orNull ?: true
    }

    override val webviewEnabled: Boolean by lazy {
        instrumentation.webviewOnPageStartedEnabled.orNull ?: extension.instrumentWebview.orNull ?: true
    }

    override val fcmPushNotificationsEnabled: Boolean by lazy {
        instrumentation.firebasePushNotificationsEnabled.orNull ?: extension.instrumentFirebaseMessaging.orNull ?: false
    }

    override val ignoredClasses: List<String> by lazy {
        instrumentation.classIgnorePatterns.get().plus(extension.classSkipList.get())
    }
}
