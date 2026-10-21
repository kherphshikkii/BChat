package com.kherphy.bchat

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kherphy.bchat.databinding.ActivityProfileBinding

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

    private fun render() {
        val name = ChatConnection.remoteName.ifBlank { "Unknown device" }
        val state = ChatConnection.state
        val connected = state == ChatConnection.State.CONNECTED

        binding.tvName.text = name
        binding.avatarBig.set(name, AvatarStore.theirFile(ChatConnection.remoteAddress))
        binding.onlineDot.visibility = if (connected) View.VISIBLE else View.GONE

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