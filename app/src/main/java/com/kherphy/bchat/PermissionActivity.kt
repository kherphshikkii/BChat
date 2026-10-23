package com.kherphy.bchat

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kherphy.bchat.databinding.ActivityPermissionBinding

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private var showingDenied = false
    private var leaving = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (BtPermissions.hasAll(this)) continueToApp() else showDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnPrimary.setOnClickListener {
            if (showingDenied) {
                openAppSettings()
            } else {
                permissionLauncher.launch(BtPermissions.required())
            }
        }

        binding.btnSecondary.setOnClickListener { goHome() }

        showAsk()
    }

    override fun onResume() {
        super.onResume()
        if (BtPermissions.hasAll(this)) continueToApp()
    }

    private fun continueToApp() {
        if (leaving) return
        leaving = true
        goHome()
    }

    private fun goHome() {
        val intent = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        startActivity(intent)
    }

    private fun showAsk() {
        showingDenied = false
        binding.iconCircle.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary_container))
        binding.ivIcon.setImageResource(R.drawable.ic_bluetooth)
        binding.ivIcon.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
        binding.tvTitle.text = "Allow nearby devices"
        binding.tvSubtitle.text = "Bluetooth Chat needs this to find and connect to phones near you."
        binding.featureList.visibility = View.VISIBLE
        binding.btnPrimary.text = "Allow"
    }

    private fun showDenied() {
        showingDenied = true
        binding.iconCircle.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error_container))
        binding.ivIcon.setImageResource(R.drawable.ic_bluetooth_off)
        binding.ivIcon.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_disconnected))
        binding.tvTitle.text = "Permission needed"
        binding.tvSubtitle.text =
            "Without access to nearby devices, Bluetooth Chat can't find phones. " +
                    "You can turn it on in Settings."
        binding.featureList.visibility = View.GONE
        binding.btnPrimary.text = "Open settings"
    }
}