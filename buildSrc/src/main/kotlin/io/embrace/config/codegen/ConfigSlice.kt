package io.embrace.config.codegen

import com.squareup.kotlinpoet.ParameterSpec
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException

/**
 * A resolved config. All configs are defined in one YAML file, grouped by config and then by feature. Each feature
 * becomes a property, resolved from its remote/local/default values.
 *
 * ```yaml
 * FooConfig:                          # resolved by resolveFoo()
 *   foo_enabled:
 *     property: fooEnabled
 *     default: false
 *     local:
 *       type: Boolean                 # Boolean, Int, Long or String
 *       sdk: enabledFeatures.isFooEnabled()   # read from InstrumentedConfig
 *     remote:
 *       type: Pct                     # rollout percentage, resolved to a Boolean
 *       sdk: pctFooEnabled            # read from RemoteConfig
 * ```
 *
 * Int and Long features may set `min` and `max` together, and String features `max_length`.
 * Local or remote values outside them are ignored.
 */
class ConfigSlice(
    val name: String,
    val fields: List<ConfigField>,
) {

    val resolver: String = "resolve" + name.removeSuffix("Config")

    val property: String = name.removeSuffix("Config").replaceFirstChar(Char::lowercaseChar)

    val doc: String =
        "Resolved " + name.removeSuffix("Config").split(Regex("(?=[A-Z])")).joinToString(" ") { it.lowercase() }.trim() + " config."

    val resolverParams: List<ParameterSpec> = listOfNotNull(
        ParameterSpec("local", ConfigClassNames.INSTRUMENTED_CONFIG).takeIf { fields.any { it.local != null } },
        ParameterSpec("remote", ConfigClassNames.REMOTE_CONFIG.copy(nullable = true)).takeIf { fields.any { it.remote != null } },
        ParameterSpec("bucket", ConfigClassNames.LAZY_FLOAT).takeIf { fields.any { it.remote?.type == ConfigType.PCT } },
    )

    companion object {
        fun fromYaml(source: String): List<ConfigSlice> {
            val yaml = try {
                Yaml(SafeConstructor(LoaderOptions().apply { isAllowDuplicateKeys = false })).load<Any>(source).asMap()
            } catch (exc: YAMLException) {
                throw IllegalArgumentException(exc.message, exc)
            }
            return yaml.map { (name, features) ->
                val fields = within(name) {
                    features.asMap()
                }.map { (feature, value) ->
                    within("$name.$feature") {
                        parseFeature(value.asMap())
                    }
                }
                ConfigSlice(name, fields)
            }
        }

        private fun parseFeature(feature: Map<String, Any>): ConfigField {
            feature.checkKeys("property", "default", "local?", "remote?", "min?", "max?", "max_length?")
            val local = feature["local"]?.let { within("local") { parseProperty(it.asMap()) } }
            val remote = feature["remote"]?.let { within("remote") { parseProperty(it.asMap()) } }
            val type = local?.type ?: remote?.type?.resolvedType ?: throw IllegalArgumentException("needs a local or remote section")
            require(local?.type != ConfigType.PCT) { "Pct is only valid for remote" }
            require(remote == null || remote.type.resolvedType == type) { "remote type does not match local" }
            val default = feature.string("default").also { type.literal(it) }
            val range = parseRange(feature, type)
            require(range == null || range.contains(default)) { "default '$default' is out of range" }
            return ConfigField(feature.string("property"), type, default, local, remote, range)
        }

        private fun parseRange(feature: Map<String, Any>, type: ConfigType): ConfigRange? {
            val bounds = if ("min" in feature || "max" in feature) {
                val limits = when (type) {
                    ConfigType.INT -> Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                    ConfigType.LONG -> Long.MIN_VALUE..Long.MAX_VALUE
                    else -> throw IllegalArgumentException("min and max are only valid for Int or Long")
                }
                val min = feature.bound("min", type, limits)
                val max = feature.bound("max", type, limits)
                require(min < max) { "min must be less than max" }
                min..max
            } else {
                null
            }
            val maxLength = feature["max_length"]?.let {
                require(type == ConfigType.STRING) { "max_length is only valid for String" }
                feature.string("max_length").toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalArgumentException("'max_length' must be a positive Int")
            }
            return if (bounds == null && maxLength == null) null else ConfigRange(bounds, maxLength)
        }

        private fun Map<String, Any>.bound(key: String, type: ConfigType, limits: LongRange): Long =
            string(key).toLongOrNull()?.takeIf { it > limits.first && it < limits.last }
                ?: throw IllegalArgumentException("'$key' is not a valid ${type.yamlName}")

        private fun parseProperty(yaml: Map<String, Any>): ConfigProperty {
            yaml.checkKeys("type", "sdk")
            return ConfigProperty(ConfigType.fromYaml(yaml.string("type")), yaml.string("sdk"))
        }

        private fun Map<String, Any>.checkKeys(vararg keys: String) {
            keys.filterNot { it.endsWith("?") }.firstOrNull { it !in this }?.let { throw IllegalArgumentException("missing '$it'") }
            (this.keys - keys.map { it.removeSuffix("?") }.toSet()).firstOrNull()?.let { throw IllegalArgumentException("unknown '$it'") }
        }

        private fun Map<String, Any>.string(key: String): String = when (val value = get(key)) {
            null -> throw IllegalArgumentException("missing '$key'")
            is Map<*, *>, is List<*> -> throw IllegalArgumentException("'$key' must be a scalar")
            else -> value.toString()
        }

        @Suppress("UNCHECKED_CAST")
        private fun Any?.asMap(): Map<String, Any> = this as? Map<String, Any> ?: throw IllegalArgumentException("expected a map")

        private fun <T> within(context: String, block: () -> T): T = try {
            block()
        } catch (exc: IllegalArgumentException) {
            throw IllegalArgumentException("$context: ${exc.message}", exc)
        }
    }
}
