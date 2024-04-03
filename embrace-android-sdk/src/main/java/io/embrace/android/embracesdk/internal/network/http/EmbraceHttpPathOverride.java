package io.embrace.android.embracesdk.internal.network.http;

import static io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import io.embrace.android.embracesdk.annotation.InternalApi;

@InternalApi
public class EmbraceHttpPathOverride {
    /**
     * Header used to override the URL's relative path
     */
    protected static final String PATH_OVERRIDE = "x-emb-path";

    /**
     * Max length of relative path override
     */
    private static final Integer RELATIVE_PATH_MAX_LENGTH = 1024;

    /**
     * Allowed characters in relative path
     * As per https://tools.ietf.org/html/rfc3986#section-2 and https://stackoverflow.com/a/1547940.
     * Removed certain characters we do not want present: ?#
     */
    private static final Pattern RELATIVE_PATH_PATTERN = Pattern.compile("[A-Za-z0-9-._~:/\\[\\]@!$&'()*+,;=]+");

    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    @NonNull
    public static String getURLString(@NonNull HttpPathOverrideRequest request) {
        return getURLString(request, request.getHeaderByName(PATH_OVERRIDE));
    }

    @SuppressWarnings("AbbreviationAsWordInNameCheck")
    @InternalApi
    @NonNull
    public static String getURLString(@NonNull HttpPathOverrideRequest request, @Nullable String pathOverride) {
        String url;
        try {
            if (pathOverride != null && validatePathOverride(pathOverride)) {
                url = request.getOverriddenURL(pathOverride);
            } else {
                url = request.getURLString();
            }
        } catch (Exception e) {
            url = request.getURLString();
        }
        return url;
    }

    private static Boolean validatePathOverride(String path) {
        if (path == null) {
            logger.logError("URL relative path cannot be null");
            return false;
        }
        if (path.isEmpty()) {
            logger.logError("Relative path must have non-zero length");
            return false;
        }
        if (path.length() > RELATIVE_PATH_MAX_LENGTH) {
            logger.logError(String.format(Locale.US,
                    "Relative path %s is greater than the maximum allowed length of %d. It will be ignored",
                    path, RELATIVE_PATH_MAX_LENGTH));
            return false;
        }
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(path)) {
            logger.logError("Relative path must not contain unicode " +
                    "characters. Relative path " + path + " will be ignored.");
            return false;
        }
        if (!path.startsWith("/")) {
            logger.logError("Relative path must start with a /");
            return false;
        }
        if (!RELATIVE_PATH_PATTERN.matcher(path).matches()) {
            logger.logError("Relative path contains invalid chars. " +
                    "Relative path " + path + " will be ignored.");
            return false;
        }

        return true;
    }
}