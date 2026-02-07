package com.verifylabs.ai.core.util

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.TextView
import com.verifylabs.ai.R

object DialogUtils {

    fun showIosErrorDialog(
        context: Context,
        title: String,
        message: String,
        onDismiss: (() -> Unit)? = null // Default null for backward compatibility
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_ios_error, null)
        
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val btnOk = dialogView.findViewById<TextView>(R.id.btnOk)

        tvTitle.text = title
        tvMessage.text = message

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnOk.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        // Essential for rounded corners with card view
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        dialog.show()
    }
}
