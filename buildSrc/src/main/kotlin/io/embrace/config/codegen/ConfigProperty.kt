package io.embrace.config.codegen

import com.squareup.kotlinpoet.ClassName

class ConfigProperty(
    val type: ConfigType,
    val sdk: String,
    val parentType: ClassName? = null,
)
