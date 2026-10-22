package com.kherphy.bchat

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kherphy.bchat.databinding.ActivitySettingsBinding
import com.kherphy.bchat.databinding.SheetProfilePhotoBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) startPreview(uri)
    }

    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshAvatar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        AvatarStore.init(this)

        binding.etName.setText(Prefs.getName(this))
        binding.tvProfileName.text = Prefs.getName(this)
        binding.switchVisible.isChecked = Prefs.isVisible(this)
        binding.switchSave.isChecked = Prefs.isSaveHistory(this)
        binding.switchAsk.isChecked = Prefs.isAskBefore(this)
        binding.switchReceipts.isChecked = Prefs.isReceipts(this)
        binding.switchAlerts.isChecked = Prefs.isAlerts(this)
        binding.switchPreview.isChecked = Prefs.isPreview(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.tvThemeValue.text = ThemeHelper.label(Prefs.getTheme(this))
        binding.rowTheme.setOnClickListener { showThemeDialog() }

        binding.avatarFrame.setOnClickListener { showPhotoSheet() }
        binding.btnChangePhoto.setOnClickListener { showPhotoSheet() }

        binding.etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                refreshAvatar()
            }
        })

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
        binding.switchReceipts.setOnCheckedChangeListener { _, checked ->
            Prefs.setReceipts(this, checked)
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

    override fun onResume() {
        super.onResume()
        refreshAvatar()
    }

    override fun onPause() {
        super.onPause()
        Prefs.setName(this, binding.etName.text.toString())
    }

    private fun currentName(): String =
        binding.etName.text.toString().trim().ifBlank { Prefs.getName(this) }

    private fun refreshAvatar() {
        val name = currentName()
        binding.tvProfileName.text = name
        binding.avatarMe.set(name, AvatarStore.myFile())
    }

    private fun showPhotoSheet() {
        val sheet = BottomSheetDialog(this)
        val sheetBinding = SheetProfilePhotoBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        sheetBinding.actionRemove.visibility =
            if (AvatarStore.myFile() != null) View.VISIBLE else View.GONE

        sheetBinding.actionGallery.setOnClickListener {
            sheet.dismiss()
            pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        sheetBinding.actionRemove.setOnClickListener {
            sheet.dismiss()
            AvatarStore.removeMine()
            ChatConnection.sendAvatarIfConnected()
            refreshAvatar()
        }
        sheet.show()
    }

    private fun startPreview(uri: android.net.Uri) {
        AvatarUtils.prepareAsync(this, uri) { file ->
            if (file == null) {
                Toast.makeText(this, "Couldn't read that photo", Toast.LENGTH_SHORT).show()
            } else {
                previewLauncher.launch(Intent(this, PhotoPreviewActivity::class.java))
            }
        }
    }

    private fun showThemeDialog() {
        val values = listOf(ThemeHelper.LIGHT, ThemeHelper.DARK, ThemeHelper.SYSTEM)
        val labels = values.map { ThemeHelper.label(it) }.toTypedArray()
        val current = values.indexOf(Prefs.getTheme(this)).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                val chosen = values[which]
                if (chosen != Prefs.getTheme(this)) {
                    Prefs.setTheme(this, chosen)
                    ThemeHelper.apply(this)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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