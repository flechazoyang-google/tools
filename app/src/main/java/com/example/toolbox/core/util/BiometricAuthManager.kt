package com.example.toolbox.core.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps AndroidX [BiometricPrompt] for use in Jetpack Compose.
 *
 * Example usage:
 * ```kotlin
 * val authManager = remember { BiometricAuthManager(context) }
 * authManager.authenticate(
 *     title = "解锁密码箱",
 *     subtitle = "验证指纹以解锁",
 *     onSuccess = { viewModel.unlockWithBiometric() },
 *     onError = { /* handle */ },
 * )
 * ```
 */
class BiometricAuthManager(private val context: Context) {

    /** Returns true if the device has biometric hardware and the user has enrolled. */
    val isAvailable: Boolean
        get() {
            val manager = BiometricManager.from(context)
            return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> true
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> false
                else -> false
            }
        }

    /**
     * Launches the system biometric prompt.
     *
     * @param title Dialog title.
     * @param subtitle Dialog subtitle (optional).
     * @param negativeButtonText Text for the cancel button. Default "取消".
     * @param onSuccess Called when authentication succeeds.
     * @param onError Called on error or cancellation with a user-facing message.
     */
    fun authenticate(
        title: String,
        subtitle: String? = null,
        negativeButtonText: String = "取消",
        onSuccess: () -> Unit,
        onError: (message: String) -> Unit,
    ) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            onError("当前上下文不支持生物识别")
            return
        }

        if (!isAvailable) {
            onError("设备不支持指纹识别或未录入指纹")
            return
        }

        val executor = ContextCompat.getMainExecutor(context)

        val callback = object : AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Silently ignore user cancellation (errorCode 13 = negative button pressed, 10 = cancelled)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("指纹识别失败，请重试")
            }
        }

        val promptInfoBuilder = PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeButtonText)

        if (subtitle != null) {
            promptInfoBuilder.setSubtitle(subtitle)
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfoBuilder.build())
    }
}
