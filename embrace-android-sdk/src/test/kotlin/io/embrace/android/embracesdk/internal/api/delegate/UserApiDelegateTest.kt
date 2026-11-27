package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.fakes.FakeUserService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class UserApiDelegateTest {

    private lateinit var delegate: UserApiDelegate
    private lateinit var fakeUserService: FakeUserService

    @Before
    fun setUp() {
        fakeUserService = FakeUserService()
        delegate = UserApiDelegate(lazy { fakeUserService })
    }

    @Test
    fun `user identifier`() {
        delegate.userIdentifier = "test"
        assertEquals("test", fakeUserService.id)
        delegate.userIdentifier = null
        assertNull(fakeUserService.id)
    }
}
