@file:Suppress("UPPER_BOUND_VIOLATED_BASED_ON_JAVA_ANNOTATIONS")

package io.embrace.gradle.plugin.instrumentation

import io.mockk.every
import io.mockk.mockk
import org.gradle.api.provider.Property

@Suppress("UNCHECKED_CAST")
fun <T> fakeProperty(value: T): Property<T> {
    return mockk(relaxed = true) {
        every { get() } returns value as (T & Any)
    }
}
