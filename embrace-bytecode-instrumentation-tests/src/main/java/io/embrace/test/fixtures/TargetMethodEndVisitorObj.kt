package io.embrace.test.fixtures

import kotlin.random.Random

@Suppress("unused")
class TargetMethodEndVisitorObj {
    fun instrumentAtEndWithReturn() {
        print("Instrumenting at the end of a method with a return statement")
        return
    }

    fun instrumentAtEndWithoutReturn() {
        print("Instrumenting at the end of a method without a return statement")
    }

    fun instrumentAtEndWithThrow() {
        print("Instrumenting at the end of a method with a throw statement")
        throw Exception("Exception")
    }

    fun instrumentAtEndWithMultipleReturns() {
        if (Random.nextBoolean()) {
            print("Instrumenting at the end of a method with multiple return statements - first return")
            return
        } else {
            print("Instrumenting at the end of a method with multiple return statements - second return")
            return
        }
    }
}
