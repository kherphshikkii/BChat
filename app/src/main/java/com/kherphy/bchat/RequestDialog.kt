package com.kherphy.bchat

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object RequestDialog {

    private var dialog: AlertDialog? = null

    fun show(activity: AppCompatActivity, name: String) {
        if (activity.isFinishing) return
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Connection request")
            .setMessage("$name wants to chat with you.")
            .setCancelable(false)
            .setPositiveButton("Accept") { _, _ -> ChatConnection.acceptPending() }
            .setNegativeButton("Decline") { _, _ -> ChatConnection.rejectPending() }
            .show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}