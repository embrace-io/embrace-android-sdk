package io.embrace.android.gradle.plugin.instrumentation.config.arch

/**
 * DSL for creating a model of how an SDK config class should be instrumented.
 */
fun modelSdkConfigClass(init: InstrumentedConfigClass.() -> Unit): InstrumentedConfigClass {
    return InstrumentedConfigClass().apply(init)
}

/**
 * DSL to declare how an SDK config method should be instrumented.
 */
fun InstrumentedConfigClass.boolMethod(name: String, valueProvider: () -> Boolean?) {
    addMethod(InstrumentedConfigMethod(name, ReturnType.BOOLEAN, valueProvider))
}

/**
 * DSL to declare how an SDK config method should be instrumented.
 */
fun InstrumentedConfigClass.intMethod(name: String, valueProvider: () -> Int?) {
    addMethod(InstrumentedConfigMethod(name, ReturnType.INT, valueProvider))
}

/**
 * DSL to declare how an SDK config method should be instrumented.
 */
fun InstrumentedConfigClass.longMethod(name: String, valueProvider: () -> Long?) {
    addMethod(InstrumentedConfigMethod(name, ReturnType.LONG, valueProvider))
}

/**
 * DSL to declare how an SDK config method should be instrumented.
 */
fun InstrumentedConfigClass.stringMethod(name: String, valueProvider: () -> String?) {
    addMethod(InstrumentedConfigMethod(name, ReturnType.STRING, valueProvider))
}

/**
 * DSL to declare how an SDK config method should be instrumented.
 */
fun InstrumentedConfigClass.stringListMethod(name: String, valueProvider: () -> List<String>?) {
    addMethod(InstrumentedConfigMethod(name, ReturnType.STRING_LIST, valueProvider))
}

/**
 * DSL to declare how an SDK config method should be instrumented.
 */
fun InstrumentedConfigClass.mapMethod(name: String, valueProvider: () -> Map<String, String>?) {
    addMethod(InstrumentedConfigMethod(name, ReturnType.MAP, valueProvider))
}
