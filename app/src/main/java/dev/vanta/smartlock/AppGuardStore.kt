package dev.vanta.smartlock

import android.content.Context

internal object AppGuardStore {
    private const val PREFS = "vanta_app_guard"
    private const val KEY_PROTECTED = "protected_packages"
    private const val KEY_AUTHORIZED = "authorized_package"
    private const val KEY_GATE_ACTIVE = "gate_active"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun protectedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PROTECTED, emptySet())?.toSet() ?: emptySet()

    fun setProtectedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_PROTECTED, packages.toSet()).apply()
    }

    fun authorizedPackage(context: Context): String? =
        prefs(context).getString(KEY_AUTHORIZED, null)

    fun setAuthorizedPackage(context: Context, packageName: String?) {
        prefs(context).edit().putString(KEY_AUTHORIZED, packageName).apply()
    }

    fun isGateActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GATE_ACTIVE, false)

    fun setGateActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_GATE_ACTIVE, active).apply()
    }
}
