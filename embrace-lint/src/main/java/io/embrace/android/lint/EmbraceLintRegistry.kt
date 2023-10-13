package io.embrace.android.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Container for all the lint issues that this module should scan for.
 */
@Suppress("UnstableApiUsage")
class EmbraceLintRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(EmbracePublicApiPackageRule.ISSUE)

    override val api: Int = CURRENT_API

    override val vendor: Vendor = Vendor(
        vendorName = "Embrace",
        feedbackUrl = "https://embrace.io",
        contact = "support@embrace.io"
    )
}
