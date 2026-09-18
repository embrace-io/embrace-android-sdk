package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class StateSchemaTypeTest {

    @Test
    fun `state with a value that is not a system value records only the value`() {
        // A value that does not implement TypedStateValue at all
        assertEquals(
            mapOf(EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE to "foo"),
            TestState("foo").attributes(),
        )
        assertNull(recordedStateValueType("foo"))

        // A value that implements TypedStateValue but is not a system value
        assertEquals(
            mapOf(EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE to "foo"),
            TypedTestState(TypedValue("foo")).attributes(),
        )
        assertNull(recordedStateValueType(TypedValue("foo")))
    }

    @Test
    fun `state with a system value records the value type from the semantic conventions`() {
        val systemValue = TypedValue("foo", isSystemValue = true)
        assertEquals(
            mapOf(
                EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE to "foo",
                EmbStateTransitionAttributes.EMB_STATE_VALUE_TYPE to EmbStateTransitionAttributes.EmbStateValueTypeValues.SYSTEM,
            ),
            TypedTestState(systemValue).attributes(),
        )
        assertEquals(EmbStateTransitionAttributes.EmbStateValueTypeValues.SYSTEM, recordedStateValueType(systemValue))
    }

    private class TestState(initialValue: String) : SchemaType.State<String>(initialValue, "test")

    private class TypedTestState(initialValue: TypedValue) : SchemaType.State<TypedValue>(initialValue, "typed")

    private data class TypedValue(
        val value: String,
        override val isSystemValue: Boolean = false,
    ) : TypedStateValue {
        override fun toString(): String = value
    }
}
