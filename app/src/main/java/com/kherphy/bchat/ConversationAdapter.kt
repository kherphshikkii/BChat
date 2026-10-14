package com.kherphy.bchat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kherphy.bchat.databinding.ItemConversationBinding

class ConversationAdapter(
    private val onClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.Holder>() {

    private val items = mutableListOf<Conversation>()

    val count: Int get() = items.size

    fun setAll(list: List<Conversation>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val b = holder.binding
        b.tvInitial.text = item.name.firstOrNull()?.uppercase() ?: "?"
        b.tvName.text = item.name
        b.tvLast.text = item.lastText
        b.tvTime.text = Format.listTime(item.lastTime)
        b.root.setOnClickListener { onClick(item) }
    }

    class Holder(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)
}