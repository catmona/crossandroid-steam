package de.radicalfishgames.crosscode

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.PrintWriter
import java.io.StringWriter

class ErrorHandler(
    val hostActivity: Activity,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        t: Thread,
        e: Throwable,
    ) {
        hostActivity.runOnUiThread {
            MaterialAlertDialogBuilder(hostActivity)
                .setTitle(R.string.crash_title)
                .setMessage(R.string.crash_message)
                .setPositiveButton(R.string.crash_copy_button) { dialog: DialogInterface, which: Int ->
                    val clipboard = hostActivity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("CrossAndroid Error", errorToString(e))
                    clipboard.setPrimaryClip(clip)
                }.show()
        }
    }

    private fun errorToString(e: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        return sw.toString()
    }
}
