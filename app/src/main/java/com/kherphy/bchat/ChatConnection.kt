package com.kherphy.bchat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
object ChatConnection {

    private val APP_UUID: UUID = UUID.fromString("8f3c2a54-6d1e-4b7a-9c0e-5b2d7a1e4f90")
    private const val SERVICE_NAME = "BChat"

    private const val TYPE_HELLO: Byte = 1
    private const val TYPE_MESSAGE: Byte = 2
    private const val TYPE_IMAGE_START: Byte = 3
    private const val TYPE_IMAGE_CHUNK: Byte = 4
    private const val TYPE_IMAGE_END: Byte = 5
    private const val TYPE_IMAGE_CANCEL: Byte = 6
    private const val TYPE_UNSEND: Byte = 7
    private const val TYPE_TYPING: Byte = 8
    private const val TYPE_ACK: Byte = 9

    private const val CHUNK_SIZE = 8192
    private const val MAX_CHUNK_SIZE = 65536
    private const val MAX_IMAGE_BYTES = 3_000_000
    private const val MAX_ID_LENGTH = 64
    private const val REQUEST_TIMEOUT_MS = 30_000L
    private const val TYPING_TIMEOUT_MS = 6_000L
    private const val AUTO_RETRY_MIN_MS = 45_000L
    private const val AUTO_RETRY_MAX_MS = 75_000L
    private const val AUTO_MAX_ATTEMPTS = 40

    const val MAX_MESSAGE_LENGTH = 1000

    enum class State {
        IDLE,
        LISTENING,
        CONNECTING,
        CONNECTED
    }

    enum class SendResult {
        SENT,
        FAILED,
        CANCELLED
    }

    class ImageTransfer {
        @Volatile
        var cancelled = false
            private set

        fun cancel() {
            cancelled = true
        }
    }

    interface Listener {
        fun onStateChanged(state: State) {}
        fun onConnectionFailed(reason: String) {}
        fun onMessageAdded(message: Message) {}
        fun onMessageChanged(message: Message) {}
        fun onMessageRemoved(id: String) {}
        fun onHistoryReloaded() {}
        fun onRemoteNameChanged(name: String) {}
        fun onIncomingRequest(name: String) {}
        fun onIncomingImageProgress(received: Int, total: Int) {}
        fun onIncomingImageEnded() {}
        fun onTypingChanged(typing: Boolean) {}
    }

    var state: State = State.IDLE
        private set
    var remoteName: String = ""
        private set
    var remoteAddress: String = ""
        private set
    var remoteTyping: Boolean = false
        private set

    private val messages = mutableListOf<Message>()
    val history: List<Message> get() = messages
    private var historyAddress: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val sendExecutor = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())

    private var appContext: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var pendingSocket: BluetoothSocket? = null
    private val pendingTimeout = Runnable { rejectPending() }
    private val typingExpire = Runnable { setRemoteTyping(false) }

    private var silentConnect = false
    private var autoAttempts = 0
    private var autoIndex = 0
    private val autoRetry = Runnable { autoRetryTick() }

    fun init(context: Context) {
        appContext = context.applicationContext
        ChatStore.init(context)
        if (adapter == null) {
            adapter = context.applicationContext
                .getSystemService(BluetoothManager::class.java)?.adapter
            scheduleAutoRetry()
        }
    }

    private fun ctx(): Context = checkNotNull(appContext)

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun refreshListening() {
        val ready = adapter?.isEnabled == true &&
                Prefs.isVisible(ctx()) &&
                BtPermissions.hasAll(ctx())
        if (ready) startListening() else stopListening()
    }

    fun startListening() {
        if (state == State.CONNECTED || state == State.CONNECTING) return
        if (!Prefs.isVisible(ctx())) return
        if (acceptThread == null) {
            val thread = AcceptThread()
            acceptThread = thread
            thread.start()
        }
        setState(State.LISTENING)
    }

    fun stopListening() {
        acceptThread?.cancel()
        acceptThread = null
        if (state == State.LISTENING) setState(State.IDLE)
    }

    fun connect(address: String) {
        startConnect(address, false)
    }

    private fun startConnect(address: String, silent: Boolean) {
        if (state == State.CONNECTING || state == State.CONNECTED) return
        rejectPending()

        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (device == null) {
            if (!silent) notifyFailed("Couldn't find that device")
            return
        }

        acceptThread?.cancel()
        acceptThread = null

        silentConnect = silent
        if (!silent) {
            remoteAddress = address
            remoteName = device.name ?: address
        }

        val thread = ConnectThread(device)
        connectThread = thread
        thread.start()
        setState(State.CONNECTING)
    }

    fun disconnect() {
        val wasConnected = state == State.CONNECTED

        silentConnect = false
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        setRemoteTyping(false)

        if (wasConnected) addNotice("You disconnected")
        setState(State.IDLE)
        startListening()
        scheduleAutoRetry()
    }

    fun send(text: String, onResult: (Boolean) -> Unit) {
        if (remoteAddress.isBlank()) {
            onResult(false)
            return
        }
        val id = UUID.randomUUID().toString()
        val thread = connectedThread

        if (state == State.CONNECTED && thread != null) {
            sendExecutor.execute {
                val ok = thread.writeText(id, text)
                handler.post {
                    addToHistory(
                        Message(
                            text,
                            System.currentTimeMillis(),
                            MessageType.SENT,
                            null,
                            id,
                            false,
                            !ok
                        )
                    )
                    onResult(true)
                }
            }
            return
        }

        if (!Prefs.isSaveHistory(ctx())) {
            onResult(false)
            return
        }
        addToHistory(
            Message(text, System.currentTimeMillis(), MessageType.SENT, null, id, false, true)
        )
        resetAutoAttempts()
        onResult(true)
    }

    fun sendImage(
        file: File,
        onProgress: (Int, Int) -> Unit,
        onResult: (SendResult) -> Unit
    ): ImageTransfer {
        val transfer = ImageTransfer()
        val thread = connectedThread
        if (state != State.CONNECTED || thread == null) {
            handler.post { onResult(SendResult.FAILED) }
            return transfer
        }
        val id = UUID.randomUUID().toString()
        sendExecutor.execute {
            val result = thread.writeImage(file, id, transfer) { sent, total ->
                handler.post { onProgress(sent, total) }
            }
            handler.post {
                if (result == SendResult.SENT) {
                    addToHistory(
                        Message(
                            "",
                            System.currentTimeMillis(),
                            MessageType.SENT,
                            file.absolutePath,
                            id
                        )
                    )
                }
                onResult(result)
            }
        }
        return transfer
    }

    fun sendTyping(typing: Boolean) {
        val thread = connectedThread
        if (state != State.CONNECTED || thread == null) return
        sendExecutor.execute { thread.writeTyping(typing) }
    }

    fun deleteForMe(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val removed = messages.removeAt(index)
        removed.imagePath?.let { File(it).delete() }
        persist()
        listeners.forEach { it.onMessageRemoved(id) }
    }

    fun unsend(id: String, onResult: (Boolean) -> Unit) {
        val thread = connectedThread
        val target = messages.firstOrNull { it.id == id }
        val allowed = state == State.CONNECTED &&
                thread != null &&
                target != null &&
                target.type == MessageType.SENT &&
                !target.deleted &&
                !target.pending
        if (!allowed || thread == null) {
            onResult(false)
            return
        }
        sendExecutor.execute {
            val ok = thread.writeUnsend(id)
            handler.post {
                if (ok) markDeleted(id)
                onResult(ok)
            }
        }
    }

    fun openSavedChat(address: String, name: String) {
        if (state == State.CONNECTED || state == State.CONNECTING) return
        remoteAddress = address
        remoteName = name
        loadHistory(address)
    }

    fun forgetHistory() {
        ChatStore.clearAll()
        messages.clear()
        historyAddress = ""
        handler.removeCallbacks(autoRetry)
    }

    fun resetAutoAttempts() {
        autoAttempts = 0
        scheduleAutoRetry()
    }

    fun acceptPending() {
        val socket = pendingSocket ?: return
        pendingSocket = null
        handler.removeCallbacks(pendingTimeout)
        RequestDialog.dismiss()
        onConnected(socket)
    }

    fun rejectPending() {
        val socket = pendingSocket ?: return
        pendingSocket = null
        handler.removeCallbacks(pendingTimeout)
        RequestDialog.dismiss()
        closeQuietly(socket)
        startListening()
    }

    private fun scheduleAutoRetry() {
        handler.removeCallbacks(autoRetry)
        if (appContext == null) return
        if (ChatStore.pendingAddresses().isEmpty()) return
        handler.postDelayed(autoRetry, (AUTO_RETRY_MIN_MS..AUTO_RETRY_MAX_MS).random())
    }

    private fun autoRetryTick() {
        val addresses = ChatStore.pendingAddresses()
        if (addresses.isEmpty() || autoAttempts >= AUTO_MAX_ATTEMPTS) return

        val bluetooth = adapter
        val ready = bluetooth != null &&
                bluetooth.isEnabled &&
                !bluetooth.isDiscovering &&
                BtPermissions.hasAll(ctx()) &&
                pendingSocket == null &&
                state != State.CONNECTED &&
                state != State.CONNECTING

        if (ready) {
            autoAttempts++
            autoIndex++
            startConnect(addresses[autoIndex % addresses.size], true)
        }
        scheduleAutoRetry()
    }

    private fun flushPending(thread: ConnectedThread) {
        val waiting = messages.filter {
            it.type == MessageType.SENT && it.pending && !it.deleted && it.imagePath == null
        }
        if (waiting.isEmpty()) return
        sendExecutor.execute {
            waiting.forEach { thread.writeText(it.id, it.text) }
        }
    }

    private fun setState(newState: State) {
        state = newState
        listeners.forEach { it.onStateChanged(newState) }
    }

    private fun setRemoteTyping(value: Boolean) {
        handler.removeCallbacks(typingExpire)
        if (value) handler.postDelayed(typingExpire, TYPING_TIMEOUT_MS)
        if (remoteTyping == value) return
        remoteTyping = value
        listeners.forEach { it.onTypingChanged(value) }
    }

    private fun notifyFailed(reason: String) {
        listeners.forEach { it.onConnectionFailed(reason) }
    }

    private fun addToHistory(message: Message) {
        messages.add(message)
        if (message.type != MessageType.NOTICE) persist()
        listeners.forEach { it.onMessageAdded(message) }
    }

    private fun markDeleted(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = messages[index]
        old.imagePath?.let { File(it).delete() }
        val updated = old.copy(text = "", imagePath = null, deleted = true, pending = false)
        messages[index] = updated
        persist()
        listeners.forEach { it.onMessageChanged(updated) }
    }

    private fun addNotice(label: String) {
        val now = System.currentTimeMillis()
        val time = timeFormat.format(Date(now))
        addToHistory(Message("$label · $time", now, MessageType.NOTICE))
    }

    private fun persist() {
        if (historyAddress.isBlank()) return
        if (!Prefs.isSaveHistory(ctx())) return
        ChatStore.save(historyAddress, remoteName, messages)
    }

    private fun loadHistory(address: String) {
        messages.clear()
        historyAddress = address

        var lastDay = ""
        if (Prefs.isSaveHistory(ctx())) {
            ChatStore.loadMessages(address).forEach { message ->
                val label = Format.dayLabel(message.timestamp)
                if (label != lastDay) {
                    messages.add(Message(label, message.timestamp, MessageType.NOTICE))
                    lastDay = label
                }
                messages.add(message)
            }
        }
        if (lastDay != "Today") {
            messages.add(Message("Today", System.currentTimeMillis(), MessageType.NOTICE))
        }

        listeners.forEach { it.onHistoryReloaded() }
    }

    private fun closeQuietly(socket: BluetoothSocket) {
        try {
            socket.close()
        } catch (e: IOException) {
        }
    }

    private fun accepted(thread: AcceptThread, socket: BluetoothSocket) {
        val busy = state == State.CONNECTED || state == State.CONNECTING || pendingSocket != null
        if (thread !== acceptThread || busy) {
            closeQuietly(socket)
            return
        }
        acceptThread = null

        if (Prefs.isAskBefore(ctx())) {
            pendingSocket = socket
            val device = socket.remoteDevice
            val name = device.name ?: device.address
            handler.postDelayed(pendingTimeout, REQUEST_TIMEOUT_MS)
            listeners.forEach { it.onIncomingRequest(name) }
        } else {
            onConnected(socket)
        }
    }

    private fun acceptFailed(thread: AcceptThread) {
        if (thread !== acceptThread) return
        acceptThread = null
        if (state == State.LISTENING) setState(State.IDLE)
    }

    private fun connectSucceeded(thread: ConnectThread, socket: BluetoothSocket) {
        if (thread !== connectThread || thread.cancelled) {
            closeQuietly(socket)
            return
        }
        connectThread = null
        onConnected(socket)
    }

    private fun connectFailed(thread: ConnectThread) {
        if (thread !== connectThread || thread.cancelled) return
        connectThread = null
        val quiet = silentConnect
        silentConnect = false
        setState(State.IDLE)
        if (!quiet) {
            notifyFailed(
                "Couldn't connect. Check that the other phone has BChat open, then try again."
            )
        }
        startListening()
        scheduleAutoRetry()
    }

    private fun onConnected(socket: BluetoothSocket) {
        val device = socket.remoteDevice
        remoteAddress = device.address
        remoteName = device.name ?: device.address
        silentConnect = false
        autoAttempts = 0
        setRemoteTyping(false)

        if (remoteAddress != historyAddress) loadHistory(remoteAddress)

        val thread = ConnectedThread(socket)
        connectedThread = thread
        thread.start()

        addNotice("Connected")
        setState(State.CONNECTED)

        val myName = Prefs.getName(ctx())
        sendExecutor.execute { thread.writeHello(myName) }
        flushPending(thread)
    }

    private fun connectionLost(thread: ConnectedThread) {
        if (thread !== connectedThread) return
        connectedThread = null
        setRemoteTyping(false)
        addNotice("Connection lost")
        setState(State.IDLE)
        startListening()
        scheduleAutoRetry()
    }

    private fun helloReceived(thread: ConnectedThread, text: String) {
        if (thread !== connectedThread) return
        val name = text.trim().take(30)
        if (name.isEmpty()) return
        remoteName = name
        persist()
        listeners.forEach { it.onRemoteNameChanged(name) }
    }

    private fun textReceived(thread: ConnectedThread, id: String, text: String) {
        if (thread !== connectedThread) return
        val cleanId = id.take(MAX_ID_LENGTH)
        sendExecutor.execute { thread.writeAck(cleanId) }
        if (messages.any { it.id == cleanId }) return
        setRemoteTyping(false)
        addToHistory(
            Message(text, System.currentTimeMillis(), MessageType.RECEIVED, null, cleanId)
        )
    }

    private fun ackReceived(thread: ConnectedThread, id: String) {
        if (thread !== connectedThread) return
        val index = messages.indexOfFirst { it.id == id.take(MAX_ID_LENGTH) }
        if (index < 0) return
        val old = messages[index]
        if (old.type != MessageType.SENT || !old.pending) return
        val updated = old.copy(pending = false)
        messages[index] = updated
        persist()
        listeners.forEach { it.onMessageChanged(updated) }
    }

    private fun unsendReceived(thread: ConnectedThread, id: String) {
        if (thread !== connectedThread) return
        val target = messages.firstOrNull { it.id == id.take(MAX_ID_LENGTH) } ?: return
        if (target.type != MessageType.RECEIVED || target.deleted) return
        markDeleted(target.id)
    }

    private fun typingReceived(thread: ConnectedThread, typing: Boolean) {
        if (thread !== connectedThread) return
        setRemoteTyping(typing)
    }

    private fun incomingProgress(thread: ConnectedThread, received: Int, total: Int) {
        if (thread !== connectedThread) return
        listeners.forEach { it.onIncomingImageProgress(received, total) }
    }

    private fun incomingEnded(thread: ConnectedThread) {
        if (thread !== connectedThread) return
        listeners.forEach { it.onIncomingImageEnded() }
    }

    private fun incomingRejected(thread: ConnectedThread) {
        if (thread !== connectedThread) return
        addNotice("A photo was too large to receive")
    }

    private fun imageReceived(thread: ConnectedThread, file: File, id: String) {
        if (thread !== connectedThread) {
            file.delete()
            return
        }
        setRemoteTyping(false)
        addToHistory(
            Message(
                "",
                System.currentTimeMillis(),
                MessageType.RECEIVED,
                file.absolutePath,
                id.take(MAX_ID_LENGTH)
            )
        )
        listeners.forEach { it.onIncomingImageEnded() }
    }

    private class AcceptThread : Thread() {
        private var server: BluetoothServerSocket? = null
        @Volatile private var cancelled = false

        override fun run() {
            try {
                val s = adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
                    ?: throw IOException("No Bluetooth adapter")
                server = s
                val socket = s.accept()
                try {
                    s.close()
                } catch (e: IOException) {
                }
                handler.post { accepted(this@AcceptThread, socket) }
            } catch (e: IOException) {
                if (!cancelled) {
                    Log.e("BChat", "Listening failed", e)
                    handler.post { acceptFailed(this@AcceptThread) }
                }
            }
        }

        fun cancel() {
            cancelled = true
            try {
                server?.close()
            } catch (e: IOException) {
            }
        }
    }

    private class ConnectThread(private val device: BluetoothDevice) : Thread() {
        @Volatile var cancelled = false
        private var socket: BluetoothSocket? = null

        override fun run() {
            try {
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket = s
                adapter?.cancelDiscovery()
                s.connect()
                handler.post { connectSucceeded(this@ConnectThread, s) }
            } catch (e: IOException) {
                Log.e("BChat", "Connect failed", e)
                try {
                    socket?.close()
                } catch (closeError: IOException) {
                }
                handler.post { connectFailed(this@ConnectThread) }
            }
        }

        fun cancel() {
            cancelled = true
            try {
                socket?.close()
            } catch (e: IOException) {
            }
        }
    }

    private class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val input = DataInputStream(socket.inputStream)
        private val output = DataOutputStream(socket.outputStream)
        @Volatile private var closedByUs = false

        private var incomingFile: File? = null
        private var incomingOut: FileOutputStream? = null
        private var incomingId = ""
        private var incomingTotal = 0
        private var incomingReceived = 0

        override fun run() {
            try {
                while (true) {
                    when (input.readByte()) {
                        TYPE_HELLO -> {
                            val name = input.readUTF()
                            handler.post { helloReceived(this@ConnectedThread, name) }
                        }
                        TYPE_MESSAGE -> {
                            val id = input.readUTF()
                            val text = input.readUTF()
                            handler.post { textReceived(this@ConnectedThread, id, text) }
                        }
                        TYPE_ACK -> {
                            val id = input.readUTF()
                            handler.post { ackReceived(this@ConnectedThread, id) }
                        }
                        TYPE_IMAGE_START -> startIncoming(input.readInt(), input.readUTF())
                        TYPE_IMAGE_CHUNK -> readChunk()
                        TYPE_IMAGE_END -> finishIncoming()
                        TYPE_IMAGE_CANCEL -> cancelIncoming()
                        TYPE_UNSEND -> {
                            val id = input.readUTF()
                            handler.post { unsendReceived(this@ConnectedThread, id) }
                        }
                        TYPE_TYPING -> {
                            val typing = input.readBoolean()
                            handler.post { typingReceived(this@ConnectedThread, typing) }
                        }
                        else -> throw IOException("Unknown packet type")
                    }
                }
            } catch (e: IOException) {
                discardIncoming()
                if (!closedByUs) handler.post { connectionLost(this@ConnectedThread) }
            }
        }

        private fun startIncoming(total: Int, id: String) {
            discardIncoming()
            incomingTotal = total
            incomingReceived = 0
            incomingId = id
            if (total <= 0 || total > MAX_IMAGE_BYTES) {
                handler.post { incomingRejected(this@ConnectedThread) }
                return
            }
            val file = ChatStore.newImageFile()
            val out = try {
                FileOutputStream(file)
            } catch (e: IOException) {
                null
            }
            if (out == null) return
            incomingFile = file
            incomingOut = out
            handler.post { incomingProgress(this@ConnectedThread, 0, total) }
        }

        private fun readChunk() {
            val length = input.readInt()
            if (length < 0 || length > MAX_CHUNK_SIZE) throw IOException("Bad chunk size")
            val data = ByteArray(length)
            input.readFully(data)

            val out = incomingOut ?: return
            try {
                out.write(data)
            } catch (e: IOException) {
                discardIncoming()
                handler.post { incomingEnded(this@ConnectedThread) }
                return
            }
            incomingReceived += length
            if (incomingReceived > incomingTotal) {
                discardIncoming()
                handler.post { incomingEnded(this@ConnectedThread) }
                return
            }
            val received = incomingReceived
            val total = incomingTotal
            handler.post { incomingProgress(this@ConnectedThread, received, total) }
        }

        private fun finishIncoming() {
            val file = incomingFile
            val id = incomingId
            val complete = incomingOut != null && incomingReceived == incomingTotal
            closeIncomingStream()
            incomingFile = null
            incomingTotal = 0
            incomingReceived = 0
            if (complete && file != null) {
                handler.post { imageReceived(this@ConnectedThread, file, id) }
            } else {
                file?.delete()
                handler.post { incomingEnded(this@ConnectedThread) }
            }
        }

        private fun cancelIncoming() {
            discardIncoming()
            handler.post { incomingEnded(this@ConnectedThread) }
        }

        private fun closeIncomingStream() {
            try {
                incomingOut?.close()
            } catch (e: IOException) {
            }
            incomingOut = null
        }

        private fun discardIncoming() {
            closeIncomingStream()
            incomingFile?.delete()
            incomingFile = null
            incomingTotal = 0
            incomingReceived = 0
        }

        fun writeHello(name: String): Boolean = try {
            synchronized(output) {
                output.writeByte(TYPE_HELLO.toInt())
                output.writeUTF(name)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }

        fun writeText(id: String, text: String): Boolean = try {
            synchronized(output) {
                output.writeByte(TYPE_MESSAGE.toInt())
                output.writeUTF(id)
                output.writeUTF(text)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }

        fun writeAck(id: String): Boolean = try {
            synchronized(output) {
                output.writeByte(TYPE_ACK.toInt())
                output.writeUTF(id)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }

        fun writeUnsend(id: String): Boolean = try {
            synchronized(output) {
                output.writeByte(TYPE_UNSEND.toInt())
                output.writeUTF(id)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }

        fun writeTyping(typing: Boolean): Boolean = try {
            synchronized(output) {
                output.writeByte(TYPE_TYPING.toInt())
                output.writeBoolean(typing)
                output.flush()
            }
            true
        } catch (e: IOException) {
            false
        }

        fun writeImage(
            file: File,
            id: String,
            transfer: ImageTransfer,
            onProgress: (Int, Int) -> Unit
        ): SendResult {
            try {
                val total = file.length().toInt()
                synchronized(output) {
                    output.writeByte(TYPE_IMAGE_START.toInt())
                    output.writeInt(total)
                    output.writeUTF(id)
                    output.flush()
                }
                val buffer = ByteArray(CHUNK_SIZE)
                var sent = 0
                FileInputStream(file).use { source ->
                    while (true) {
                        if (transfer.cancelled) {
                            synchronized(output) {
                                output.writeByte(TYPE_IMAGE_CANCEL.toInt())
                                output.flush()
                            }
                            return SendResult.CANCELLED
                        }
                        val read = source.read(buffer)
                        if (read < 0) break
                        synchronized(output) {
                            output.writeByte(TYPE_IMAGE_CHUNK.toInt())
                            output.writeInt(read)
                            output.write(buffer, 0, read)
                            output.flush()
                        }
                        sent += read
                        onProgress(sent, total)
                    }
                }
                synchronized(output) {
                    output.writeByte(TYPE_IMAGE_END.toInt())
                    output.flush()
                }
                return SendResult.SENT
            } catch (e: IOException) {
                return SendResult.FAILED
            }
        }

        fun cancel() {
            closedByUs = true
            try {
                socket.close()
            } catch (e: IOException) {
            }
        }
    }
}