package com.kherphy.bchat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kherphy.bchat.databinding.ItemMessageReceivedBinding
import com.kherphy.bchat.databinding.ItemMessageSentBinding
import com.kherphy.bchat.databinding.ItemNoticeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
    fun setAll(list: List<Message>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int = messages[position].type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (MessageType.values()[viewType]) {
            MessageType.SENT ->
                SentHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            MessageType.RECEIVED ->
                ReceivedHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            MessageType.NOTICE ->
                NoticeHolder(ItemNoticeBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val time = timeFormat.format(Date(message.timestamp))
        when (holder) {
            is SentHolder -> {
                holder.binding.tvMessage.text = message.text
                holder.binding.tvTime.text = time
            }
            is ReceivedHolder -> {
                holder.binding.tvMessage.text = message.text
                holder.binding.tvTime.text = time
            }
            is NoticeHolder -> {
                holder.binding.tvNotice.text = message.text
            }
        }
    }

    class SentHolder(val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root)

    class ReceivedHolder(val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root)

    class NoticeHolder(val binding: ItemNoticeBinding) :
        RecyclerView.ViewHolder(binding.root)
}