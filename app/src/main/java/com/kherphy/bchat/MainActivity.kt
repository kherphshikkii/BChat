package com.kherphy.bchat

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.kherphy.bchat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = MessageAdapter()

    private val connectionListener = object : ChatConnection.Listener {
        override fun onStateChanged(state: ChatConnection.State) {
            render(state)
        }

        override fun onConnectionFailed(reason: String) {
            toast(reason)
        }

        override fun onMessageAdded(message: Message) {
            adapter.addMessage(message)
            scrollToBottom()
        }

        override fun onHistoryReloaded() {
            adapter.setAll(ChatConnection.history)
            scrollToBottom()
        }

        override fun onIncomingRequest(name: String) {
            RequestDialog.show(this@MainActivity, name)
        }

        override fun onRemoteNameChanged(name: String) {
            binding.tvDeviceName.text = name
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        if (ChatConnection.remoteAddress.isBlank()) {
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupMessageList()
        setupButtons()

        binding.etMessage.filters =
            arrayOf(InputFilter.LengthFilter(ChatConnection.MAX_MESSAGE_LENGTH))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveChat()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return

        adapter.setAll(ChatConnection.history)
        scrollToBottom()

        ChatConnection.addListener(connectionListener)
        render(ChatConnection.state)
    }

    override fun onStop() {
        super.onStop()
        ChatConnection.removeListener(connectionListener)
    }

    private fun setupMessageList() {
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { leaveChat() }

        binding.btnSend.setOnClickListener { sendMessage() }

        binding.btnDisconnect.setOnClickListener {
            ChatConnection.disconnect()
        }

        binding.btnReconnect.setOnClickListener {
            ChatConnection.connect(ChatConnection.remoteAddress)
        }
    }

    private fun leaveChat() {
        ChatConnection.disconnect()
        finish()
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        if (ChatConnection.state != ChatConnection.State.CONNECTED) {
            toast("Not connected. Tap Reconnect.")
            return
        }

        binding.btnSend.isEnabled = false
        ChatConnection.send(text) { ok ->
            binding.btnSend.isEnabled = true
            if (ok) {
                binding.etMessage.text.clear()
            } else {
                toast("Message not sent. Check the connection and try again.")
            }
        }
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun render(state: ChatConnection.State) {
        binding.tvDeviceName.text = ChatConnection.remoteName.ifBlank { "Unknown device" }

        when (state) {
            ChatConnection.State.CONNECTED -> {
                showStatus("Connected", R.color.status_connected)
                binding.btnDisconnect.visibility = View.VISIBLE
                binding.composeBar.visibility = View.VISIBLE
                binding.disconnectedBanner.visibility = View.GONE
            }

            ChatConnection.State.CONNECTING -> {
                showStatus("Connecting…", R.color.warning_text)
                binding.btnDisconnect.visibility = View.GONE
                binding.composeBar.visibility = View.GONE
                binding.disconnectedBanner.visibility = View.VISIBLE
                binding.btnReconnect.isEnabled = false
                binding.btnReconnect.text = "Connecting…"
            }

            else -> {
                showStatus("Disconnected", R.color.status_disconnected)
                binding.btnDisconnect.visibility = View.GONE
                binding.composeBar.visibility = View.GONE
                binding.disconnectedBanner.visibility = View.VISIBLE
                binding.btnReconnect.isEnabled = true
                binding.btnReconnect.text = "Reconnect"
            }
        }
    }

    private fun showStatus(text: String, colorRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.tvStatus.setTextColor(color)
        binding.tvStatus.text = text
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}