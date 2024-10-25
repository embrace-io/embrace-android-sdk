package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopFlutterInternalInterface
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

internal class NoopFlutterInternalInterfaceTest {

    private lateinit var impl: NoopFlutterInternalInterface

    @Before
    fun setUp() {
        impl = NoopFlutterInternalInterface(
            NoopEmbraceInternalInterface(
                mockk(relaxed = true)
            )
        )
    }

    @Test
    fun `check no errors thrown when invoked`() {
        impl.setEmbraceFlutterSdkVersion("version")
        impl.logHandledDartException(
            "stack",
            "name",
            "message",
            "context",
            "library"
        )
        impl.logUnhandledDartException(
            "stack",
            "name",
            "message",
            "context",
            "library"
        )
    }
}
