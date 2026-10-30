package io.embrace.bytecode.strategy

/**
 * Defines all the potential strategies for deciding whether a class should be visited by a particular feature
 * during bytecode instrumentation.
 */
sealed class ClassVisitStrategy {

    /**
     * Returns true if the class should be visited for _potential_ instrumentation.
     */
    abstract fun shouldVisit(ctx: ClassInfo): Boolean

    /**
     * Visits every class.
     */
    internal object Exhaustive : ClassVisitStrategy() {
        override fun shouldVisit(ctx: ClassInfo): Boolean = true
    }

    /**
     * Visits every class matching the given name.
     */
    internal class MatchClassName(private val name: String) : ClassVisitStrategy() {
        override fun shouldVisit(ctx: ClassInfo): Boolean {
            return ctx.className == name
        }
    }

    /**
     * Visits every class that has a superclass matching the given name.
     */
    internal class MatchSuperClassName(private val name: String) : ClassVisitStrategy() {
        override fun shouldVisit(ctx: ClassInfo): Boolean {
            return ctx.superClasses.contains(name)
        }
    }
}
