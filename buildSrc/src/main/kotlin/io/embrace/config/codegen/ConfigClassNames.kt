package io.embrace.config.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName

internal object ConfigClassNames {
    private const val CONFIG = "io.embrace.android.embracesdk.internal.config"

    val INSTRUMENTED_CONFIG = ClassName("$CONFIG.instrumented.schema", "InstrumentedConfig")
    val REMOTE_CONFIG = ClassName("$CONFIG.remote", "RemoteConfig")
    val LAZY_FLOAT = Lazy::class.asClassName().parameterizedBy(FLOAT)
}
