package io.embrace.android.gradle.plugin.config

interface InstrumentationBehavior {

    /**
     * Whether OkHttp should be auto-instrumented
     */
    val okHttpEnabled: Boolean

    /**
     * Whether View clicks should be auto-instrumented
     */
    val onClickEnabled: Boolean

    /**
     * Whether View long clicks should be auto-instrumented
     */
    val onLongClickEnabled: Boolean

    /**
     * Whether WebViews should be auto-instrumented
     */
    val webviewEnabled: Boolean

    /**
     * Whether FCM push notifications should be auto-instrumented
     */
    val fcmPushNotificationsEnabled: Boolean

    /**
     * A list of string regexes that are used to filter classes during bytecode instrumentation
     */
    val ignoredClasses: List<String>

    val autoStartEnabled: Boolean
}
