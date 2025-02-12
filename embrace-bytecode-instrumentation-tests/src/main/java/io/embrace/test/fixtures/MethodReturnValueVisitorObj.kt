package io.embrace.test.fixtures

@Suppress("FunctionOnlyReturningConstant")
class MethodReturnValueVisitorObj {
    fun getSomeBool(): Boolean = false
    fun getSomeInt(): Int = 100
    fun getSomeLong(): Long = 2
    fun getSomeStr(): String = "Hi"
    fun getSomeList(): List<String> = emptyList()
    fun getSomeMap(): Map<String, Int> = emptyMap()
}
