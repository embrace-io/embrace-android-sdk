package io.embrace.android.embracesdk.retrofit.swazzle.callback.retrofit

import io.embrace.android.embracesdk.Embrace
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * This class wraps a [Converter.Factory] and records spans around the
 * serialization of retrofit request and response bodies.
 */
public class EmbraceTracedConverterFactory(
    private val delegate: Converter.Factory
) : Converter.Factory() {

    private class ResponseBodyTracedConverter<T>(
        private val delegate: Converter<ResponseBody, T>,
        private val url: String
    ) : Converter<ResponseBody, T> {

        override fun convert(value: ResponseBody): T? {
            var result: T? = null
            Embrace.getInstance().internalInterface.recordSpan(
                "Retrofit response body serialization",
                attributes = mapOf("url.full" to url)
            ) {
                result = delegate.convert(value)
            }
            return result
        }
    }

    private class RequestBodyTracedConverter<T>(
        private val delegate: Converter<T, RequestBody>,
        private val url: String
    ) : Converter<T, RequestBody> {

        override fun convert(value: T): RequestBody? {
            var result: RequestBody? = null
            Embrace.getInstance().internalInterface.recordSpan(
                "Retrofit request body serialization",
                attributes = mapOf("url.full" to url)
            ) {
                result = delegate.convert(value)
            }
            return result
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val delegateConverter = delegate.responseBodyConverter(type, annotations, retrofit)
            ?: return null
        return ResponseBodyTracedConverter(delegateConverter, retrofit.baseUrl().toString())
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val delegateConverter =
            delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
                ?: return null

        return RequestBodyTracedConverter(delegateConverter, retrofit.baseUrl().toString())
    }
}
