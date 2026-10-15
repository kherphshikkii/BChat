package com.kherphy.bchat

import android.os.Build
import android.text.InputFilter
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NameDialog {

    fun show(activity: AppCompatActivity, onDone: () -> Unit = {}) {
        val input = EditText(activity).apply {
            setText(Build.MODEL)
            setSelectAllOnFocus(true)
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(30))
        }
        val container = FrameLayout(activity).apply {
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Your name")
            .setMessage("Other phones see this name when you connect.")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                Prefs.setName(activity, input.text.toString())
                onDone()
            }
            .show()
    }
}