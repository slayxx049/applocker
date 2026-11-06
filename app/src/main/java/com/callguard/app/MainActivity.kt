package com.callguard.app

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 48, 28, 28)
            setBackgroundColor(0xFFF5F7FB.toInt())
        }

        val title = TextView(this).apply {
            text = "CallGuard"
            textSize = 32f
            setTextColor(0xFF172033.toInt())
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Your phone. Your access rules."
            textSize = 16f
            setTextColor(0xFF5B6578.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 28)
        }

        val sectionTitle = TextView(this).apply {
            text = "Security setup"
            textSize = 20f
            setTextColor(0xFF172033.toInt())
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Fingerprint authentication has not been tested yet."
            textSize = 15f
            setTextColor(0xFF5B6578.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 20)
        }

        val biometricButton = Button(this).apply {
            text = "Test fingerprint authentication"
            isAllCaps = false
            setOnClickListener { testBiometrics() }
        }

        val info = TextView(this).apply {
            text = "Next steps: add a private PIN, choose protected apps, and configure a safe call-only flow. Android verifies your fingerprint; CallGuard does not access or store fingerprint images or templates."
            textSize = 14f
            setTextColor(0xFF5B6578.toInt())
            setPadding(0, 28, 0, 0)
        }

        root.addView(title, matchWidth())
        root.addView(subtitle, matchWidth())
        root.addView(sectionTitle, matchWidth())
        root.addView(statusText, matchWidth())
        root.addView(
            biometricButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(info, matchWidth())
        setContentView(root)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun testBiometrics() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> showBiometricPrompt()
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                updateStatus("This device does not have biometric hardware.")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                updateStatus("Biometric hardware is currently unavailable.")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                updateStatus("No fingerprint is enrolled. Add one in Android Settings, then try again.")
            else -> updateStatus("Strong biometric authentication is not currently available.")
        }
    }

    private fun showBiometricPrompt() {
        val executor = Executor { command -> runOnUiThread(command) }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                updateStatus("Fingerprint verified by Android. CallGuard did not receive fingerprint data.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                updateStatus("Authentication ended: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                updateStatus("Fingerprint not recognized. You can try again.")
            }
        }

        val prompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CallGuard")
            .setSubtitle("Verify using your enrolled fingerprint")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun updateStatus(message: String) {
        statusText.text = message
    }
}
