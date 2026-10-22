package com.kherphy.bchat

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kherphy.bchat.databinding.ActivityProfileBinding
import com.kherphy.bchat.databinding.DialogChatNameBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val listener = object : ChatConnection.Listener {
        override fun onStateChanged(state: ChatConnection.State) {
            render()
        }

        override fun onRemoteNameChanged(name: String) {
            render()
        }

        override fun onAvatarChanged(address: String) {
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ChatConnection.remoteAddress.isBlank()) {
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.avatarBig.setOnClickListener {
            AvatarStore.theirFile(ChatConnection.remoteAddress)?.let {
                ImageViewerActivity.open(this, it.absolutePath)
            }
        }

        binding.rowChatName.setOnClickListener { showChatNameDialog() }

        binding.btnAction.setOnClickListener {
            if (ChatConnection.state == ChatConnection.State.CONNECTED) {
                ChatConnection.disconnect()
                finish()
            } else {
                ChatConnection.resetAutoAttempts()
                ChatConnection.connect(ChatConnection.remoteAddress)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        ChatConnection.addListener(listener)
        render()
    }

    override fun onStop() {
        super.onStop()
        if (!::binding.isInitialized) return
        ChatConnection.removeListener(listener)
    }

    private fun showChatNameDialog() {
        val dialogBinding = DialogChatNameBinding.inflate(layoutInflater)
        val real = ChatConnection.realName()
        val current = ChatConnection.chatName()

        dialogBinding.tvHint.text =
            "Choose what you call $real. They’ll see a note in the chat that you changed it."
        dialogBinding.etChatName.setText(current)
        dialogBinding.etChatName.setSelection(dialogBinding.etChatName.text?.length ?: 0)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle("Chat name")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                ChatConnection.setChatName(dialogBinding.etChatName.text.toString())
                render()
            }
            .setNegativeButton("Cancel", null)

        if (current.isNotEmpty()) {
            builder.setNeutralButton("Reset") { _, _ ->
                ChatConnection.setChatName("")
                render()
            }
        }
        builder.show()
    }

    private fun render() {
        val name = ChatConnection.remoteName.ifBlank { "Unknown device" }
        val chatName = ChatConnection.chatName()
        val state = ChatConnection.state
        val connected = state == ChatConnection.State.CONNECTED

        binding.tvName.text = name
        binding.avatarBig.set(name, AvatarStore.theirFile(ChatConnection.remoteAddress))
        binding.onlineDot.visibility = if (connected) View.VISIBLE else View.GONE

        if (chatName.isEmpty()) {
            binding.tvRealName.visibility = View.GONE
            binding.tvChatNameValue.text = "Not set"
            binding.tvChatNameValue.setTextColor(
                ContextCompat.getColor(this, R.color.text_secondary)
            )
        } else {
            binding.tvRealName.visibility = View.VISIBLE
            binding.tvRealName.text = ChatConnection.realName()
            binding.tvChatNameValue.text = chatName
            binding.tvChatNameValue.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
        }

        val statusColor = when (state) {
            ChatConnection.State.CONNECTED -> R.color.status_connected
            ChatConnection.State.CONNECTING -> R.color.warning_text
            else -> R.color.status_disconnected
        }
        val color = ContextCompat.getColor(this, statusColor)
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.tvStatus.setTextColor(color)
        binding.tvStatus.text = when (state) {
            ChatConnection.State.CONNECTED -> "Connected"
            ChatConnection.State.CONNECTING -> "Connecting…"
            else -> "Disconnected"
        }

        val actionColor = ContextCompat.getColor(
            this,
            if (connected) R.color.status_disconnected else R.color.primary
        )
        binding.btnAction.text = if (connected) "Disconnect" else "Reconnect"
        binding.btnAction.isEnabled = state != ChatConnection.State.CONNECTING
        binding.btnAction.setTextColor(actionColor)
        binding.btnAction.strokeColor = ColorStateList.valueOf(actionColor)
    }
}