package com.example.modiriatmaliman

import android.app.AlertDialog
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AppSecurity {
    fun requireFingerprint(activity: FragmentActivity, onSuccess: () -> Unit) {
        val manager = BiometricManager.from(activity)
        val state = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (state != BiometricManager.BIOMETRIC_SUCCESS) {
            val message = when (state) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "ابتدا اثر انگشت را در تنظیمات امنیتی گوشی ثبت کنید."
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "این گوشی حسگر اثر انگشت سازگار ندارد."
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "حسگر اثر انگشت فعلاً در دسترس نیست."
                else -> "امکان استفاده از اثر انگشت وجود ندارد. تنظیمات امنیتی گوشی را بررسی کنید."
            }
            AlertDialog.Builder(activity)
                .setTitle("ورود با اثر انگشت")
                .setMessage(message)
                .setPositiveButton("خروج") { _, _ -> activity.finish() }
                .setCancelable(false)
                .show()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(activity, "ورود انجام نشد: " + errString, Toast.LENGTH_LONG).show()
                    activity.finish()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود امن به مدیریت مالی من")
            .setSubtitle("برای ورود، حسگر اثر انگشت را لمس کنید")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("انصراف")
            .build()
        prompt.authenticate(info)
    }
}
