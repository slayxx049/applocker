package com.callguard.app

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class AuthActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appPackage = intent.getStringExtra(EXTRA_PACKAGE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(0xFFF5F7FB.toInt())
        }
        layout.addView(TextView(this).apply {
            text = "Verify your identity"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFF172033.toInt())
        })
        layout.addView(Button(this).apply {
            text = "Authenticate with fingerprint"
            isAllCaps = false
            setOnClickListener {
                if (appPackage == null) {
                    finish()
                } else if (BiometricManager.from(this@AuthActivity).canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                    ) == BiometricManager.BIOMETRIC_SUCCESS) {
                    val executor = Executor { command -> runOnUiThread(command) }
                    val prompt = BiometricPrompt(this@AuthActivity, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                getSharedPreferences(SecurityStore.PREFS, MODE_PRIVATE).edit()
                                    .putLong("allowed_until_$appPackage", System.currentTimeMillis() + 60_000L)
                                    .apply()
                                finish()
                            }
                        })
                    prompt.authenticate(
                        BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock protected app")
                            .setSubtitle("Verify with your enrolled fingerprint")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            .setNegativeButtonText("Cancel")
                            .build()
                    )
                }
            }
        })
        setContentView(layout)
    }

    companion object {
        const val EXTRA_PACKAGE = "protected_package"
    }
}
