package io.embrace.gradle.configmodel

/**
 * Builds a JSON model from fields declared throughout the schema. Each field is declared by its dotted path from the
 * root, and every object along that path must be declared in the model's `objects`. A class's fields are ordered by
 * declaration, and an object's field is added to its parent when the first field inside it is declared.
 */
internal class ModelBuilder(
    private val reader: SpecReader,
    private val location: String,
    value: Any?,
) {

    private val rootName: String
    private val rootDoc: String?
    private val objects: Map<String, ObjectSpec>
    private val fields = LinkedHashMap<String, MutableList<ConfigFieldSpec>>()
    private val declared = mutableMapOf<String, ConfigFieldSpec>()
    private val types = mutableListOf<ConfigClassSpec>()

    init {
        val map = reader.map(value, location, "root", "objects")
        val root = reader.map(map["root"], "$location.root", "class", "doc")
        rootName = reader.string(root["class"], "$location.root.class")
        rootDoc = root["doc"]?.let { reader.string(it, "$location.root.doc") }
        objects = reader.list(map["objects"] ?: emptyList<Any>(), "$location.objects").map { parseObject(it) }
            .also { specs -> reader.ensureUnique(specs.map { it.key }, location, "object") }
            .associateBy { it.key }
        fields[rootName] = mutableListOf()
    }

    /** The names of the model's classes, which fields may use as types. */
    val classNames: Set<String> get() = setOf(rootName) + objects.values.map { it.className } + types.map { it.name }

    fun addType(cls: ConfigClassSpec, typeLocation: String) {
        reader.ensure(cls.name !in classNames, typeLocation) { "class '${cls.name}' is already defined" }
        types.add(cls)
    }

    /** The field already declared at [path], if any. */
    fun field(path: String): ConfigFieldSpec? = declared[path]

    fun declare(path: String, field: ConfigFieldSpec, fieldLocation: String) {
        reader.ensure(path !in declared, fieldLocation) { "'$path' is declared more than once" }
        reader.ensure(path !in objects, fieldLocation) { "'$path' is declared as an object" }
        val segments = path.split('.')
        var cls = rootName
        segments.dropLast(1).indices.forEach { index ->
            val objectPath = segments.take(index + 1).joinToString(".")
            val obj = objects[objectPath] ?: reader.fail(
                fieldLocation,
                "'$objectPath' is not an object. Add it to $location.objects",
            )
            val parentFields = fields.getValue(cls)
            if (parentFields.none { it.key == segments[index] }) {
                parentFields.add(obj.field(segments[index]))
            }
            cls = obj.className
            fields.getOrPut(cls) { mutableListOf() }
        }
        fields.getValue(cls).add(field)
        declared[path] = field
    }

    fun build(): ConfigModelSpec {
        val unused = objects.values.filter { it.className !in fields }.map { it.key }
        reader.ensure(unused.isEmpty(), location) { "objects $unused have no fields" }
        val classDocs = objects.values.associate { it.className to it.classDoc } + (rootName to rootDoc)
        return ConfigModelSpec(fields.map { (name, fields) -> ConfigClassSpec(name, classDocs[name], fields) } + types)
    }

    private fun parseObject(value: Any?): ObjectSpec {
        val map = reader.map(value, "$location.objects", "key", "class", "property", "doc", "class_doc", "deprecated")
        val key = reader.string(map["key"], "$location.objects: key")
        val objectLocation = "$location.objects.$key"
        return ObjectSpec(
            key = key,
            className = reader.string(map["class"], "$objectLocation.class"),
            property = map["property"]?.let { reader.string(it, "$objectLocation.property") },
            doc = map["doc"]?.let { reader.string(it, "$objectLocation.doc") },
            classDoc = map["class_doc"]?.let { reader.string(it, "$objectLocation.class_doc") },
            deprecated = map["deprecated"]?.let { reader.string(it, "$objectLocation.deprecated") },
        )
    }
}
