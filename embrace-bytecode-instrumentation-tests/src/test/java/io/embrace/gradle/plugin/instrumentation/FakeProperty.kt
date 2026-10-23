package io.embrace.gradle.plugin.instrumentation

import org.gradle.api.internal.provider.DefaultProperty
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.provider.Property

inline fun <reified T : Any> fakeProperty(value: T): Property<T> =
    DefaultProperty(PropertyHost.NO_OP, T::class.javaObjectType).value(value)
