package com.kherphy.bchat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.kherphy.bchat.databinding.ItemImageReceivedBinding
import com.kherphy.bchat.databinding.ItemImageSentBinding
import com.kherphy.bchat.databinding.ItemMessageReceivedBinding
import com.kherphy.bchat.databinding.ItemMessageSentBinding
import com.kherphy.bchat.databinding.ItemNoticeBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MessageAdapter(
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())

    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

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

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.type == MessageType.NOTICE -> VIEW_NOTICE
            message.imagePath != null && message.type == MessageType.SENT -> VIEW_IMAGE_SENT
            message.imagePath != null -> VIEW_IMAGE_RECEIVED
            message.type == MessageType.SENT -> VIEW_SENT
            else -> VIEW_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_SENT -> SentHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            VIEW_RECEIVED ->
                ReceivedHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            VIEW_IMAGE_SENT ->
                SentImageHolder(ItemImageSentBinding.inflate(inflater, parent, false))
            VIEW_IMAGE_RECEIVED ->
                ReceivedImageHolder(ItemImageReceivedBinding.inflate(inflater, parent, false))
            else -> NoticeHolder(ItemNoticeBinding.inflate(inflater, parent, false))
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
            is SentImageHolder -> {
                bindImage(holder.binding.ivImage, message.imagePath)
                holder.binding.ivImage.setOnClickListener {
                    message.imagePath?.let(onImageClick)
                }
                holder.binding.tvTime.text = time
            }
            is ReceivedImageHolder -> {
                bindImage(holder.binding.ivImage, message.imagePath)
                holder.binding.ivImage.setOnClickListener {
                    message.imagePath?.let(onImageClick)
                }
                holder.binding.tvTime.text = time
            }
            is NoticeHolder -> {
                holder.binding.tvNotice.text = message.text
            }
        }
    }

    private fun bindImage(view: ImageView, path: String?) {
        if (path == null) {
            view.setImageDrawable(null)
            return
        }
        val bitmap = cache.get(path) ?: decode(path)?.also { cache.put(path, it) }
        if (bitmap == null) {
            view.setImageResource(R.drawable.ic_image)
        } else {
            view.setImageBitmap(bitmap)
        }
    }

    private fun decode(path: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_SIDE) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    class SentHolder(val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root)

    class ReceivedHolder(val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root)

    class SentImageHolder(val binding: ItemImageSentBinding) :
        RecyclerView.ViewHolder(binding.root)

    class ReceivedImageHolder(val binding: ItemImageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root)

    class NoticeHolder(val binding: ItemNoticeBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_SENT = 0
        private const val VIEW_RECEIVED = 1
        private const val VIEW_NOTICE = 2
        private const val VIEW_IMAGE_SENT = 3
        private const val VIEW_IMAGE_RECEIVED = 4
        private const val THUMB_SIDE = 640
        private val CACHE_KB = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}