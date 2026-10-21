package com.kherphy.bchat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val image = ImageView(context)
    private val letter = TextView(context)

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.primary_container))
        }
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }

        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.visibility = GONE
        addView(
            image,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        letter.gravity = Gravity.CENTER
        letter.includeFontPadding = false
        letter.setTextColor(ContextCompat.getColor(context, R.color.primary))
        addView(
            letter,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    fun set(name: String, file: File?) {
        val bitmap = file?.let { load(it) }
        if (bitmap != null) {
            image.setImageBitmap(bitmap)
            image.visibility = VISIBLE
            letter.visibility = GONE
        } else {
            image.setImageDrawable(null)
            image.visibility = GONE
            letter.visibility = VISIBLE
            letter.text = name.trim().firstOrNull()?.uppercase() ?: "?"
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        letter.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.42f)
        invalidateOutline()
    }

    companion object {
        private val cache = object : LruCache<String, Bitmap>(24) {}

        private fun load(file: File): Bitmap? {
            val key = "${file.absolutePath}:${file.lastModified()}"
            cache.get(key)?.let { return it }
            val bitmap = try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (bitmap != null) cache.put(key, bitmap)
            return bitmap
        }
    }
}