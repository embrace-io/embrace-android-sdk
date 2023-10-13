package io.embrace.android.embracesdk

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ChangedPackages
import android.content.pm.FeatureInfo
import android.content.pm.InstrumentationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PermissionGroupInfo
import android.content.pm.PermissionInfo
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.content.pm.SharedLibraryInfo
import android.content.pm.VersionedPackage
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.UserHandle

internal class FakePackageManager(private val embraceContext: EmbraceContext) : PackageManager() {

    override fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
        return PackageInfo().apply {
            this.packageName = embraceContext.packageName
            versionName = "1.1.2"
            versionCode = 5
        }
    }

    override fun getPackageInfo(versionedPackage: VersionedPackage, flags: Int): PackageInfo {
        throw UnsupportedOperationException()
    }

    override fun currentToCanonicalPackageNames(packageNames: Array<out String>): Array<String> {
        throw UnsupportedOperationException()
    }

    override fun canonicalToCurrentPackageNames(packageNames: Array<out String>): Array<String> {
        throw UnsupportedOperationException()
    }

    override fun getLaunchIntentForPackage(packageName: String): Intent? {
        throw UnsupportedOperationException()
    }

    override fun getLeanbackLaunchIntentForPackage(packageName: String): Intent? {
        throw UnsupportedOperationException()
    }

    override fun getPackageGids(packageName: String): IntArray {
        throw UnsupportedOperationException()
    }

    override fun getPackageGids(packageName: String, flags: Int): IntArray {
        throw UnsupportedOperationException()
    }

    override fun getPackageUid(packageName: String, flags: Int): Int {
        throw UnsupportedOperationException()
    }

    override fun getPermissionInfo(permName: String, flags: Int): PermissionInfo {
        throw UnsupportedOperationException()
    }

    override fun queryPermissionsByGroup(
        permissionGroup: String?,
        flags: Int
    ): MutableList<PermissionInfo> {
        throw UnsupportedOperationException()
    }

    override fun getPermissionGroupInfo(groupName: String, flags: Int): PermissionGroupInfo {
        throw UnsupportedOperationException()
    }

    override fun getAllPermissionGroups(flags: Int): MutableList<PermissionGroupInfo> {
        throw UnsupportedOperationException()
    }

    override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo {
        throw UnsupportedOperationException()
    }

    override fun getActivityInfo(component: ComponentName, flags: Int): ActivityInfo {
        throw UnsupportedOperationException()
    }

    override fun getReceiverInfo(component: ComponentName, flags: Int): ActivityInfo {
        throw UnsupportedOperationException()
    }

    override fun getServiceInfo(component: ComponentName, flags: Int): ServiceInfo {
        throw UnsupportedOperationException()
    }

    override fun getProviderInfo(component: ComponentName, flags: Int): ProviderInfo {
        throw UnsupportedOperationException()
    }

    override fun getInstalledPackages(flags: Int): MutableList<PackageInfo> {
        throw UnsupportedOperationException()
    }

    override fun getPackagesHoldingPermissions(
        permissions: Array<out String>,
        flags: Int
    ): MutableList<PackageInfo> {
        throw UnsupportedOperationException()
    }

    override fun checkPermission(permName: String, packageName: String): Int {
        throw UnsupportedOperationException()
    }

    override fun isPermissionRevokedByPolicy(permName: String, packageName: String): Boolean {
        throw UnsupportedOperationException()
    }

    override fun addPermission(info: PermissionInfo): Boolean {
        throw UnsupportedOperationException()
    }

    override fun addPermissionAsync(info: PermissionInfo): Boolean {
        throw UnsupportedOperationException()
    }

    override fun removePermission(permName: String) {
        throw UnsupportedOperationException()
    }

    override fun checkSignatures(packageName1: String, packageName2: String): Int {
        throw UnsupportedOperationException()
    }

    override fun checkSignatures(uid1: Int, uid2: Int): Int {
        throw UnsupportedOperationException()
    }

    override fun getPackagesForUid(uid: Int): Array<String>? {
        throw UnsupportedOperationException()
    }

    override fun getNameForUid(uid: Int): String? {
        throw UnsupportedOperationException()
    }

    override fun getInstalledApplications(flags: Int): MutableList<ApplicationInfo> {
        throw UnsupportedOperationException()
    }

    override fun isInstantApp(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isInstantApp(packageName: String): Boolean {
        throw UnsupportedOperationException()
    }

    override fun getInstantAppCookieMaxBytes(): Int {
        throw UnsupportedOperationException()
    }

    override fun getInstantAppCookie(): ByteArray {
        throw UnsupportedOperationException()
    }

    override fun clearInstantAppCookie() {
        throw UnsupportedOperationException()
    }

    override fun updateInstantAppCookie(cookie: ByteArray?) {
        throw UnsupportedOperationException()
    }

    override fun getSystemSharedLibraryNames(): Array<String>? {
        throw UnsupportedOperationException()
    }

    override fun getSharedLibraries(flags: Int): MutableList<SharedLibraryInfo> {
        throw UnsupportedOperationException()
    }

    override fun getChangedPackages(sequenceNumber: Int): ChangedPackages? {
        throw UnsupportedOperationException()
    }

    override fun getSystemAvailableFeatures(): Array<FeatureInfo> {
        throw UnsupportedOperationException()
    }

    override fun hasSystemFeature(featureName: String): Boolean {
        throw UnsupportedOperationException()
    }

    override fun hasSystemFeature(featureName: String, version: Int): Boolean {
        throw UnsupportedOperationException()
    }

    override fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? {
        throw UnsupportedOperationException()
    }

    override fun queryIntentActivities(intent: Intent, flags: Int): MutableList<ResolveInfo> {
        throw UnsupportedOperationException()
    }

    override fun queryIntentActivityOptions(
        caller: ComponentName?,
        specifics: Array<out Intent>?,
        intent: Intent,
        flags: Int
    ): MutableList<ResolveInfo> {
        throw UnsupportedOperationException()
    }

    override fun queryBroadcastReceivers(intent: Intent, flags: Int): MutableList<ResolveInfo> {
        throw UnsupportedOperationException()
    }

    override fun resolveService(intent: Intent, flags: Int): ResolveInfo? {
        throw UnsupportedOperationException()
    }

    override fun queryIntentServices(intent: Intent, flags: Int): MutableList<ResolveInfo> {
        throw UnsupportedOperationException()
    }

    override fun queryIntentContentProviders(intent: Intent, flags: Int): MutableList<ResolveInfo> {
        throw UnsupportedOperationException()
    }

    override fun resolveContentProvider(authority: String, flags: Int): ProviderInfo? {
        throw UnsupportedOperationException()
    }

    override fun queryContentProviders(
        processName: String?,
        uid: Int,
        flags: Int
    ): MutableList<ProviderInfo> {
        throw UnsupportedOperationException()
    }

    override fun getInstrumentationInfo(className: ComponentName, flags: Int): InstrumentationInfo {
        throw UnsupportedOperationException()
    }

    override fun queryInstrumentation(
        targetPackage: String,
        flags: Int
    ): MutableList<InstrumentationInfo> {
        throw UnsupportedOperationException()
    }

    override fun getDrawable(
        packageName: String,
        resid: Int,
        appInfo: ApplicationInfo?
    ): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getActivityIcon(activityName: ComponentName): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getActivityIcon(intent: Intent): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getActivityBanner(activityName: ComponentName): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getActivityBanner(intent: Intent): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getDefaultActivityIcon(): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getApplicationIcon(info: ApplicationInfo): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getApplicationIcon(packageName: String): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getApplicationBanner(info: ApplicationInfo): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getApplicationBanner(packageName: String): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getActivityLogo(activityName: ComponentName): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getActivityLogo(intent: Intent): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getApplicationLogo(info: ApplicationInfo): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getApplicationLogo(packageName: String): Drawable? {
        throw UnsupportedOperationException()
    }

    override fun getUserBadgedIcon(drawable: Drawable, user: UserHandle): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getUserBadgedDrawableForDensity(
        drawable: Drawable,
        user: UserHandle,
        badgeLocation: Rect?,
        badgeDensity: Int
    ): Drawable {
        throw UnsupportedOperationException()
    }

    override fun getUserBadgedLabel(label: CharSequence, user: UserHandle): CharSequence {
        throw UnsupportedOperationException()
    }

    override fun getText(
        packageName: String,
        resid: Int,
        appInfo: ApplicationInfo?
    ): CharSequence? {
        throw UnsupportedOperationException()
    }

    override fun getXml(
        packageName: String,
        resid: Int,
        appInfo: ApplicationInfo?
    ): XmlResourceParser? {
        throw UnsupportedOperationException()
    }

    override fun getApplicationLabel(info: ApplicationInfo): CharSequence {
        throw UnsupportedOperationException()
    }

    override fun getResourcesForActivity(activityName: ComponentName): Resources {
        throw UnsupportedOperationException()
    }

    override fun getResourcesForApplication(app: ApplicationInfo): Resources {
        throw UnsupportedOperationException()
    }

    override fun getResourcesForApplication(packageName: String): Resources {
        throw UnsupportedOperationException()
    }

    override fun verifyPendingInstall(id: Int, verificationCode: Int) {
        throw UnsupportedOperationException()
    }

    override fun extendVerificationTimeout(
        id: Int,
        verificationCodeAtTimeout: Int,
        millisecondsToDelay: Long
    ) {
        throw UnsupportedOperationException()
    }

    override fun setInstallerPackageName(targetPackage: String, installerPackageName: String?) {
        throw UnsupportedOperationException()
    }

    override fun getInstallerPackageName(packageName: String): String? {
        throw UnsupportedOperationException()
    }

    override fun addPackageToPreferred(packageName: String) {
        throw UnsupportedOperationException()
    }

    override fun removePackageFromPreferred(packageName: String) {
        throw UnsupportedOperationException()
    }

    override fun getPreferredPackages(flags: Int): MutableList<PackageInfo> {
        throw UnsupportedOperationException()
    }

    override fun addPreferredActivity(
        filter: IntentFilter,
        match: Int,
        set: Array<out ComponentName>?,
        activity: ComponentName
    ) {
        throw UnsupportedOperationException()
    }

    override fun clearPackagePreferredActivities(packageName: String) {
        throw UnsupportedOperationException()
    }

    override fun getPreferredActivities(
        outFilters: MutableList<IntentFilter>,
        outActivities: MutableList<ComponentName>,
        packageName: String?
    ): Int {
        throw UnsupportedOperationException()
    }

    override fun setComponentEnabledSetting(
        componentName: ComponentName,
        newState: Int,
        flags: Int
    ) {
        throw UnsupportedOperationException()
    }

    override fun getComponentEnabledSetting(componentName: ComponentName): Int {
        throw UnsupportedOperationException()
    }

    override fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int) {
        throw UnsupportedOperationException()
    }

    override fun getApplicationEnabledSetting(packageName: String): Int {
        throw UnsupportedOperationException()
    }

    override fun isSafeMode(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun setApplicationCategoryHint(packageName: String, categoryHint: Int) {
        throw UnsupportedOperationException()
    }

    override fun getPackageInstaller(): PackageInstaller {
        throw UnsupportedOperationException()
    }

    override fun canRequestPackageInstalls(): Boolean {
        throw UnsupportedOperationException()
    }
}
