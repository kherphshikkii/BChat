package com.kherphy.bchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kherphy.bchat.databinding.ItemDeviceBinding

data class DeviceItem(
    val name: String,
    val address: String,
    val rssi: Int
)

class DeviceAdapter(
    private val onConnectClick: (DeviceItem) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.Holder>() {

    private val devices = mutableListOf<DeviceItem>()
    private var connectingAddress: String? = null

    val count: Int get() = devices.size

    fun addOrUpdate(item: DeviceItem) {
        val index = devices.indexOfFirst { it.address == item.address }
        if (index >= 0) {
            devices[index] = item
            notifyItemChanged(index)
        } else {
            devices.add(item)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun clear() {
        devices.clear()
        connectingAddress = null
        notifyDataSetChanged()
    }

    fun setConnecting(address: String?) {
        connectingAddress = address
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = devices.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val device = devices[position]
        val b = holder.binding
        val context = b.root.context
        val isConnecting = device.address == connectingAddress

        b.tvInitial.text = device.name.first().uppercase()
        b.tvName.text = device.name

        if (isConnecting) {
            b.rowRoot.setBackgroundColor(ContextCompat.getColor(context, R.color.warning_bg))
            b.tvSubtitle.text = "Connecting…"
            b.tvSubtitle.setTextColor(ContextCompat.getColor(context, R.color.warning_text))
            b.btnConnect.visibility = View.GONE
            b.progressConnecting.visibility = View.VISIBLE
        } else {
            b.rowRoot.setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            b.tvSubtitle.text = distanceLabel(device.rssi)
            b.tvSubtitle.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            b.btnConnect.visibility = View.VISIBLE
            b.progressConnecting.visibility = View.GONE
        }

        b.btnConnect.setOnClickListener { onConnectClick(device) }
    }

    private fun distanceLabel(rssi: Int): String = when {
        rssi == Short.MIN_VALUE.toInt() -> "Nearby"
        rssi >= -60 -> "Close by"
        rssi >= -80 -> "Nearby"
        else -> "Farther away"
    }

    class Holder(val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root)
}