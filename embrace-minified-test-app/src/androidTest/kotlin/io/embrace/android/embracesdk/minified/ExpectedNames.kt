package io.embrace.android.embracesdk.minified

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * A name that must survive R8, as listed in the `expected_names.txt` asset that
 * `generateExpectedNames` writes from the SDK's sources, or declared by a test.
 */
internal sealed class ExpectedName {
    abstract val className: String

    data class Class(override val className: String) : ExpectedName()
    data class Provider(override val className: String) : ExpectedName()
    data class Method(override val className: String, val name: String, val paramCount: Int = -1) : ExpectedName()
    data class Field(override val className: String, val name: String) : ExpectedName()
}

internal val appClassLoader: ClassLoader
    get() = ApplicationProvider.getApplicationContext<android.content.Context>().classLoader

internal fun generatedExpectedNames(): List<ExpectedName> {
    val assets = InstrumentationRegistry.getInstrumentation().context.assets
    return assets.open("expected_names.txt").bufferedReader().readLines()
        .filter(String::isNotBlank)
        .map { line ->
            val parts = line.split(' ')
            when (parts[0]) {
                "provider" -> ExpectedName.Provider(parts[1])
                "method" -> ExpectedName.Method(parts[1], parts[2], parts[3].toInt())
                else -> error("Unknown entry in expected_names.txt: $line")
            }
        }
}

/**
 * Returns null if [name] resolves in the minified app, or a description of what R8 removed.
 */
internal fun findMissing(name: ExpectedName): String? {
    val cls = try {
        Class.forName(name.className, false, appClassLoader)
    } catch (ignored: ClassNotFoundException) {
        return "class ${name.className} was renamed or removed"
    }
    return when (name) {
        is ExpectedName.Class -> null
        is ExpectedName.Provider -> try {
            cls.getDeclaredConstructor()
            null
        } catch (ignored: NoSuchMethodException) {
            "provider ${name.className} has no no-arg constructor"
        }
        is ExpectedName.Method -> {
            val found = cls.hierarchy().flatMap { it.declaredMethods.asSequence() }.any {
                it.name == name.name && (name.paramCount < 0 || it.parameterTypes.size == name.paramCount)
            }
            if (found) null else "method ${name.className}#${name.name} was renamed or removed. ${cls.describeMembers()}"
        }
        is ExpectedName.Field -> {
            val found = cls.hierarchy().flatMap { it.declaredFields.asSequence() }.any { it.name == name.name }
            if (found) null else "field ${name.className}#${name.name} was renamed or removed. ${cls.describeMembers()}"
        }
    }
}

/**
 * Asserts that every name resolves, reporting all failures at once.
 */
internal fun assertAllKept(names: List<ExpectedName>) {
    check(names.isNotEmpty()) { "No expected names supplied" }
    val missing = names.mapNotNull(::findMissing)
    if (missing.isNotEmpty()) {
        throw AssertionError(
            "${missing.size} of ${names.size} names used by the SDK via reflection, JNI or by name did not " +
                "survive R8. Add a keep rule to the owning module's embrace-proguard.cfg:\n" +
                missing.joinToString("\n") { " - $it" }
        )
    }
}

private fun Class<*>.hierarchy(): Sequence<Class<*>> {
    val seen = LinkedHashSet<Class<*>>()
    fun visit(cls: Class<*>?) {
        if (cls == null || !seen.add(cls)) {
            return
        }
        visit(cls.superclass)
        cls.interfaces.forEach(::visit)
    }
    visit(this)
    return seen.asSequence()
}

private fun Class<*>.describeMembers(): String {
    val members: List<Member> = declaredMethods.toList() + declaredFields.toList()
    return "Members present: " + members.joinToString { if (it is Method) "${it.name}()" else it.name }
}
