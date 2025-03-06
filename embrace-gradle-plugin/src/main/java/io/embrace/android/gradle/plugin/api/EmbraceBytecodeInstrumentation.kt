package io.embrace.android.gradle.plugin.api

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * DSL for configuring the Embrace Gradle Plugin's bytecode instrumentation behavior.
 */
abstract class EmbraceBytecodeInstrumentation @Inject internal constructor(objectFactory: ObjectFactory) {

    /**
     * Global flag that overrides all others & decides whether Embrace should perform any bytecode instrumentation.
     * Defaults to true.
     */
    val enabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether Embrace should automatically instrument OkHttp requests. Defaults to true.
     */
    val okhttpEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether Embrace should automatically instrument android.view.View click events. Defaults to true.
     */
    val onClickEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether Embrace should automatically instrument android.view.View long click events. Defaults to true.
     */
    val onLongClickEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether Embrace should automatically instrument onPageStarted() in webviews. Defaults to true.
     */
    val webviewOnPageStartedEnabled: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * Whether Embrace should automatically instrument push notifications from Firebase. Defaults to false.
     */
    val firebasePushNotificationsEnabled: Property<Boolean> =
        objectFactory.property(Boolean::class.java)

    /**
     * A list of string patterns that are used to filter classes during bytecode instrumentation. For example, 'com.example.foo.*'
     * would avoid instrumenting any classes in the 'com.example.foo' package.
     *
     * This can be useful if you wish to avoid instrumenting certain parts of your codebase.
     *
     * Defaults to an empty list.
     */
    val classIgnorePatterns: ListProperty<String> =
        objectFactory.listProperty(String::class.java)
}
