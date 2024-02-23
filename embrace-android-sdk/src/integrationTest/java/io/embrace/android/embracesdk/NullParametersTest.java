package io.embrace.android.embracesdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static io.embrace.android.embracesdk.Embrace.NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE;
import static io.embrace.android.embracesdk.assertions.InternalErrorAssertionsKt.assertInternalErrorLogged;

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
    private final Embrace embrace = testRule.getEmbrace();

    @Before
    public void before() {
        assertTrue(embrace.isStarted());
    }

    @Test
    public void testAddSessionProperty() {
        embrace.addSessionProperty(null, "value", false);
        assertError("addSessionProperty");
        embrace.addSessionProperty("key", null, false);
        assertError("addSessionProperty");
    }

    @Test
    public void testAddUserPersona() {
        embrace.addUserPersona(null);
        assertError("addUserPersona");
    }

    @Test
    public void testClearUserPersona() {
        embrace.clearUserPersona(null);
        assertError("clearUserPersona");
    }

    @Test
    public void testRemoveSessionProperty() {
        embrace.removeSessionProperty(null);
        assertError("removeSessionProperty");
    }

    @Test
    public void testStartMoment1Parameter() {
        embrace.startMoment(null);
        assertError("startMoment");
    }

    @Test
    public void testStartMoment2Parameters() {
        embrace.startMoment(null, null);
        assertError("startMoment");
    }

    @Test
    public void testStartMoment3ParametersAllowProperties() {
        embrace.startMoment(null, null, null);
        assertError("startMoment");
    }

    @Test
    public void testEndMoment1Parameter() {
        embrace.endMoment(null);
        assertError("endMoment");
    }

    @Test
    public void testEndMoment2ParametersCustomIdentifier() {
        embrace.endMoment(null, NULL_STRING);
        assertError("endMoment");
    }

    @Test
    public void testEndMoment2ParametersAllowProperties() {
        embrace.endMoment(null, NULL_STRING);
        assertError("endMoment");
    }

    @Test
    public void testEndMoment3Parameters() {
        embrace.endMoment(null, NULL_STRING, null);
        assertError("endMoment");
    }

    @Test
    public void testEndAppStartup() {
        embrace.endAppStartup(null);
        assertError("endAppStartup");
    }

    @Test
    public void testRecordNetworkRequest() {
        embrace.recordNetworkRequest(null);
        assertError("recordNetworkRequest");
    }

    @Test
    public void testLogInfo() {
        embrace.logInfo(null);
        assertError("logInfo");
    }

    @Test
    public void testLogWarning() {
        embrace.logWarning(null);
        assertError("logWarning");
    }

    @Test
    public void testLogError() {
        embrace.logError(NULL_STRING);
        assertError("logError");
    }

    @Test
    public void testLogException() {
        embrace.logException(null);
        assertError("logException");
    }

    @Test
    public void testLogException2Parameters() {
        embrace.logException(null, Severity.ERROR);
        assertError("logException");
        embrace.logException(EXCEPTION, null);
        assertError("logException");
    }

    @Test
    public void testLogException3Parameters() {
        embrace.logException(null, Severity.ERROR, null);
        assertError("logException");
        embrace.logException(EXCEPTION, null, null);
        assertError("logException");
    }

    @Test
    public void testLogException4Parameters() {
        embrace.logException(null, Severity.ERROR, null, null);
        assertError("logException");
        embrace.logException(EXCEPTION, null, null, null);
        assertError("logException");
    }

    @Test
    public void testLogCustomStacktrace() {
        embrace.logCustomStacktrace(null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testLogCustomStacktrace2Parameters() {
        embrace.logCustomStacktrace(null, Severity.ERROR);
        assertError("logCustomStacktrace");
        embrace.logCustomStacktrace(new StackTraceElement[0], null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testLogCustomStacktrace3Parameters() {
        embrace.logCustomStacktrace(null, Severity.ERROR, null);
        assertError("logCustomStacktrace");
        embrace.logCustomStacktrace(new StackTraceElement[0], null, null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testLogCustomStacktrace4Parameters() {
        embrace.logCustomStacktrace(null, Severity.ERROR, null, null);
        assertError("logCustomStacktrace");
        embrace.logCustomStacktrace(new StackTraceElement[0], null, null, null);
        assertError("logCustomStacktrace");
    }

    @Test
    public void testStartView() {
        embrace.startView(null);
        assertError("startView");
    }

    @Test
    public void testEndView() {
        embrace.endView(null);
        assertError("endView");
    }

    @Test
    public void testAddBreadcrumb() {
        embrace.addBreadcrumb(null);
        assertError("addBreadcrumb");
    }

    @Test
    public void testLogPushNotification() {
        embrace.logPushNotification(null, null, null, null, null, null, true, true);
        assertError("logPushNotification");
        embrace.logPushNotification(null, null, null, null, null, 1, null, true);
        assertError("logPushNotification");
        embrace.logPushNotification(null, null, null, null, null, 1, true, null);
        assertError("logPushNotification");
    }

    @Test
    public void testTrackWebViewPerformanceWithStringMessage() {
        embrace.trackWebViewPerformance(null, "message");
        assertError("trackWebViewPerformance");
        embrace.trackWebViewPerformance("tag", NULL_STRING);
        assertError("trackWebViewPerformance");
    }

    @Test
    public void testTrackWebViewPerformanceWithConsoleMessage() {
        embrace.trackWebViewPerformance(null, new ConsoleMessage("message", "id", 1, ConsoleMessage.MessageLevel.DEBUG));
        assertError("trackWebViewPerformance");
        embrace.trackWebViewPerformance("tag", (ConsoleMessage) null);
        assertError("trackWebViewPerformance");
    }

    @Test
    public void testCreateSpan() {
        assertNull(embrace.createSpan(null));
        assertError("createSpan");
    }

    @Test
    public void testCreateSpanWithParent() {
        assertNull(embrace.createSpan(null, null));
        assertError("createSpan");
    }

    @Test
    public void testStartSpan() {
        assertNull(embrace.startSpan(null));
        assertError("startSpan");
    }

    @Test
    public void testStartSpanWithParent() {
        assertNull(embrace.startSpan(null, null));
        assertError("startSpan");
    }

    @Test
    public void testStartSpanWithParentAndStartTime() {
        assertNull(embrace.startSpan(null, null, null));
        assertError("startSpan");
    }

    @Test
    public void testRecordSpan() {
        assertTrue(embrace.recordSpan(null, () -> true));
        assertError("recordSpan");
        assertNull(embrace.recordSpan("test-span", null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordSpanWithAttributesAndEvents() {
        assertTrue(embrace.recordSpan(null, null, null, () -> true));
        assertError("recordSpan");
        assertNull(embrace.recordSpan("test-span", null, null, null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordSpanWithParent() {
        assertTrue(embrace.recordSpan(null, null, () -> true));
        assertError("recordSpan");
        assertNull(embrace.recordSpan("test-span", null, null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordSpanWithParentAttributesAndEvents() {
        assertTrue(embrace.recordSpan(null, null, null, null, () -> true));
        assertError("recordSpan");
        assertNull(embrace.recordSpan("test-span", null, null, null, null));
        assertError("recordSpan");
    }

    @Test
    public void testRecordCompletedSpan() {
        assertFalse(embrace.recordCompletedSpan(null, 0, 1));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithErrorCode() {
        assertFalse(embrace.recordCompletedSpan(null, 0, 1, (ErrorCode) null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithParent() {
        assertFalse(embrace.recordCompletedSpan(null, 0, 1, (EmbraceSpan) null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithErrorCodeAndParent() {
        assertFalse(embrace.recordCompletedSpan(null, 0, 1, (ErrorCode) null, null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithAttributesAndEvents() {
        assertFalse(embrace.recordCompletedSpan(null, 0, 1, (Map<String, String>) null, null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testRecordCompletedSpanWithEverything() {
        assertFalse(embrace.recordCompletedSpan(null, 0, 1, null, null, null, null));
        assertError("recordCompletedSpan");
    }

    @Test
    public void testGetSpan() {
        assertNull(embrace.getSpan(null));
        assertError("getSpan");
    }

    @Test
    public void testAddSpanExporter() {
        embrace.addSpanExporter(null);
        assertError("addSpanExporter");
    }

    private void assertError(@NonNull String functionName) {
        assertInternalErrorLogged(
            IntegrationTestRuleExtensionsKt.internalErrorService().getCurrentExceptionError(),
            IllegalArgumentException.class.getCanonicalName(),
            functionName + NULL_PARAMETER_ERROR_MESSAGE_TEMPLATE,
            IntegrationTestRule.DEFAULT_SDK_START_TIME_MS
        );
        IntegrationTestRuleExtensionsKt.internalErrorService().resetExceptionErrorObject();
    }
}
