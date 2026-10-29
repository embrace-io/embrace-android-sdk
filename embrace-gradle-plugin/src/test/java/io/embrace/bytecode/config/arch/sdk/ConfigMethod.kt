package io.embrace.bytecode.config.arch.sdk

data class ConfigMethod(
    val name: String,
    val descriptor: String,
    val result: Any? = null,
)
