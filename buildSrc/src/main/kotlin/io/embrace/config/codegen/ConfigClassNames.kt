package io.embrace.config.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName

internal object ConfigClassNames {
    private const val CONFIG = "io.embrace.android.embracesdk.internal.config"

    val INSTRUMENTED_CONFIG = ClassName("$CONFIG.instrumented.schema", "InstrumentedConfig")
    val INSTRUMENTED_CONFIG_IMPL = ClassName("$CONFIG.instrumented", "InstrumentedConfigImpl")
    val REMOTE_CONFIG = ClassName("$CONFIG.remote", "RemoteConfig")
    val LAZY_FLOAT = Lazy::class.asClassName().parameterizedBy(FLOAT)
    val TEST = ClassName("org.junit", "Test")
    val ASSERT_EQUALS = MemberName("org.junit.Assert", "assertEquals")
}
