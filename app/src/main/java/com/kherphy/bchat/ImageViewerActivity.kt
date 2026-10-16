package com.kherphy.bchat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kherphy.bchat.databinding.ActivityImageViewerBinding

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val path = intent.getStringExtra(EXTRA_PATH)
        val bitmap = path?.let { decode(it) }
        if (bitmap == null) {
            Toast.makeText(this, "Couldn't open this photo", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.ivFull.setImageBitmap(bitmap)
        binding.ivFull.setOnClickListener { finish() }
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun decode(path: String): Bitmap? =
        try {
            BitmapFactory.decodeFile(path)
        } catch (e: OutOfMemoryError) {
            null
        }

    companion object {
        private const val EXTRA_PATH = "path"

        fun open(context: Context, path: String) {
            context.startActivity(
                Intent(context, ImageViewerActivity::class.java).putExtra(EXTRA_PATH, path)
            )
        }
    }
}