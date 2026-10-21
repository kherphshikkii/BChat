package com.kherphy.bchat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kherphy.bchat.databinding.ItemImageReceivedBinding
import com.kherphy.bchat.databinding.ItemImageSentBinding
import com.kherphy.bchat.databinding.ItemMessageReceivedBinding
import com.kherphy.bchat.databinding.ItemMessageSentBinding
import com.kherphy.bchat.databinding.ItemNoticeBinding
import com.kherphy.bchat.databinding.ItemUnsentReceivedBinding
import com.kherphy.bchat.databinding.ItemUnsentSentBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MessageAdapter(
    private val onImageClick: (String) -> Unit,
    private val onLongClick: (Message) -> Unit,
    private val onQuoteClick: (String) -> Unit,
    private val onReactionClick: (Message) -> Unit,
    private val nameProvider: () -> String,
    private val avatarProvider: () -> File?
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()
    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())

    private val cache = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
        if (messages.size >= 2) notifyItemChanged(messages.size - 2)
    }

    fun setAll(list: List<Message>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun replace(message: Message) {
        val index = messages.indexOfFirst { it.id == message.id }
        if (index < 0) return
        messages[index] = message
        val from = max(0, index - 1)
        val to = min(messages.size - 1, index + 1)
        notifyItemRangeChanged(from, to - from + 1)
    }

    fun remove(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        messages.removeAt(index)
        notifyItemRemoved(index)
        if (index > 0) notifyItemChanged(index - 1)
        if (index < messages.size) notifyItemChanged(index)
    }

    fun refreshAll() {
        notifyDataSetChanged()
    }

    fun positionOf(id: String): Int = messages.indexOfFirst { it.id == id }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.type == MessageType.NOTICE -> VIEW_NOTICE
            message.deleted && message.type == MessageType.SENT -> VIEW_UNSENT_SENT
            message.deleted -> VIEW_UNSENT_RECEIVED
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
            VIEW_UNSENT_SENT ->
                UnsentSentHolder(ItemUnsentSentBinding.inflate(inflater, parent, false))
            VIEW_UNSENT_RECEIVED ->
                UnsentReceivedHolder(ItemUnsentReceivedBinding.inflate(inflater, parent, false))
            else -> NoticeHolder(ItemNoticeBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val time = timeFormat.format(Date(message.timestamp))
        val first = isFirstInRun(position)
        val last = isLastInRun(position)
        when (holder) {
            is SentHolder -> {
                val b = holder.binding
                applySpacing(b.root, first)
                b.tvMessage.text = message.text
                bindTime(b.tvTime, time, last, message.pending)
                bindQuote(b.quoteBlock, b.tvQuoteName, b.tvQuoteText, message)
                bindReactions(b.tvReactions, message)
                b.bubble.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
            is ReceivedHolder -> {
                val b = holder.binding
                applySpacing(b.root, first)
                b.tvMessage.text = message.text
                bindTime(b.tvTime, time, last, false)
                bindAvatar(b.avatar, last)
                bindQuote(b.quoteBlock, b.tvQuoteName, b.tvQuoteText, message)
                bindReactions(b.tvReactions, message)
                b.bubble.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
            is SentImageHolder -> {
                val b = holder.binding
                applySpacing(b.root, first)
                bindImage(b.ivImage, message.imagePath)
                bindTime(b.tvTime, time, last, false)
                bindReactions(b.tvReactions, message)
                b.ivImage.setOnClickListener { message.imagePath?.let(onImageClick) }
                b.ivImage.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
            is ReceivedImageHolder -> {
                val b = holder.binding
                applySpacing(b.root, first)
                bindImage(b.ivImage, message.imagePath)
                bindTime(b.tvTime, time, last, false)
                bindAvatar(b.avatar, last)
                bindReactions(b.tvReactions, message)
                b.ivImage.setOnClickListener { message.imagePath?.let(onImageClick) }
                b.ivImage.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
            is UnsentSentHolder -> {
                holder.binding.tvUnsent.text = "You unsent a message"
                holder.binding.tvUnsent.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
            is UnsentReceivedHolder -> {
                holder.binding.tvUnsent.text = "${nameProvider()} unsent a message"
                holder.binding.tvUnsent.setOnLongClickListener {
                    onLongClick(message)
                    true
                }
            }
            is NoticeHolder -> holder.binding.tvNotice.text = message.text
        }
    }

    private fun sameRun(a: Message?, b: Message?): Boolean {
        if (a == null || b == null) return false
        if (a.type == MessageType.NOTICE || b.type == MessageType.NOTICE) return false
        if (a.deleted || b.deleted) return false
        if (a.type != b.type) return false
        return abs(b.timestamp - a.timestamp) < RUN_GAP_MS
    }

    private fun isFirstInRun(position: Int): Boolean =
        !sameRun(messages.getOrNull(position - 1), messages[position])

    private fun isLastInRun(position: Int): Boolean =
        !sameRun(messages[position], messages.getOrNull(position + 1))

    private fun applySpacing(root: View, first: Boolean) {
        val density = root.resources.displayMetrics.density
        val top = ((if (first) 10 else 2) * density).toInt()
        val bottom = (2 * density).toInt()
        root.setPadding(0, top, 0, bottom)
    }

    private fun bindTime(view: TextView, time: String, show: Boolean, pending: Boolean) {
        if (show || pending) {
            view.visibility = View.VISIBLE
            view.text = if (pending) "Waiting to send · $time" else time
        } else {
            view.visibility = View.GONE
        }
    }

    private fun bindAvatar(avatar: AvatarView, visible: Boolean) {
        if (visible) {
            avatar.visibility = View.VISIBLE
            avatar.set(nameProvider(), avatarProvider())
        } else {
            avatar.visibility = View.INVISIBLE
        }
    }

    private fun bindReactions(view: TextView, message: Message) {
        val mine = message.myReaction
        val theirs = message.theirReaction
        if (mine.isEmpty() && theirs.isEmpty()) {
            view.visibility = View.GONE
            view.setOnClickListener(null)
            view.setOnLongClickListener(null)
            return
        }
        val reactions = listOf(mine, theirs).filter { it.isNotEmpty() }
        val emojis = reactions.distinct().joinToString("")
        view.text = if (reactions.size > 1) "$emojis ${reactions.size}" else emojis
        view.visibility = View.VISIBLE
        view.setOnClickListener { onReactionClick(message) }
        view.setOnLongClickListener {
            onLongClick(message)
            true
        }
    }

    private fun bindQuote(
        block: View,
        nameView: TextView,
        textView: TextView,
        message: Message
    ) {
        val replyId = message.replyToId
        if (replyId == null) {
            block.visibility = View.GONE
            block.setOnClickListener(null)
            block.setOnLongClickListener(null)
            block.isClickable = false
            return
        }
        block.visibility = View.VISIBLE
        nameView.text = if (message.replyMine) "You" else nameProvider()
        val unavailable = message.replyText.isEmpty()
        textView.text = if (unavailable) "Message unsent" else message.replyText
        textView.setTypeface(null, if (unavailable) Typeface.ITALIC else Typeface.NORMAL)
        block.setOnClickListener { onQuoteClick(replyId) }
        block.setOnLongClickListener {
            onLongClick(message)
            true
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

    class UnsentSentHolder(val binding: ItemUnsentSentBinding) :
        RecyclerView.ViewHolder(binding.root)

    class UnsentReceivedHolder(val binding: ItemUnsentReceivedBinding) :
        RecyclerView.ViewHolder(binding.root)

    class NoticeHolder(val binding: ItemNoticeBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_SENT = 0
        private const val VIEW_RECEIVED = 1
        private const val VIEW_NOTICE = 2
        private const val VIEW_IMAGE_SENT = 3
        private const val VIEW_IMAGE_RECEIVED = 4
        private const val VIEW_UNSENT_SENT = 5
        private const val VIEW_UNSENT_RECEIVED = 6
        private const val THUMB_SIDE = 640
        private const val RUN_GAP_MS = 5 * 60 * 1000L
        private val CACHE_KB = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}