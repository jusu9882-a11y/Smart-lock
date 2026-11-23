package dev.vanta.smartlock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppGuardAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == packageNameOfVanta()) {
            if (!AppGuardStore.isGateActive(this)) {
                AppGuardStore.setAuthorizedPackage(this, null)
            }
            return
        }

        val protected = AppGuardStore.protectedPackages(this)
        val authorized = AppGuardStore.authorizedPackage(this)
        if (authorized == packageName) return

        if (authorized != null) AppGuardStore.setAuthorizedPackage(this, null)
        if (packageName !in protected || AppGuardStore.isGateActive(this)) return

        AppGuardStore.setGateActive(this, true)
        val intent = Intent(this, AppGuardGateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AppGuardGateActivity.EXTRA_TARGET_PACKAGE, packageName)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            AppGuardStore.setGateActive(this, false)
        }
    }

    private fun packageNameOfVanta(): String = applicationContext.packageName

    override fun onInterrupt() {
        AppGuardStore.setGateActive(this, false)
    }
}
