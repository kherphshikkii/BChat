package com.kherphy.bchat

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.kherphy.bchat.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = MessageAdapter { path -> ImageViewerActivity.open(this, path) }

    private var preparing = false
    private var activeTransfer: ChatConnection.ImageTransfer? = null
    private var sendPercent = -1
    private var receivePercent = -1

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) sendPhoto(uri)
    }

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

        override fun onRemoteNameChanged(name: String) {
            binding.tvDeviceName.text = name
        }

        override fun onIncomingRequest(name: String) {
            RequestDialog.show(this@MainActivity, name)
        }

        override fun onIncomingImageProgress(received: Int, total: Int) {
            receivePercent = percent(received, total)
            renderTransfer()
        }

        override fun onIncomingImageEnded() {
            receivePercent = -1
            renderTransfer()
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
        renderTransfer()
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

        binding.btnAttach.setOnClickListener {
            if (ChatConnection.state != ChatConnection.State.CONNECTED) {
                toast("Not connected. Tap Reconnect.")
            } else {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }

        binding.btnCancelTransfer.setOnClickListener {
            activeTransfer?.cancel()
            binding.btnCancelTransfer.isEnabled = false
        }

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

    private fun sendPhoto(uri: Uri) {
        if (ChatConnection.state != ChatConnection.State.CONNECTED) {
            toast("Not connected. Tap Reconnect.")
            return
        }
        if (preparing || activeTransfer != null) return

        preparing = true
        renderTransfer()
        ImageUtils.prepareAsync(this, uri) { file ->
            preparing = false
            if (file == null) {
                renderTransfer()
                toast("Couldn't read that photo")
            } else {
                startSending(file)
            }
        }
    }

    private fun startSending(file: File) {
        sendPercent = 0
        binding.btnCancelTransfer.isEnabled = true
        activeTransfer = ChatConnection.sendImage(
            file,
            { sent, total ->
                sendPercent = percent(sent, total)
                renderTransfer()
            },
            { result ->
                activeTransfer = null
                sendPercent = -1
                renderTransfer()
                when (result) {
                    ChatConnection.SendResult.SENT -> Unit
                    ChatConnection.SendResult.CANCELLED -> {
                        file.delete()
                        toast("Photo cancelled")
                    }
                    ChatConnection.SendResult.FAILED -> {
                        file.delete()
                        toast("Photo not sent. Check the connection and try again.")
                    }
                }
            }
        )
        renderTransfer()
    }

    private fun percent(done: Int, total: Int): Int =
        if (total <= 0) 0 else (done.toLong() * 100 / total).toInt()

    private fun renderTransfer() {
        when {
            preparing -> {
                binding.transferBar.visibility = View.VISIBLE
                binding.tvTransfer.text = "Preparing photo…"
                binding.transferProgress.progress = 0
                binding.btnCancelTransfer.visibility = View.GONE
            }
            activeTransfer != null -> {
                binding.transferBar.visibility = View.VISIBLE
                binding.tvTransfer.text = "Sending photo… ${sendPercent.coerceAtLeast(0)}%"
                binding.transferProgress.progress = sendPercent.coerceAtLeast(0)
                binding.btnCancelTransfer.visibility = View.VISIBLE
            }
            receivePercent >= 0 -> {
                binding.transferBar.visibility = View.VISIBLE
                binding.tvTransfer.text = "Receiving photo… $receivePercent%"
                binding.transferProgress.progress = receivePercent
                binding.btnCancelTransfer.visibility = View.GONE
            }
            else -> binding.transferBar.visibility = View.GONE
        }
        binding.btnAttach.isEnabled = !preparing && activeTransfer == null
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
                receivePercent = -1
                renderTransfer()
            }

            else -> {
                showStatus("Disconnected", R.color.status_disconnected)
                binding.btnDisconnect.visibility = View.GONE
                binding.composeBar.visibility = View.GONE
                binding.disconnectedBanner.visibility = View.VISIBLE
                binding.btnReconnect.isEnabled = true
                binding.btnReconnect.text = "Reconnect"
                receivePercent = -1
                renderTransfer()
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