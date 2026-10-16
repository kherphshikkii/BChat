package com.kherphy.bchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kherphy.bchat.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val adapter = MessageAdapter(
        onImageClick = { path -> ImageViewerActivity.open(this, path) },
        onLongClick = { message -> showActions(message) },
        nameProvider = { ChatConnection.remoteName.ifBlank { "They" } }
    )

    private val uiHandler = Handler(Looper.getMainLooper())
    private val stopTyping = Runnable { setTypingOff() }
    private var typingSent = false
    private var lastTypingSent = 0L

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

        override fun onMessageChanged(message: Message) {
            adapter.replace(message)
        }

        override fun onMessageRemoved(id: String) {
            adapter.remove(id)
        }

        override fun onHistoryReloaded() {
            adapter.setAll(ChatConnection.history)
            scrollToBottom()
        }

        override fun onRemoteNameChanged(name: String) {
            binding.tvDeviceName.text = name
            renderTyping()
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

        override fun onTypingChanged(typing: Boolean) {
            renderTyping()
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
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                onTextEdited(s?.toString().orEmpty())
            }
        })

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
        if (!::binding.isInitialized) return
        setTypingOff()
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
        setTypingOff()
        ChatConnection.disconnect()
        finish()
    }

    private fun onTextEdited(text: String) {
        if (text.isBlank()) {
            setTypingOff()
            return
        }
        if (ChatConnection.state != ChatConnection.State.CONNECTED) return

        val now = SystemClock.elapsedRealtime()
        if (!typingSent || now - lastTypingSent > TYPING_REFRESH_MS) {
            typingSent = true
            lastTypingSent = now
            ChatConnection.sendTyping(true)
        }
        uiHandler.removeCallbacks(stopTyping)
        uiHandler.postDelayed(stopTyping, TYPING_STOP_MS)
    }

    private fun setTypingOff() {
        uiHandler.removeCallbacks(stopTyping)
        if (typingSent) {
            typingSent = false
            ChatConnection.sendTyping(false)
        }
    }

    private fun renderTyping() {
        val show = ChatConnection.remoteTyping &&
                ChatConnection.state == ChatConnection.State.CONNECTED
        if (show) {
            val name = ChatConnection.remoteName.ifBlank { "They" }
            binding.tvTyping.text = "$name is typing…"
            binding.tvTyping.visibility = View.VISIBLE
        } else {
            binding.tvTyping.visibility = View.GONE
        }
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

    private fun showActions(message: Message) {
        val options = mutableListOf<String>()
        if (message.text.isNotEmpty() && !message.deleted) options.add(ACTION_COPY)
        options.add(ACTION_REMOVE)
        val canUnsend = message.type == MessageType.SENT &&
                !message.deleted &&
                ChatConnection.state == ChatConnection.State.CONNECTED
        if (canUnsend) options.add(ACTION_UNSEND)

        MaterialAlertDialogBuilder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    ACTION_COPY -> copyText(message.text)
                    ACTION_REMOVE -> confirmRemove(message)
                    else -> confirmUnsend(message)
                }
            }
            .show()
    }

    private fun confirmRemove(message: Message) {
        val name = ChatConnection.remoteName.ifBlank { "The other person" }
        confirm(
            "Remove for you?",
            "This is removed from your phone only. $name can still see it.",
            "Remove"
        ) {
            ChatConnection.deleteForMe(message.id)
        }
    }

    private fun confirmUnsend(message: Message) {
        confirm(
            "Unsend message?",
            "This removes the message for everyone in this chat.",
            "Unsend"
        ) {
            ChatConnection.unsend(message.id) { ok ->
                if (!ok) toast("Couldn't unsend. Check the connection and try again.")
            }
        }
    }

    private fun confirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
    }

    private fun copyText(text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast("Copied")
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
        renderTyping()
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

    companion object {
        private const val TYPING_REFRESH_MS = 3_000L
        private const val TYPING_STOP_MS = 4_000L
        private const val ACTION_COPY = "Copy"
        private const val ACTION_REMOVE = "Remove for you"
        private const val ACTION_UNSEND = "Unsend for everyone"
    }
}