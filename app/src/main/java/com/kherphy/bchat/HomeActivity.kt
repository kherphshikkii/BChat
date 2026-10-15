package com.kherphy.bchat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.kherphy.bchat.databinding.ActivityHomeBinding

@SuppressLint("MissingPermission")
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var conversationAdapter: ConversationAdapter
    private var bluetoothAdapter: BluetoothAdapter? = null

    private val connectionListener = object : ChatConnection.Listener {
        override fun onStateChanged(state: ChatConnection.State) {
            if (state == ChatConnection.State.CONNECTED) openChat() else renderStatus()
        }

        override fun onIncomingRequest(name: String) {
            RequestDialog.show(this@HomeActivity, name)
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ChatConnection.refreshListening()
            renderStatus()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        ChatConnection.refreshListening()
        renderStatus()
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_CANCELED) {
            toast("Your phone is not visible to others")
        } else {
            toast("Your phone is visible for 5 minutes")
        }
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ChatConnection.init(this)
        bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter

        conversationAdapter = ConversationAdapter { openSavedChat(it) }
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = conversationAdapter

        binding.btnAction.setOnClickListener { onActionClicked() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnVisible.setOnClickListener { makeVisible() }

        if (Prefs.hasName(this)) {
            askNotificationPermission()
        } else {
            NameDialog.show(this) { askNotificationPermission() }
        }
    }

    override fun onStart() {
        super.onStart()
        ChatConnection.addListener(connectionListener)
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        ChatService.start(this)
        ChatConnection.refreshListening()
        renderStatus()
        loadConversations()
        if (ChatConnection.state == ChatConnection.State.CONNECTED) openChat()
    }

    override fun onStop() {
        super.onStop()
        ChatConnection.removeListener(connectionListener)
        unregisterReceiver(bluetoothStateReceiver)
    }

    private fun renderStatus() {
        val on = bluetoothAdapter?.isEnabled == true
        if (on) {
            val connected = ChatConnection.state == ChatConnection.State.CONNECTED
            binding.statusCard.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.input_bg))
            binding.statusDot.visibility = View.VISIBLE
            binding.ivBtOff.visibility = View.GONE
            binding.tvBtTitle.text =
                if (connected) "Bluetooth is on · Connected" else "Bluetooth is on · Not connected"
            binding.tvBtTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.tvBtSubtitle.visibility = View.GONE
            binding.btnAction.text = "Find devices"
            binding.btnAction.setIconResource(R.drawable.ic_search)
        } else {
            binding.statusCard.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.error_container))
            binding.statusDot.visibility = View.GONE
            binding.ivBtOff.visibility = View.VISIBLE
            binding.tvBtTitle.text = "Bluetooth is off"
            binding.tvBtTitle.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected))
            binding.tvBtSubtitle.text = "Turn it on to find nearby phones."
            binding.tvBtSubtitle.visibility = View.VISIBLE
            binding.btnAction.text = "Turn on Bluetooth"
            binding.btnAction.setIconResource(R.drawable.ic_bluetooth)
        }
    }

    private fun loadConversations() {
        conversationAdapter.setAll(ChatStore.conversations())
        val empty = conversationAdapter.count == 0
        binding.rvChats.visibility = if (empty) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun onActionClicked() {
        if (!BtPermissions.hasAll(this)) {
            startActivity(Intent(this, PermissionActivity::class.java))
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            toast("This phone doesn't support Bluetooth")
            return
        }
        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        startActivity(Intent(this, DevicesActivity::class.java))
    }

    private fun makeVisible() {
        if (!BtPermissions.hasAll(this)) {
            startActivity(Intent(this, PermissionActivity::class.java))
            return
        }
        if (bluetoothAdapter?.isEnabled != true) {
            toast("Turn on Bluetooth first")
            return
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        discoverableLauncher.launch(intent)
    }

    private fun openSavedChat(conversation: Conversation) {
        ChatConnection.openSavedChat(conversation.address, conversation.name)
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun openChat() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!BtPermissions.hasAll(this)) return
        if (Prefs.hasAskedNotifications(this)) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        Prefs.setAskedNotifications(this)
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}