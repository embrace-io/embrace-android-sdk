package io.embrace.android.gradle.plugin.config

import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension

class InstrumentationBehaviorImpl(
    private val extension: SwazzlerExtension,
) : InstrumentationBehavior {

    override val invalidateBytecode: Boolean by lazy {
        extension.forceIncrementalOverwrite.get()
    }

    override val okHttpEnabled: Boolean by lazy {
        extension.instrumentOkHttp.get()
    }

    override val onClickEnabled: Boolean by lazy {
        extension.instrumentOnClick.get()
    }

    override val onLongClickEnabled: Boolean by lazy {
        extension.instrumentOnLongClick.get()
    }

    override val webviewEnabled: Boolean by lazy {
        extension.instrumentWebview.get()
    }

    override val fcmPushNotificationsEnabled: Boolean by lazy {
        extension.instrumentFirebaseMessaging.get()
    }

    override val ignoredClasses: List<String> by lazy {
        extension.classSkipList.get()
    }
}
