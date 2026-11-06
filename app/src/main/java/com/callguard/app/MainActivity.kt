package com.callguard.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun render() {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 28)
            setBackgroundColor(0xFFF5F7FB.toInt())
        }

        val title = TextView(this).apply {
            text = "CallGuard"
            textSize = 30f
            setTextColor(0xFF172033.toInt())
        }
        content.addView(title, fullWidth())

        val subtitle = TextView(this).apply {
            text = "Choose which apps require authentication."
            textSize = 15f
            setTextColor(0xFF5B6578.toInt())
            setPadding(0, 6, 0, 18)
        }
        content.addView(subtitle, fullWidth())

        val permissions = Button(this).apply {
            text = "Set up required Android permissions"
            isAllCaps = false
            setOnClickListener { openPermissionSettings() }
        }
        content.addView(permissions, fullWidth())

        val permissionStatus = TextView(this).apply {
            text = "Usage access: ${if (hasUsageAccess()) "Ready" else "Not enabled"}   •   Overlay: ${if (Settings.canDrawOverlays(this@MainActivity)) "Ready" else "Not enabled"}"
            textSize = 13f
            setTextColor(0xFF5B6578.toInt())
            setPadding(0, 6, 0, 18)
        }
        content.addView(permissionStatus, fullWidth())

        val pinTitle = TextView(this).apply {
            text = if (SecurityStore.hasPin(this@MainActivity)) "PIN is configured" else "Create your CallGuard PIN"
            textSize = 19f
            setTextColor(0xFF172033.toInt())
        }
        content.addView(pinTitle, fullWidth())

        val pinInput = EditText(this).apply {
            hint = "Enter a PIN (at least 4 digits)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setSingleLine(true)
        }
        content.addView(pinInput, fullWidth())

        val savePin = Button(this).apply {
            text = "Save / change PIN"
            isAllCaps = false
            setOnClickListener {
                val pin = pinInput.text.toString()
                if (pin.length < 4 || pin.any { !it.isDigit() }) {
                    Toast.makeText(this@MainActivity, "Use at least 4 digits.", Toast.LENGTH_SHORT).show()
                } else {
                    SecurityStore.setPin(this@MainActivity, pin)
                    pinInput.text.clear()
                    Toast.makeText(this@MainActivity, "PIN saved on this device.", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
        content.addView(savePin, fullWidth())

        val appTitle = TextView(this).apply {
            text = "Select apps to lock"
            textSize = 21f
            setTextColor(0xFF172033.toInt())
            setPadding(0, 22, 0, 8)
        }
        content.addView(appTitle, fullWidth())

        val appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val protected = getSharedPreferences(SecurityStore.PREFS, Context.MODE_PRIVATE)
            .getStringSet(SecurityStore.KEY_PROTECTED, emptySet())?.toMutableSet() ?: mutableSetOf()

        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        for (info in apps) {
            val appPackage = info.activityInfo.packageName
            val check = CheckBox(this).apply {
                text = info.loadLabel(packageManager).toString()
                setCompoundDrawablesWithIntrinsicBounds(info.loadIcon(packageManager), null, null, null)
                compoundDrawablePadding = 18
                textSize = 16f
                isChecked = protected.contains(appPackage)
                setPadding(4, 10, 4, 10)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) protected.add(appPackage) else protected.remove(appPackage)
                    getSharedPreferences(SecurityStore.PREFS, Context.MODE_PRIVATE)
                        .edit().putStringSet(SecurityStore.KEY_PROTECTED, protected.toSet()).apply()
                }
            }
            appList.addView(check, fullWidth())
        }

        if (apps.isEmpty()) {
            appList.addView(TextView(this).apply {
                text = "No launchable apps were found."
                setTextColor(0xFF5B6578.toInt())
            })
        }
        content.addView(appList, fullWidth())

        val start = Button(this).apply {
            text = "Enable app protection"
            isAllCaps = false
            setOnClickListener {
                if (!SecurityStore.hasPin(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Create a PIN first.", Toast.LENGTH_LONG).show()
                } else if (protected.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Select at least one app.", Toast.LENGTH_LONG).show()
                } else if (!hasUsageAccess() || !Settings.canDrawOverlays(this@MainActivity)) {
                    openPermissionSettings()
                } else {
                    val service = Intent(this@MainActivity, AppMonitorService::class.java)
                    androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, service)
                    Toast.makeText(this@MainActivity, "App protection service started.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(start, fullWidth())

        val note = TextView(this).apply {
            text = "Keep CallGuard's protection notification visible. Android may stop background services, and some system or secure screens cannot be covered. This prototype should be tested before relying on it."
            textSize = 13f
            setTextColor(0xFF5B6578.toInt())
            setPadding(0, 18, 0, 0)
        }
        content.addView(note, fullWidth())

        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    private fun openPermissionSettings() {
        if (!hasUsageAccess()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Required permissions are enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
