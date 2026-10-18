package com.kherphy.bchat

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kherphy.bchat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ChatConnection.init(this)

        binding.etName.setText(Prefs.getName(this))
        binding.switchVisible.isChecked = Prefs.isVisible(this)
        binding.switchSave.isChecked = Prefs.isSaveHistory(this)
        binding.switchAsk.isChecked = Prefs.isAskBefore(this)
        binding.switchAlerts.isChecked = Prefs.isAlerts(this)
        binding.switchPreview.isChecked = Prefs.isPreview(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.switchVisible.setOnCheckedChangeListener { _, checked ->
            Prefs.setVisible(this, checked)
            ChatConnection.refreshListening()
        }
        binding.switchSave.setOnCheckedChangeListener { _, checked ->
            Prefs.setSaveHistory(this, checked)
        }
        binding.switchAsk.setOnCheckedChangeListener { _, checked ->
            Prefs.setAskBefore(this, checked)
        }
        binding.switchAlerts.setOnCheckedChangeListener { _, checked ->
            Prefs.setAlerts(this, checked)
        }
        binding.switchPreview.setOnCheckedChangeListener { _, checked ->
            Prefs.setPreview(this, checked)
        }

        binding.rowSound.setOnClickListener { openSoundSettings() }
        binding.rowClear.setOnClickListener { confirmClear() }
    }

    override fun onPause() {
        super.onPause()
        Prefs.setName(this, binding.etName.text.toString())
    }

    private fun openSoundSettings() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CHANNEL_MESSAGES)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open notification settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClear() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Clear all chats?")
            .setMessage("This deletes all messages on this phone. People you chatted with keep their own copy.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                ChatConnection.forgetHistory()
                Toast.makeText(this, "All chats cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
    }
}