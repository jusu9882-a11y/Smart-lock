package dev.vanta.smartlock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AppGuardGateActivity : FragmentActivity() {
    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (target.isNullOrBlank() || target !in AppGuardStore.protectedPackages(this)) {
            AppGuardStore.setGateActive(this, false)
            finish()
            return
        }

        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

        if (BiometricManager.from(this).canAuthenticate(authenticators) !=
            BiometricManager.BIOMETRIC_SUCCESS) {
            leaveProtectedApp()
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock protected app")
            .setSubtitle("Authenticate with your phone's security")
            .setAllowedAuthenticators(authenticators)
            .build()

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppGuardStore.setAuthorizedPackage(this@AppGuardGateActivity, target)
                    AppGuardStore.setGateActive(this@AppGuardGateActivity, false)
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    leaveProtectedApp()
                }

                override fun onAuthenticationFailed() {
                    // Keep the system prompt active for another attempt.
                }
            }
        )
        prompt.authenticate(promptInfo)
    }

    private fun leaveProtectedApp() {
        AppGuardStore.setGateActive(this, false)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
