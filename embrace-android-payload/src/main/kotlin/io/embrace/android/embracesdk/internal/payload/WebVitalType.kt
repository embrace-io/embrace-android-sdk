package io.embrace.android.embracesdk.internal.payload

/**
 * Web Core Vital type.
 *
 * FID = First Input Delay: Measures the delay between a user's interaction (such as tapping a button) and the browser's response.
 * LCP = Largest Contentful Paint: Measures the time it takes for the largest content element to become visible to the user.
 * CLS = Cumulative Layout Shift: Assesses the visual stability of the page by measuring unexpected layout shifts during loading.
 * FCP = First Contentful Paint: Indicates the time it takes for the first content element to appear on the screen.
 *
 */
enum class WebVitalType {
    FID, LCP, CLS, FCP
}
