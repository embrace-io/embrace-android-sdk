package io.embrace.android.embracesdk;

import static org.junit.Assert.assertFalse;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.embrace.android.embracesdk.app.AppFramework;

@RunWith(AndroidJUnit4.class)
public class PreSdkStartTest {

    @Rule
    public IntegrationTestRule testRule = new IntegrationTestRule(
        () -> IntegrationTestRule.Companion.newHarness(false)
    );

    @NonNull
    private final Embrace embrace = testRule.getEmbrace();

    @Test
    public void testStartWithNullContext() {
        embrace.start(null);
        embrace.start(null, true);
        embrace.start(null, false, AppFramework.NATIVE);
        assertFalse(embrace.isStarted());
    }

    @Test
    public void testStartWithNullAppFramework() {
        embrace.start(testRule.harness.getFakeCoreModule().getContext(), false, null);
        assertFalse(embrace.isStarted());
    }

    @Test
    public void testSetAppId() {
        assertFalse(embrace.setAppId(null));
    }
}
