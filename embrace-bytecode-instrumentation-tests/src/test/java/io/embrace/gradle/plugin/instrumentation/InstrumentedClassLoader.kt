package io.embrace.gradle.plugin.instrumentation

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

/**
 * Defines instrumented copies of the given classes and delegates everything else to the parent.
 */
internal class InstrumentedClassLoader(
    private val classNames: Set<String>,
    private val factory: (nextVisitor: ClassVisitor, className: String) -> ClassVisitor,
) : ClassLoader(InstrumentedClassLoader::class.java.classLoader) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name !in classNames) {
            return super.loadClass(name, resolve)
        }
        val cls = findLoadedClass(name) ?: instrument(name).let { defineClass(name, it, 0, it.size) }
        if (resolve) {
            resolveClass(cls)
        }
        return cls
    }

    private fun instrument(name: String): ByteArray {
        val reader = ClassReader(name)
        val writer = ClassWriter(reader, ASM_CLASS_WRITER_FLAGS)
        reader.accept(factory(writer, name), ASM_CLASS_READER_FLAGS)
        return writer.toByteArray()
    }
}
