package io.embrace.test.fixtures

@Suppress("unused")
class KotlinConstructorTarget(value: Any, val label: String = "default") : ConstructorTargetBase(value) {

    val length: Int = label.length

    constructor() : this(Any())

    constructor(count: Int) : this(List(count) { it }, "count")
}
