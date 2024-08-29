package io.embrace.android.embracesdk.internal.anr.sigquit

// IMPORTANT: This class is referenced by emb_anr_manager.c. Move or rename both at the same time, or it will break.
public class EmbraceSigquitNdkDelegate : SigquitNdkDelegate {
    external override fun installGoogleAnrHandler(googleThreadId: Int): Int
}

internal interface SigquitNdkDelegate {
    fun installGoogleAnrHandler(googleThreadId: Int): Int
}
