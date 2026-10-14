package com.kherphy.bchat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.kherphy.bchat.databinding.ActivityDevicesBinding
import android.text.InputFilter
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder

@SuppressLint("MissingPermission")
class DevicesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevicesBinding
    private lateinit var deviceAdapter: DeviceAdapter
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private var receiverRegistered = false

    private val connectionListener = object : ChatConnection.Listener {
        override fun onStateChanged(state: ChatConnection.State) {
            when (state) {
                ChatConnection.State.CONNECTED -> openChat()
                ChatConnection.State.CONNECTING ->
                    deviceAdapter.setConnecting(ChatConnection.remoteAddress)
                else -> deviceAdapter.setConnecting(null)
            }
        }

        override fun onConnectionFailed(reason: String) {
            toast(reason)
        }
        override fun onIncomingRequest(name: String) {
            RequestDialog.show(this@DevicesActivity, name)
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter?.isEnabled == true) {
            startScan()
        } else {
            toast("Bluetooth needs to be on to find phones")
            showFinished()
        }
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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = getDevice(intent) ?: return
                    val name = device.name
                    if (name.isNullOrBlank()) return
                    val rssi = intent.getShortExtra(
                        BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
                    ).toInt()
                    deviceAdapter.addOrUpdate(DeviceItem(name, device.address, rssi))
                    binding.tvScanStatus.text = foundText(scanningNow = true)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                    showFinished()
                }
            }
        }
    }

    private fun askForNameIfNeeded() {
        if (Prefs.hasName(this)) return

        val input = EditText(this).apply {
            setText(Build.MODEL)
            setSelectAllOnFocus(true)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(30))
        }
        val container = FrameLayout(this).apply {
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Your name")
            .setMessage("Other phones see this name when you connect.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setName(this, input.text.toString())
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)


        if (!hasPermissions()) {
            startActivity(Intent(this, PermissionActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityDevicesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ChatConnection.init(this)
        bluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter

        setupList()
        setupButtons()
        registerScanReceiver()
        startScan()
        askForNameIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        ChatConnection.addListener(connectionListener)
        if (ChatConnection.state == ChatConnection.State.CONNECTED) openChat()
    }

    override fun onStop() {
        super.onStop()
        ChatConnection.removeListener(connectionListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(receiver)
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    private fun setupList() {
        deviceAdapter = DeviceAdapter { device -> onConnectClicked(device) }
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnScan.setOnClickListener {
            if (scanning) stopScan() else startScan()
        }
        binding.btnScanAgain.setOnClickListener { startScan() }

        binding.btnVisible.setOnClickListener {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // 300 seconds
            discoverableLauncher.launch(intent)
        }
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun startScan() {
        if (ChatConnection.state == ChatConnection.State.CONNECTING) return

        val adapter = bluetoothAdapter
        if (adapter == null) {
            toast("This phone doesn't support Bluetooth")
            return
        }
        if (!adapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        ChatConnection.startListening()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationOn()) {
            toast("Turn on Location so Android can scan for devices")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        if (adapter.isDiscovering) adapter.cancelDiscovery()
        deviceAdapter.clear()

        if (adapter.startDiscovery()) {
            scanning = true
            showScanning()
        } else {
            toast("Couldn't start scanning. Please try again.")
            showFinished()
        }
    }

    private fun stopScan() {
        bluetoothAdapter?.cancelDiscovery()
    }

    private fun onConnectClicked(device: DeviceItem) {
        stopScan()
        Toast.makeText(
            this,
            "If Android asks, confirm the pairing code on both phones",
            Toast.LENGTH_LONG
        ).show()
        ChatConnection.connect(device.address)
    }

    private fun openChat() {
        stopScan()
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun showScanning() {
        binding.progress.visibility = View.VISIBLE
        binding.tvScanStatus.visibility = View.VISIBLE
        binding.tvScanStatus.text = foundText(scanningNow = true)
        binding.rvDevices.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.btnScan.visibility = View.VISIBLE
        binding.btnScan.text = "Stop scanning"
    }

    private fun showFinished() {
        binding.progress.visibility = View.INVISIBLE
        if (deviceAdapter.count == 0) {
            binding.tvScanStatus.visibility = View.GONE
            binding.rvDevices.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.btnScan.visibility = View.GONE
        } else {
            binding.tvScanStatus.visibility = View.VISIBLE
            binding.tvScanStatus.text = foundText(scanningNow = false)
            binding.rvDevices.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.btnScan.visibility = View.VISIBLE
            binding.btnScan.text = "Scan again"
        }
    }

    private fun foundText(scanningNow: Boolean): String {
        val n = deviceAdapter.count
        val devices = if (n == 1) "1 device" else "$n devices"
        return if (scanningNow) "Scanning… $devices found" else "Scan finished · $devices found"
    }

    private fun hasPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationOn(): Boolean {
        val manager = getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @Suppress("DEPRECATION")
    private fun getDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}