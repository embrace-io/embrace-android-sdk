package io.embrace.android.embracesdk.internal.utils.event

import androidx.annotation.CheckResult

/**
 * An (almost) immutable snapshot of the handlers registered against one key, in delivery order, each paired with the order it was
 * registered with.
 */
internal class Registrations private constructor(
    val handlers: Array<EventHandler<*>>,
    private val orders: IntArray,
) {

    /**
     * Returns these registrations with [handler] after every handler whose order is no greater than [order], or these registrations
     * unchanged if [handler] is already registered.
     */
    @CheckResult
    fun add(handler: EventHandler<*>, order: Int): Registrations {
        if (indexOf(handler) >= 0) {
            return this
        }
        var index = orders.size
        while (index > 0 && orders[index - 1] > order) {
            index--
        }

        val newHandlers = arrayOfNulls<EventHandler<*>>(handlers.size + 1)
        handlers.copyInto(newHandlers, endIndex = index)
        handlers.copyInto(newHandlers, destinationOffset = index + 1, startIndex = index)
        newHandlers[index] = handler

        val newOrders = IntArray(orders.size + 1)
        orders.copyInto(newOrders, endIndex = index)
        orders.copyInto(newOrders, destinationOffset = index + 1, startIndex = index)
        newOrders[index] = order

        return Registrations(newHandlers.withoutNulls(), newOrders)
    }

    /**
     * Returns these registrations without [handler], or these registrations unchanged if [handler] is not registered.
     */
    @CheckResult
    fun remove(handler: EventHandler<*>): Registrations {
        val index = indexOf(handler)
        if (index < 0) {
            return this
        }

        val newHandlers = arrayOfNulls<EventHandler<*>>(handlers.size - 1)
        handlers.copyInto(newHandlers, endIndex = index)
        handlers.copyInto(newHandlers, destinationOffset = index, startIndex = index + 1)

        val newOrders = IntArray(orders.size - 1)
        orders.copyInto(newOrders, endIndex = index)
        orders.copyInto(newOrders, destinationOffset = index, startIndex = index + 1)

        return Registrations(newHandlers.withoutNulls(), newOrders)
    }

    private fun indexOf(handler: EventHandler<*>): Int = handlers.indexOfFirst { it === handler }

    @Suppress("UNCHECKED_CAST") // every slot has been filled, so no nulls
    private fun Array<EventHandler<*>?>.withoutNulls(): Array<EventHandler<*>> = this as Array<EventHandler<*>>

    companion object {
        /**
         * No handlers, the registrations of a key that nothing has been registered against.
         */
        val EMPTY: Registrations = Registrations(emptyArray(), IntArray(0))
    }
}
