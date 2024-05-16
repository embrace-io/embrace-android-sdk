package io.embrace.android.embracesdk.okhttp3.swazzle.callback.okhttp3;

import java.util.List;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3ApplicationInterceptor;
import io.embrace.android.embracesdk.okhttp3.EmbraceOkHttp3NetworkInterceptor;
import okhttp3.Interceptor;

/**
 * Callback hooks for the okhttp3.OkHttpClient class.
 */
@InternalApi
public final class OkHttpClient {

    private OkHttpClient() {
    }

    @InternalApi
    public static final class Builder {

        private Builder() {
        }

        /**
         * As there was a way to clear the injected interceptors during the OkHttpClient
         * initialization using the builder, we are hooking the build method as well, instead of
         * just the Builder constructor.
         * <p>
         * Once the build method is called, OkHTTP mushes everything and returns the OkHttpClient
         * instance, where the developer has no way to alter any of the interceptors during or
         * after this point, without having to rebuild the client.
         */
        @SuppressWarnings("MethodNameCheck")
        public static void _preBuild(okhttp3.OkHttpClient.Builder thiz) {
            addEmbraceInterceptors(thiz);
        }

        @SuppressWarnings("MethodNameCheck")
        public static void _constructorOnPostBody(okhttp3.OkHttpClient.Builder thiz) {
            addEmbraceInterceptors(thiz);
        }

        /**
         * Adds embrace interceptors if they don't exist already to the OkHTTPClient provided.
         *
         * @param thiz the OkHttpClient builder in matter.
         */
        private static void addEmbraceInterceptors(okhttp3.OkHttpClient.Builder thiz) {
            try {
                addInterceptor(thiz.interceptors(), new EmbraceOkHttp3ApplicationInterceptor());
                addInterceptor(thiz.networkInterceptors(), new EmbraceOkHttp3NetworkInterceptor());
            } catch (NoSuchMethodError exception) {
                // The customer may be overwriting OkHttpClient with their own implementation, and some of the
                // methods we use are missing.
                logInternalError("Altered OkHttpClient implementation, could not add OkHttp interceptor. ", exception);
            } catch (Exception exception) {
                logInternalError("Could not add OkHttp interceptor. ", exception);
            }
        }

        /**
         * Adds the interceptor to the interceptors list if it doesn't exist already.
         *
         * @param interceptors list of existing interceptors.
         * @param interceptor  interceptor to be added.
         */
        private static void addInterceptor(List<Interceptor> interceptors,
                                           Interceptor interceptor) {
            if (interceptors != null && !containsInstance(interceptors, interceptor.getClass())) {
                interceptors.add(0, interceptor);
            }
        }

        /**
         * Checks for the existence in the elements list of an instance of the same class as the
         * one provided in the arguments.
         *
         * @param elementsList list of elements.
         * @param clazz        class of the instance that's being checked if exists.
         * @return if an instance of the provided class exists in the list of elements.
         */
        private static <T> boolean containsInstance(List<T> elementsList,
                                                    Class<? extends T> clazz) {
            for (T classInstance : elementsList) {
                if (clazz.isInstance(classInstance)) {
                    return true;
                }
            }
            return false;
        }

        private static void logInternalError(String message, Throwable throwable) {
            Embrace.getInstance().getInternalInterface().logInternalError(throwable);
        }
    }
}
