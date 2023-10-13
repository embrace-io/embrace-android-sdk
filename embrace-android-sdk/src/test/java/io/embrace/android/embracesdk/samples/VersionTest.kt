package io.embrace.android.embracesdk.samples

import org.junit.Assert.assertTrue
import org.junit.Test

internal class VersionTest {

    @Test
    fun `compare versions`() {
        assertTrue(
            ComparableVersion("5.2.1-beta01") < ComparableVersion(
                "5.2.1"
            )
        )
        assertTrue(
            ComparableVersion("5.2.1") > ComparableVersion(
                "5.2.1-beta01"
            )
        )
        assertTrue(
            ComparableVersion("5.2.1").compareTo(
                ComparableVersion("5.2.1")
            ) == 0
        )
        assertTrue(
            ComparableVersion("5.3.0") > ComparableVersion(
                "5.2.1-beta01"
            )
        )
        assertTrue(
            ComparableVersion("5.2.1-beta01") < ComparableVersion(
                "5.2.1-beta03"
            )
        )
        assertTrue(
            ComparableVersion("5.2.1-beta01") < ComparableVersion(
                "5.2.1-beta3"
            )
        )
        assertTrue(
            ComparableVersion("5.1.0") < ComparableVersion(
                "5.2.1-beta3"
            )
        )
        assertTrue(
            ComparableVersion("4.1.0-alpha1") < ComparableVersion(
                "5.2.1-beta3"
            )
        )
        assertTrue(
            ComparableVersion("4.1.0-alpha") < ComparableVersion(
                "5.2.1-beta"
            )
        )
        assertTrue(
            ComparableVersion("5.1.0-snapshot") < ComparableVersion(
                "5.2.1-beta3"
            )
        )
        assertTrue(
            ComparableVersion("5.1.0-SNAPSHOT") < ComparableVersion(
                "5.2.1-beta3"
            )
        )
        assertTrue(
            ComparableVersion("5.1.0") > ComparableVersion(
                "5.1.0-SNAPSHOT"
            )
        )
        assertTrue(
            ComparableVersion("5.1.0") > ComparableVersion(
                "5.1.0-beta"
            )
        )
        assertTrue(
            ComparableVersion("5.1.0") > ComparableVersion(
                "5.1.0-alpha"
            )
        )
    }
}
