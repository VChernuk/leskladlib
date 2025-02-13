package com.kophe.leskladlib.logging

import android.content.Context
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import android.os.Build.VERSION.SDK_INT
import com.kophe.leskladlib.loggingTag
import com.kophe.leskladlib.timestampToFormattedDate24h
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

interface Logger {

    val loggingUtil: LoggingUtil
    fun log(info: String?) {
        loggingUtil.log("${loggingTag()} $info")
    }

}

abstract class LoggingUtil(
    context: Context,
    private val versionName: String,
    protected val debug: Boolean
) {

    protected abstract val fileName: String
    protected abstract val maxLines: Int

    private val appContext = WeakReference(context)
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val deviceInfo = "$MANUFACTURER $MODEL android $SDK_INT build $versionName"

    open fun log(info: String?) {
        fileExecutor.submit {
            val prefix = timePrefix()
            try {
                val logFile = logFile() ?: return@submit
                removeExtraLinesIfNeeded(logFile)
                FileWriter(logFile, true).use { writer ->
                    writer.append("\n")
                    writer.append("$prefix : $info")
                    writer.flush()
                    writer.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    open fun logFile(): File? {
        val context = appContext.get() ?: return null
        return try {
            val root = File(context.filesDir, "log")
            if (!root.exists()) root.mkdirs()
            val logFile = File(root, fileName)
            if (!logFile.exists()) {
                logFile.createNewFile()
                writeDeviceInfoHeader(logFile)
            } else {
                logFile.useLines { it.firstOrNull() }?.let {
                    if (it.contains(versionName)) return@let
                    removeLines(logFile)
                    writeDeviceInfoHeader(logFile)
                }
            }
            logFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun writeDeviceInfoHeader(logFile: File) {
        FileWriter(logFile, false).use { writer ->
            writer.append(deviceInfo)
            writer.append("\n")
            writer.flush()
            writer.close()
        }
    }

    private fun removeExtraLinesIfNeeded(logFile: File) = removeLines(logFile)

    private fun removeLines(logFile: File) {
        val lines = logFile.readLines()
        val count = lines.count()
        if (count <= maxLines) return
        val resultLines = lines.take(2) + lines.drop(51)
        logFile.writeText(resultLines.joinToString(System.lineSeparator()))
    }

    private fun timePrefix() = System.currentTimeMillis().timestampToFormattedDate24h()

}
