package io.embrace.android.embracesdk.internal.config.resolved

import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import java.lang.reflect.Proxy

/**
 * Returns [InstrumentedConfigImpl] with the member at [sdk] (as written in config.yaml, e.g.
 * `enabledFeatures.isFooEnabled()`) returning [value]. Used by the generated config tests.
 */
internal fun overrideLocal(sdk: String, value: Any): InstrumentedConfig =
    override(InstrumentedConfig::class.java, InstrumentedConfigImpl, sdk.split('.').map(::jvmName), value)
        as InstrumentedConfig

private fun override(type: Class<*>, delegate: Any, path: List<String>, value: Any): Any {
    require(type.methods.any { it.name == path.first() }) { "${type.simpleName} has no ${path.first()}()" }
    return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
        when {
            method.name != path.first() -> method.invoke(delegate, *args.orEmpty())
            path.size == 1 -> value
            else -> override(method.returnType, method.invoke(delegate), path.drop(1), value)
        }
    }
}

private fun jvmName(member: String): String {
    val name = member.removeSuffix("?").removeSuffix("()")
    return when {
        name != member.removeSuffix("?") -> name
        name.matches(Regex("is[A-Z].*")) -> name
        else -> "get" + name.replaceFirstChar(Char::uppercaseChar)
    }
}
