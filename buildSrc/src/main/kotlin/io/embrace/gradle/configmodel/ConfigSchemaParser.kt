package io.embrace.gradle.configmodel

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException

/**
 * Parses and validates a config schema file. Options are grouped by feature, and each declares the remote and local
 * fields it reads where it first uses them; a later option names an already declared field by its path alone.
 *
 * Beyond the shape of each section, it checks that paths and types are compatible and that every default and
 * instrumented method can be generated, so that a mistake fails here with its location rather than as a compile
 * error or a silently wrong value.
 */
class ConfigSchemaParser(sourceName: String) {

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val FEATURE_KEYS = arrayOf("name", "doc", "options", "remote_fields", "local_fields", "remote_types", "local_types")
        val OPTION_KEYS = arrayOf("name", "type", "default", "doc", "remote", "local", "android")
        val ANDROID_KEYS = arrayOf("instrumented", "instrumented_type", "instrumented_default", "plugin_value", "resolver")
        val REMOTE_MODIFIERS = listOf("rollout", "clamp", "valid_range")
        val PLUGIN_PARAMS = listOf("cfg", "encodedSharedObjectFilesMap", "reactNativeBundleId", "variantOutputInfo")
        val PLUGIN_TYPES = setOf("Boolean", "Int", "Long", "String", "List<String>", "Map<String, String>")
    }

    private val reader = SpecReader(sourceName)
    private val source = sourceName

    fun parse(text: String): ConfigSchema {
        val root = try {
            Yaml(SafeConstructor(LoaderOptions().apply { isAllowDuplicateKeys = false })).load<Any?>(text)
        } catch (exc: YAMLException) {
            reader.fail(source, exc.message.orEmpty())
        }
        val top = reader.map(root, source, "android", "json_schema", "enums", "remote", "local", "features")
        val android = reader.map(top["android"], "$source: android", "packages", "instrumented")
        val enums = parseEnums(top["enums"])
        val enumsByName = enums.associateBy { it.name }
        val instrumented = parseInstrumented(android["instrumented"])
        val context = OptionContext(
            enums = enumsByName,
            remote = ModelBuilder(reader, "$source: remote", top["remote"]),
            local = ModelBuilder(reader, "$source: local", top["local"]),
            remoteFields = FieldParser(reader, local = false, enumsByName),
            localFields = FieldParser(reader, local = true, enumsByName),
            instrumented = instrumented.associateBy { it.name },
        )

        val features = reader.list(top["features"], "$source: features").map {
            reader.map(it, "$source: features", *FEATURE_KEYS)
        }
        features.forEach { parseTypes(it, context) }
        val groups = features.map { parseFeature(it, context) }
        reader.ensureUnique(groups.map { it.name }, source, "feature")
        val methods = groups.flatMap { it.options }.mapNotNull { it.instrumented }
        instrumented.forEach { cls ->
            reader.ensureUnique(methods.filter { it.className == cls.name }.map { it.name }, "$source: ${cls.name}", "method")
        }
        return ConfigSchema(
            packages = parsePackages(android["packages"]),
            enums = enums,
            remote = context.remote.build(),
            local = context.local.build(),
            instrumented = instrumented,
            groups = groups,
            jsonSchema = parseJsonSchema(top["json_schema"]),
        )
    }

    private fun parsePackages(value: Any?): ConfigPackages {
        val location = "$source: android.packages"
        val keys = arrayOf("remote", "local", "instrumented", "instrumented_impl", "resolved", "plugin", "plugin_dsl")
        val map = reader.map(value, location, *keys)
        val packages = keys.associateWith { reader.string(map[it], "$location.$it") }
        return ConfigPackages(
            remote = packages.getValue("remote"),
            local = packages.getValue("local"),
            instrumented = packages.getValue("instrumented"),
            instrumentedImpl = packages.getValue("instrumented_impl"),
            resolved = packages.getValue("resolved"),
            plugin = packages.getValue("plugin"),
            pluginDsl = packages.getValue("plugin_dsl"),
        )
    }

    private fun parseJsonSchema(value: Any?): JsonSchemaSpec {
        val location = "$source: json_schema"
        val map = reader.map(value, location, "id", "title", "description")
        return JsonSchemaSpec(
            id = reader.string(map["id"], "$location.id"),
            title = reader.string(map["title"], "$location.title"),
            description = reader.string(map["description"], "$location.description"),
        )
    }

    private fun parseEnums(value: Any?): List<ConfigEnumSpec> {
        val enums = reader.list(value ?: emptyList<Any>(), "$source: enums").map {
            val map = reader.map(it, "$source: enums", "name", "doc", "values")
            val name = reader.string(map["name"], "$source: enum name")
            val location = "$source: $name"
            val values = reader.list(map["values"], "$location.values").map { entry ->
                val valueMap = reader.map(entry, "$location.values", "name", "json", "doc")
                ConfigEnumValueSpec(
                    name = reader.string(valueMap["name"], "$location: value name"),
                    json = reader.string(valueMap["json"], "$location: value json"),
                    doc = valueMap["doc"]?.let { doc -> reader.string(doc, "$location: value doc") },
                )
            }
            reader.ensureUnique(values.map { entry -> entry.name }, location, "value")
            ConfigEnumSpec(name, map["doc"]?.let { doc -> reader.string(doc, "$location.doc") }, values)
        }
        reader.ensureUnique(enums.map { it.name }, source, "enum")
        return enums
    }

    private fun parseInstrumented(value: Any?): List<InstrumentedClassSpec> {
        val location = "$source: android.instrumented"
        val classes = reader.list(value, location).map {
            val map = reader.map(it, location, "name", "property", "doc", "plugin_params")
            val name = reader.string(map["name"], "$location: name")
            val params = map["plugin_params"]?.let { params ->
                reader.list(params, "$location: $name.plugin_params").map { param ->
                    reader.string(param, "$location: $name.plugin_params")
                }
            } ?: listOf("cfg")
            reader.ensure(params.all { param -> param in PLUGIN_PARAMS }, "$location: $name") {
                "plugin_params must be some of $PLUGIN_PARAMS"
            }
            InstrumentedClassSpec(
                name = name,
                property = reader.string(map["property"], "$location: $name.property"),
                doc = map["doc"]?.let { doc -> reader.string(doc, "$location: $name.doc") },
                pluginParams = params,
            )
        }
        reader.ensureUnique(classes.map { it.name }, location, "class")
        return classes
    }

    /** Registers a feature's standalone classes first, so that any option can use them as a type. */
    private fun parseTypes(feature: Map<String, Any?>, context: OptionContext) {
        val location = "$source: ${feature["name"]}"
        listOf("remote_types" to false, "local_types" to true).forEach { (key, local) ->
            val model = if (local) context.local else context.remote
            val parser = if (local) context.localFields else context.remoteFields
            reader.list(feature[key] ?: emptyList<Any>(), "$location.$key").forEach {
                val map = reader.map(it, "$location.$key", "name", "doc", "fields")
                val name = reader.string(map["name"], "$location.$key: name")
                val typeLocation = "$location.$name"
                val fieldMaps = reader.list(map["fields"], "$typeLocation.fields").map { field ->
                    reader.map(field, "$typeLocation.fields", *parser.fieldKeys.toTypedArray())
                }
                // A type's fields may refer to the type itself or to any class registered before it.
                val types = model.classNames + context.enums.keys + name
                val fields = fieldMaps.map { field ->
                    val fieldKey = reader.string(field["key"], "$typeLocation: field key")
                    parser.parse(field, fieldKey, "$typeLocation.$fieldKey", types)
                }
                reader.ensureUnique(fields.map { field -> field.key }, typeLocation, "key")
                val doc = map["doc"]?.let { doc -> reader.string(doc, "$typeLocation.doc") }
                model.addType(ConfigClassSpec(name, doc, fields), typeLocation)
            }
        }
    }

    private fun parseFeature(feature: Map<String, Any?>, context: OptionContext): ConfigGroupSpec {
        val name = reader.string(feature["name"], "$source: feature name")
        val location = "$source: $name"
        reader.ensure(IDENTIFIER.matches(name) && name.first().isLowerCase(), location) {
            "feature name must be a lower camel case identifier"
        }
        val options = reader.list(feature["options"], "$location.options").map { parseOption(it, location, context) }
        reader.ensureUnique(options.map { it.name }, location, "option")
        listOf("remote_fields" to false, "local_fields" to true).forEach { (key, local) ->
            val model = if (local) context.local else context.remote
            val parser = if (local) context.localFields else context.remoteFields
            val types = if (local) context.localTypes else context.remoteTypes
            reader.list(feature[key] ?: emptyList<Any>(), "$location.$key").forEach {
                val map = reader.map(it, "$location.$key", *parser.fieldKeys.toTypedArray())
                val path = reader.string(map["key"], "$location.$key: key")
                val fieldLocation = "$location.$path"
                model.declare(path, parser.parse(map, path.substringAfterLast('.'), fieldLocation, types), fieldLocation)
            }
        }
        return ConfigGroupSpec(name, feature["doc"]?.let { reader.string(it, "$location.doc") }, options)
    }

    private fun parseOption(value: Any?, featureLocation: String, context: OptionContext): ConfigOptionSpec {
        val name = reader.string((value as? Map<*, *>)?.get("name"), "$featureLocation: option name")
        val location = "$featureLocation.$name"
        val map = reader.map(value, location, *OPTION_KEYS)
        reader.ensure(IDENTIFIER.matches(name) && name.first().isLowerCase(), location) {
            "option name must be a lower camel case identifier"
        }

        val (type, nullable) = parseType(map["type"], "$location.type", context.optionTypes)
        val default = map["default"]
        reader.ensure(default != null || nullable, location) { "a non-null option must have a default" }
        reader.ensure(default == null || ConfigTypes.isValidDefault(default, type, context.enums), location) {
            "default '$default' is not a valid $type"
        }
        val doc = map["doc"]?.let { reader.string(it, "$location.doc") }

        val android = map["android"]?.let { reader.map(it, "$location.android", *ANDROID_KEYS) } ?: emptyMap()
        val pluginValue = android["plugin_value"]?.let { reader.string(it, "$location.android.plugin_value") }
        val resolver = android["resolver"]?.let { reader.string(it, "$location.android.resolver") }
        reader.ensure(resolver == null || IDENTIFIER.matches(resolver), location) { "resolver must be a function name" }
        val instrumented = android["instrumented"]?.let {
            parseInstrumentedMethod(it, android, location, type, nullable, default, context)
        }
        reader.ensure(instrumented != null || (android["instrumented_type"] == null && android["instrumented_default"] == null), location) {
            "instrumented_type and instrumented_default require android.instrumented"
        }
        reader.ensure(resolver != null || instrumented == null || (instrumented.type == type && instrumented.nullable == nullable), location) {
            "an option whose instrumented_type differs from its type needs a resolver to convert the local value"
        }
        reader.ensure(pluginValue == null || instrumented != null, location) {
            "android.plugin_value needs android.instrumented to deliver it to the SDK"
        }

        val remote = map["remote"]?.let { parseRemote(it, "$location.remote", type, default, doc, context) }
        val localPath = map["local"]?.let { parseLocal(it, "$location.local", type, default, instrumented, pluginValue, context) }
        if (instrumented != null && (localPath != null || pluginValue != null)) {
            reader.ensure(instrumented.type in PLUGIN_TYPES || instrumented.type in context.enums, location) {
                "the Gradle plugin can't instrument a method returning ${instrumented.type}. Use one of $PLUGIN_TYPES or an enum"
            }
        }

        return ConfigOptionSpec(
            name = name,
            type = type,
            nullable = nullable,
            default = default,
            doc = doc,
            remote = remote,
            localPath = localPath,
            instrumented = instrumented,
            pluginValue = pluginValue,
            resolver = resolver,
        )
    }

    private fun parseType(value: Any?, location: String, types: Set<String>): Pair<String, Boolean> {
        val text = reader.string(value, location)
        val type = text.removeSuffix("?")
        val element = ConfigTypes.element(type)
        reader.ensure(element in ConfigTypes.SCALARS || element in types, location) { ConfigTypes.unsupportedTypeMessage(type) }
        return type to text.endsWith("?")
    }

    /**
     * Parses where an option is read from in the remote config, declaring the field if this is the first use of
     * its path.
     */
    @Suppress("LongParameterList")
    private fun parseRemote(
        value: Any,
        location: String,
        type: String,
        default: Any?,
        doc: String?,
        context: OptionContext,
    ): RemoteSourceSpec {
        val parser = context.remoteFields
        val map = sourceMap(value, location, parser.sourceKeys + REMOTE_MODIFIERS)
        val path = reader.string(map["key"], "$location.key")
        val rollout = reader.boolean(map["rollout"], "$location.rollout") ?: false
        val field = referenceOrDeclare(context.remote, parser, map, path, location) {
            reader.ensure(!rollout || map["type"] != null, location) { "a rollout must declare its percentage's type, Float or Int" }
            parser.parse(map, path.substringAfterLast('.'), location, context.remoteTypes, defaultType = type, defaultDoc = doc)
        }
        if (rollout) {
            reader.ensure(type == "Boolean" && field.type in setOf("Float", "Int"), location) {
                "a rollout reads a Float or Int percentage into a Boolean option, but '$path' is a ${field.type}"
            }
        } else {
            reader.ensure(field.type == type, location) { "'$path' is a ${field.type}, but the option is a $type" }
        }
        val clamp = map["clamp"]?.let { parseRange(it, "$location.clamp", type, default) }
        val validRange = map["valid_range"]?.let { parseRange(it, "$location.valid_range", type, default) }
        reader.ensure(!rollout || (clamp == null && validRange == null), location) { "a rollout can't have a range" }
        reader.ensure(clamp == null || validRange == null, location) { "use either clamp or valid_range, not both" }
        return RemoteSourceSpec(path, rollout, clamp, validRange)
    }

    /**
     * Parses where an option is read from in embrace-config.json, declaring the field if this is the first use of
     * its path. The field has the instrumented method's type unless the plugin converts it.
     */
    @Suppress("LongParameterList")
    private fun parseLocal(
        value: Any,
        location: String,
        type: String,
        default: Any?,
        instrumented: InstrumentedMethodSpec?,
        pluginValue: String?,
        context: OptionContext,
    ): String {
        val method = instrumented ?: reader.fail(
            location,
            "an option with a local value must set android.instrumented to deliver it to the SDK",
        )
        val parser = context.localFields
        val map = sourceMap(value, location, parser.sourceKeys)
        val path = reader.string(map["key"], "$location.key")
        val field = referenceOrDeclare(context.local, parser, map, path, location) {
            val fieldType = map["type"]?.let { reader.string(it, "$location.type") } ?: method.type
            parser.parse(
                map,
                path.substringAfterLast('.'),
                location,
                context.localTypes,
                defaultType = method.type,
                defaultSchemaDefault = default.takeIf { fieldType == type },
            )
        }
        reader.ensure(pluginValue != null || field.type == method.type, location) {
            "local '$path' is a ${field.type}, but the instrumented method returns ${method.type}. " +
                "Set android.plugin_value to convert it"
        }
        return path
    }

    private fun sourceMap(value: Any, location: String, keys: List<String>): Map<String, Any?> =
        if (value is String) mapOf("key" to value) else reader.map(value, location, *(listOf("key") + keys).toTypedArray())

    /**
     * The field at [path] if an earlier option declared it, in which case [map] may not declare it again, and
     * otherwise the field [declare] parses.
     */
    private fun referenceOrDeclare(
        model: ModelBuilder,
        parser: FieldParser,
        map: Map<String, Any?>,
        path: String,
        location: String,
        declare: () -> ConfigFieldSpec,
    ): ConfigFieldSpec {
        model.field(path)?.let { existing ->
            val redeclared = parser.sourceKeys.filter { it in map }
            reader.ensure(redeclared.isEmpty(), location) { "'$path' is already declared, so $redeclared can't be set here" }
            return existing
        }
        return declare().also { model.declare(path, it, location) }
    }

    private fun parseRange(value: Any?, location: String, type: String, default: Any?): ValueRangeSpec {
        reader.ensure(type in ConfigTypes.NUMERIC, location) { "a range needs a numeric option, not $type" }
        val map = reader.map(value, location, "min", "max")
        val (min, max) = listOf("min", "max").map { key ->
            map[key]?.also {
                reader.ensure(ConfigTypes.isValidDefault(it, type, emptyMap()), location) { "$key '$it' is not a valid $type" }
            } as Number?
        }
        reader.ensure(min != null || max != null, location) { "a range needs a min or a max" }
        val number = (default as? Number)?.toDouble()
        reader.ensure(number == null || ((min == null || number >= min.toDouble()) && (max == null || number <= max.toDouble())), location) {
            "the default $default is outside the range"
        }
        return ValueRangeSpec(min, max)
    }

    @Suppress("LongParameterList")
    private fun parseInstrumentedMethod(
        value: Any,
        android: Map<String, Any?>,
        location: String,
        type: String,
        nullable: Boolean,
        default: Any?,
        context: OptionContext,
    ): InstrumentedMethodSpec {
        val reference = reader.string(value, "$location.android.instrumented")
        val className = reference.substringBefore('.')
        val method = reference.substringAfter('.', "")
        reader.ensure(className in context.instrumented && IDENTIFIER.matches(method), location) {
            "instrumented must be <class>.<method>, where the class is one of ${context.instrumented.keys}"
        }
        val (methodType, methodNullable) = android["instrumented_type"]?.let {
            parseType(it, "$location.android.instrumented_type", context.enums.keys)
        } ?: (type to nullable)
        val sameType = methodType == type && methodNullable == nullable
        val methodDefault = if (sameType) default else android["instrumented_default"]
        reader.ensure(!sameType || android["instrumented_default"] == null, location) {
            "instrumented_default is only needed when instrumented_type differs from the option type"
        }
        reader.ensure(methodDefault != null || methodNullable, location) {
            "a non-null instrumented method needs android.instrumented_default"
        }
        reader.ensure(methodDefault == null || ConfigTypes.isValidDefault(methodDefault, methodType, context.enums), location) {
            "instrumented_default '$methodDefault' is not a valid $methodType"
        }
        return InstrumentedMethodSpec(className, method, methodType, methodNullable, methodDefault)
    }
}
