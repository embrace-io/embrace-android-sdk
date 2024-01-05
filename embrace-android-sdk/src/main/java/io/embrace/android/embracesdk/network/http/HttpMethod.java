package io.embrace.android.embracesdk.network.http;

import java.util.Locale;

/**
 * Enumeration of supported HTTP request methods.
 * <p>
 * This class is part of the Embrace Public API.
 */
public enum HttpMethod {
    GET,
    HEAD,
    POST,
    PUT,
    DELETE,
    CONNECT,
    OPTIONS,
    TRACE,
    PATCH;

    /**
     * Given the string representation of the HTTP request method, returns the corresponding HttpMethod enum.
     */
    public static HttpMethod fromString(String method) {
        if (method == null) {
            return null;
        }

        // We expect that the HTTP method will be specified in English so we forcibly use the US locale.
        switch (method.toUpperCase(Locale.US)) {
            case "GET":
                return HttpMethod.GET;
            case "HEAD":
                return HttpMethod.HEAD;
            case "POST":
                return HttpMethod.POST;
            case "PUT":
                return HttpMethod.PUT;
            case "DELETE":
                return HttpMethod.DELETE;
            case "CONNECT":
                return HttpMethod.CONNECT;
            case "OPTIONS":
                return HttpMethod.OPTIONS;
            case "TRACE":
                return HttpMethod.TRACE;
            case "PATCH":
                return HttpMethod.PATCH;
            default:
                return null;
        }
    }

    /**
     * Given the int representation of the HTTP request method, returns the corresponding HttpMethod enum.
     */
    public static HttpMethod fromInt(Integer method) {
        if (method == null) {
            return null;
        }

        switch (method) {
            case 1:
                return HttpMethod.GET;
            case 2:
                return HttpMethod.POST;
            case 3:
                return HttpMethod.PUT;
            case 4:
                return HttpMethod.DELETE;
            case 5:
                return HttpMethod.PATCH;
            default:
                return null;
        }
    }
}
