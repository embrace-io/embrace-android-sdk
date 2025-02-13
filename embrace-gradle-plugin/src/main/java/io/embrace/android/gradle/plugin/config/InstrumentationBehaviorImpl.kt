package io.embrace.android.gradle.plugin.config

import com.android.build.api.instrumentation.InstrumentationScope
import io.embrace.android.gradle.swazzler.plugin.extension.SwazzlerExtension
import org.gradle.api.Project

class InstrumentationBehaviorImpl(
    private val project: Project,
    private val extension: SwazzlerExtension,
) : InstrumentationBehavior {

    override val invalidateBytecode: Boolean by lazy {
        extension.forceIncrementalOverwrite.get()
    }

    override val scope: InstrumentationScope by lazy {
        val prop = project.getProperty(EMBRACE_INSTRUMENTATION_SCOPE)
            ?: return@lazy InstrumentationScope.ALL
        try {
            InstrumentationScope.valueOf(prop.uppercase())
        } catch (e: IllegalArgumentException) {
            InstrumentationScope.ALL
        }
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
