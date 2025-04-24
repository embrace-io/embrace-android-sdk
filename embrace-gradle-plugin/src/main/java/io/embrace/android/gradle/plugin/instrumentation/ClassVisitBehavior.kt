package io.embrace.android.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.embrace.android.gradle.plugin.instrumentation.strategy.ClassVisitStrategy

internal class ClassVisitBehavior(private val params: BytecodeInstrumentationParams) {

    fun shouldInstrumentWebview(ctx: ClassContext): Boolean {
        return params.shouldInstrumentWebview.get() &&
            ClassVisitStrategy.MatchSuperClassName("android.webkit.WebViewClient").shouldVisit(ctx)
    }

    fun shouldInstrumentOkHttp(ctx: ClassContext): Boolean {
        return params.shouldInstrumentOkHttp.get() &&
            ClassVisitStrategy.MatchClassName("okhttp3.OkHttpClient\$Builder").shouldVisit(ctx)
    }

    fun shouldInstrumentOnClick(ctx: ClassContext): Boolean {
        return params.shouldInstrumentOnClick.get() && ClassVisitStrategy.Exhaustive.shouldVisit(ctx)
    }

    fun shouldInstrumentOnLongClick(ctx: ClassContext): Boolean {
        return params.shouldInstrumentOnLongClick.get() && ClassVisitStrategy.Exhaustive.shouldVisit(ctx)
    }
}
