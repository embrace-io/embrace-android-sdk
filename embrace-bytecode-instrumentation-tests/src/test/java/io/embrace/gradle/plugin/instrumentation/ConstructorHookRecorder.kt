package io.embrace.gradle.plugin.instrumentation

/**
 * Records every object passed to the hook that constructor instrumentation inserts, along with its toString() at that time.
 */
object ConstructorHookRecorder {

    val constructed: MutableList<Any> = mutableListOf()
    val states: MutableList<String> = mutableListOf()

    @JvmStatic
    fun onConstructed(obj: Any) {
        constructed.add(obj)
        states.add(obj.toString())
    }

    fun clear() {
        constructed.clear()
        states.clear()
    }
}
