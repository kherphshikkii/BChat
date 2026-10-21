package com.kherphy.bchat

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kherphy.bchat.databinding.ActivityPhotoPreviewBinding

class PhotoPreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val binding = ActivityPhotoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ChatConnection.init(this)
        AvatarStore.init(this)

        val file = AvatarUtils.pendingFile(this)
        val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        if (bitmap == null) {
            finish()
            return
        }

        binding.ivPhoto.setImageBitmap(bitmap)
        val name = Prefs.getName(this)
        binding.preview56.set(name, file)
        binding.preview40.set(name, file)
        binding.preview28.set(name, file)

        binding.btnClose.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            if (AvatarStore.setMine(file)) {
                ChatConnection.sendAvatarIfConnected()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Couldn't save the photo", Toast.LENGTH_SHORT).show()
            }
        }
    }
}