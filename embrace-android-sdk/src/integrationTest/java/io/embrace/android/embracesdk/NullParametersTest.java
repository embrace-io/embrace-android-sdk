package io.embrace.android.embracesdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static io.embrace.android.embracesdk.Embrace.NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE;
import static io.embrace.android.embracesdk.testframework.assertions.InternalErrorAssertionsKt.assertInternalErrorLogged;

import android.webkit.ConsoleMessage;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.SocketException;
import java.util.Map;

import io.embrace.android.embracesdk.spans.EmbraceSpan;
import io.embrace.android.embracesdk.spans.ErrorCode;
import io.embrace.android.embracesdk.testframework.IntegrationTestRule;

/**
 * TODO: add a lint rule to verify that all public API methods that have @NonNull parameters have a corresponding test here
 */
@SuppressWarnings("DataFlowIssue")
@RunWith(AndroidJUnit4.class)
public class NullParametersTest {
    private static final SocketException EXCEPTION = new SocketException();

    private static final String NULL_STRING = null;

    @Rule
    public IntegrationTestRule testRule = new IntegrationTestRule();

    @NonNull
    private final Embrace getEmbrace() {
        return testRule.action.getEmbrace();
    }

    @Before
    public void before() {
        assertTrue(getEmbrace().isStarted());
    }

    @Test
    public void testAddSessionProperty() {
        getEmbrace().addSessionProperty(null, "value", false);
        assertError("addSessionProperty");
        getEmbrace().addSessionProperty("key", null, false);
        assertError("addSessionProperty");
    }

    @Test
    public void testAddUserPersona() {
        getEmbrace().addUserPersona(null);
        assertError("addUserPersona");
    }

    @Test
    public void testClearUserPersona() {
        getEmbrace().clearUserPersona(null);
        assertError("clearUserPersona");
    }

    @Test
    public void testRemoveSessionProperty() {
        getEmbrace().removeSessionProperty(null);
        assertError("removeSessionProperty");
    }

    @Test
    public void testStartMoment1Parameter() {
        getEmbrace().startMoment(null);
        assertError("startMoment");
    }

    @Test
    public void testStartMoment2Parameters() {
        getEmbrace().startMoment(null, null);
        assertError("startMoment");
    }

    @Test
    public void testStartMoment3ParametersAllowProperties() {
        getEmbrace().startMoment(null, null, null);
        assertError("startMoment");
    }

    @Test
    public void testEndMoment1Parameter() {
        getEmbrace().endMoment(null);
        assertError("endMoment");
    }

    @Test
    public void testEndMoment2ParametersCustomIdentifier() {
        getEmbrace().endMoment(null, NULL_STRING);
        assertError("endMoment");
    }

    @Test
    public void testEndMoment2ParametersAllowProperties() {
        getEmbrace().endMoment(null, NULL_STRING);
        assertError("endMoment");
    }

    @Test
    public void testEndMoment3Parameters() {
        getEmbrace().endMoment(null, NULL_STRING, null);
        assertError("endMoment");
    }

    @Test
    public void testEndAppStartup() {
        getEmbrace().endAppStartup(null);
        assertError("endAppStartup");
    }

    @Test
    public void testRecordNetworkRequest() {
        getEmbrace().recordNetworkRequest(null);
        assertError("recordNetworkRequest");
    }

    @Test
    public void testLogInfo() {
        getEmbrace().logInfo(null);
        assertError("logInfo");
    }

    @Test
    public void testLogWarning() {
        getEmbrace().logWarning(null);
        assertError("logWarning");
    }

    @Test
    public void testLogError() {
        getEmbrace().logError(NULL_STRING);
        assertError("logError");
    }

    @Test
    public void testLogException() {
        getEmbrace().logException(null);
        assertError("logException");
    }

    @Test
    public void testLogException2Parameters() {
        getEmbrace().logException(null, Severity.ERROR);
        assertError("logException");
        getEmbrace().logException(EXCEPTION, null);
        assertError("logException");
    }

    @Test
    public void testLogException3Parameters() {
        getEmbrace().logException(null, Severity.ERROR, null);
        assertError("logException");
        getEmbrace().logException(EXCEPTION, null, null);
        assertError("logException");
    }

    @Test
    public void testLogException4Parameters() {
        getEmbrace().logException(null, Severity.ERROR, null, null);
        assertError("logException");
        getEmbrace().logException(EXCEPTION, null, null, null);
        assertError("logException");
    }

    @Test
    public void testLogCustomStacktrace() {
        getEmbrace().logCustomStacktrace(null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testLogCustomStacktrace2Parameters() {
        getEmbrace().logCustomStacktrace(null, Severity.ERROR);
        assertError("logCustomStacktrace");
        getEmbrace().logCustomStacktrace(new StackTraceElement[0], null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testLogCustomStacktrace3Parameters() {
        getEmbrace().logCustomStacktrace(null, Severity.ERROR, null);
        assertError("logCustomStacktrace");
        getEmbrace().logCustomStacktrace(new StackTraceElement[0], null, null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testLogCustomStacktrace4Parameters() {
        getEmbrace().logCustomStacktrace(null, Severity.ERROR, null, null);
        assertError("logCustomStacktrace");
        getEmbrace().logCustomStacktrace(new StackTraceElement[0], null, null, null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testStartView() {
        getEmbrace().startView(null);
        assertError("startView");
    }

    @Test
    public void testEndView() {
        getEmbrace().endView(null);
        assertError("endView");
    }

    @Test
    public void testAddBreadcrumb() {
        getEmbrace().addBreadcrumb(null);
        assertError("addBreadcrumb");
    }

    @Test
    public void testLogPushNotification() {
        getEmbrace().logPushNotification(null, null, null, null, null, null, true, true);
        assertError("logPushNotification");
        getEmbrace().logPushNotification(null, null, null, null, null, 1, null, true);
        assertError("logPushNotification");
        getEmbrace().logPushNotification(null, null, null, null, null, 1, true, null);
        assertError("logPushNotification");
    }

    @Test
    public void testTrackWebViewPerformanceWithStringMessage() {
        getEmbrace().trackWebViewPerformance(null, "message");
        assertError("trackWebViewPerformance");
        getEmbrace().trackWebViewPerformance("tag", NULL_STRING);
        assertError("trackWebViewPerformance");
    }

    @Test
    public void testTrackWebViewPerformanceWithConsoleMessage() {
        getEmbrace().trackWebViewPerformance(null, new ConsoleMessage("message", "id", 1, ConsoleMessage.MessageLevel.DEBUG));
        assertError("trackWebViewPerformance");
        getEmbrace().trackWebViewPerformance("tag", (ConsoleMessage) null);
        assertError("trackWebViewPerformance");
    }

    @Test
    public void testCreateSpan() {
        assertNull(getEmbrace().createSpan(null));
        assertError("createSpan");
    }

    @Test
    public void testCreateSpanWithParent() {
        assertNull(getEmbrace().createSpan(null, null));
        assertError("createSpan");
    }

    @Test
    public void testStartSpan() {
        assertNull(getEmbrace().startSpan(null));
        assertError("startSpan");
    }

    @Test
    public void testStartSpanWithParent() {
        assertNull(getEmbrace().startSpan(null, null));
        assertError("startSpan");
    }

    @Test
    public void testStartSpanWithParentAndStartTime() {
        assertNull(getEmbrace().startSpan(null, null, null));
        assertError("startSpan");
    }

    @Test
    public void testRecordSpan() {
        assertTrue(getEmbrace().recordSpan(null, () -> true));
        assertError("recordSpan");
        assertNull(getEmbrace().recordSpan("test-span", null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordSpanWithAttributesAndEvents() {
        assertTrue(getEmbrace().recordSpan(null, null, null, () -> true));
        assertError("recordSpan");
        assertNull(getEmbrace().recordSpan("test-span", null, null, null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordSpanWithParent() {
        assertTrue(getEmbrace().recordSpan(null, null, () -> true));
        assertError("recordSpan");
        assertNull(getEmbrace().recordSpan("test-span", null, null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordSpanWithParentAttributesAndEvents() {
        assertTrue(getEmbrace().recordSpan(null, null, null, null, () -> true));
        assertError("recordSpan");
        assertNull(getEmbrace().recordSpan("test-span", null, null, null, null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordCompletedSpan() {
        assertFalse(getEmbrace().recordCompletedSpan(null, 0, 1));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithErrorCode() {
        assertFalse(getEmbrace().recordCompletedSpan(null, 0, 1, (ErrorCode) null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithParent() {
        assertFalse(getEmbrace().recordCompletedSpan(null, 0, 1, (EmbraceSpan) null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithErrorCodeAndParent() {
        assertFalse(getEmbrace().recordCompletedSpan(null, 0, 1, (ErrorCode) null, null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithAttributesAndEvents() {
        assertFalse(getEmbrace().recordCompletedSpan(null, 0, 1, (Map<String, String>) null, null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithEverything() {
        assertFalse(getEmbrace().recordCompletedSpan(null, 0, 1, null, null, null, null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testGetSpan() {
        assertNull(getEmbrace().getSpan(null));
        assertError("getSpan");
    }

    @Test
    public void testAddSpanExporter() {
        getEmbrace().addSpanExporter(null);
        assertError("addSpanExporter");
    }

    private void assertError(@NonNull String functionName) {
        assertInternalErrorLogged(
            testRule.bootstrapper,
            IllegalArgumentException.class.getCanonicalName(),
            functionName + NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE
        );
    }
}
