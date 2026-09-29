package io.embrace.gradle.configmodel

/**
 * A JSON model: the remote config or embrace-config.json. The first class is the root.
 */
data class ConfigModelSpec(
    val classes: List<ConfigClassSpec>,
) {
    val root: ConfigClassSpec get() = classes.first()

    /**
     * The fields a dotted JSON path passes through from the root, e.g. `ui.breadcrumbs`, or null if there is no
     * such path.
     */
    fun resolve(path: String): List<ConfigFieldSpec>? {
        var cls = root
        val segments = path.split('.')
        return segments.mapIndexed { index, key ->
            val field = cls.fields.find { it.key == key } ?: return null
            if (index < segments.lastIndex) {
                cls = classes.find { it.name == field.type } ?: return null
            }
            field
        }
    }
}
