package io.embrace.android.embracesdk;

import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.embrace.android.embracesdk.testframework.IntegrationTestRule;
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface;

@RunWith(AndroidJUnit4.class)
public class PreSdkStartTest {

    @Rule
    public IntegrationTestRule testRule = new IntegrationTestRule(() -> new EmbraceSetupInterface(0, false));

    @NonNull
    private final Embrace getEmbrace() {
        return testRule.getEmbrace();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testStartWithNullContext() {
        getEmbrace().start(null);
        getEmbrace().start(null, Embrace.AppFramework.NATIVE);
        assertFalse(getEmbrace().isStarted());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testStartWithNullAppFramework() {
        Context context = testRule.setup.getOverriddenCoreModule().getContext();
        getEmbrace().start(context, null);
        assertFalse(getEmbrace().isStarted());
    }

    @Test
    public void testSetAppId() {
        assertFalse(getEmbrace().setAppId(null));
    }
}
