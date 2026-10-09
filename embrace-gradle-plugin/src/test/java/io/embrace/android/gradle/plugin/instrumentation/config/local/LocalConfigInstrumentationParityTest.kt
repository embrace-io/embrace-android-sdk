package io.embrace.android.gradle.plugin.instrumentation.config.local

import io.embrace.android.gradle.plugin.instrumentation.ASM_API_VERSION
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val PACKAGE = "io/embrace/android/embracesdk/internal/config/instrumented"
private const val LOCAL_CONFIG_INSTRUMENTED = "L$PACKAGE/LocalConfigInstrumented;"
private const val LOCAL_CONFIG_KEY = "L$PACKAGE/LocalConfigKey;"

/**
 * Instruments the local config classes that embrace-android-config actually generates. This will
 * fail if the generated code and plugin disagree on how a getter is keyed.
 */
class GeneratedLocalConfigInstrumentationTest {

    private val classes: Map<String, ByteArray> = readGeneratedClasses()

    @Test
    fun `every generated class has keyed getters`() {
        assertFalse("No @LocalConfigInstrumented classes found", classes.isEmpty())
        classes.forEach { (name, bytecode) ->
            assertFalse("$name has no @LocalConfigKey getters", readGetters(bytecode).isEmpty())
        }
    }

    @Test
    fun `getters return generated defaults when embrace-config json omits them`() {
        classes.forEach { (name, bytecode) ->
            val getters = readGetters(bytecode)
            assertEquals(name, invoke(name, bytecode, getters), invoke(name, instrument(bytecode, "{}"), getters))
        }
    }

    @Test
    fun `getters return values set in embrace-config json`() {
        classes.forEach { (name, bytecode) ->
            val getters = readGetters(bytecode)
            val overrides = invoke(name, bytecode, getters).map(::override)
            val json = jsonOf(getters.map { it.path }.zip(overrides).toMap())
            assertEquals(name, overrides, invoke(name, instrument(bytecode, json), getters))
        }
    }

    /**
     * Reads every `@LocalConfigInstrumented` class from embrace-android-config on the test runtime classpath.
     */
    private fun readGeneratedClasses(): Map<String, ByteArray> {
        val uri = checkNotNull(javaClass.classLoader.getResource("$PACKAGE/LocalConfigKey.class")).toURI()
        return if (uri.scheme == "jar") {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { readGeneratedClasses(it.getPath(PACKAGE)) }
        } else {
            readGeneratedClasses(Paths.get(uri).parent)
        }
    }

    private fun readGeneratedClasses(dir: Path): Map<String, ByteArray> =
        Files.list(dir).use { files ->
            files.toList()
                .filter { it.fileName.toString().endsWith(".class") }
                .map(Files::readAllBytes)
                .filter(::isLocalConfigInstrumented)
                .associateBy { ClassReader(it).className.replace('/', '.') }
        }

    private fun isLocalConfigInstrumented(bytecode: ByteArray): Boolean {
        var found = false
        ClassReader(bytecode).accept(
            object : ClassVisitor(ASM_API_VERSION) {
                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    found = found || descriptor == LOCAL_CONFIG_INSTRUMENTED
                    return null
                }
            },
            ClassReader.SKIP_CODE,
        )
        return found
    }

    private fun readGetters(bytecode: ByteArray): List<LocalConfigGetter> {
        val getters = mutableListOf<LocalConfigGetter>()
        ClassReader(bytecode).accept(
            object : ClassVisitor(ASM_API_VERSION) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor = object : MethodVisitor(ASM_API_VERSION) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
                        if (descriptor != LOCAL_CONFIG_KEY) {
                            null
                        } else {
                            object : AnnotationVisitor(ASM_API_VERSION) {
                                override fun visit(key: String?, value: Any?) {
                                    getters += LocalConfigGetter(name, value as String)
                                }
                            }
                        }
                }
            },
            ClassReader.SKIP_CODE,
        )
        return getters
    }

    private fun instrument(bytecode: ByteArray, json: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        ClassReader(bytecode).accept(LocalConfigClassVisitor(json, ASM_API_VERSION, writer), 0)
        return writer.toByteArray()
    }

    /**
     * Loads [bytecode] in its own class loader and invokes [getters] on its Kotlin object instance.
     */
    private fun invoke(name: String, bytecode: ByteArray, getters: List<LocalConfigGetter>): List<Any?> {
        val cls = object : ClassLoader(javaClass.classLoader) {
            val subject: Class<*> = defineClass(name, bytecode, 0, bytecode.size)
        }.subject
        val instance = cls.getField("INSTANCE").get(null)
        return getters.map { cls.getMethod(it.name).invoke(instance) }
    }

    private fun override(default: Any?): Any = when (default) {
        is Boolean -> !default
        is Int -> default + 1
        is Long -> default + 1
        is String -> "${default}_override"
        else -> error("Unsupported local config type: $default")
    }

    /**
     * Builds embrace-config.json with each value nested at its dot-separated path.
     */
    private fun jsonOf(values: Map<String, Any>): String {
        val root = mutableMapOf<String, Any>()
        values.forEach { (path, value) ->
            val keys = path.split('.')
            val parent = keys.dropLast(1).fold(root) { node, key ->
                @Suppress("UNCHECKED_CAST")
                node.getOrPut(key) { mutableMapOf<String, Any>() } as MutableMap<String, Any>
            }
            parent[keys.last()] = value
        }
        return toJson(root).toString()
    }

    private fun toJson(value: Any): JsonElement = when (value) {
        is Map<*, *> -> JsonObject(value.entries.associate { (key, child) -> key as String to toJson(checkNotNull(child)) })
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
