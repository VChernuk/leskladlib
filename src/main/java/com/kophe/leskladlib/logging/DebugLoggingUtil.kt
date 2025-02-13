package com.kophe.leskladlib.logging

import android.content.Context
import android.util.Log

class DebugLoggingUtil(context: Context, versionName: String, debug: Boolean) :
    LoggingUtil(context, versionName, debug) {

    override val fileName: String = "le_sklad_debug_log.txt"
    override val maxLines: Int = 5000

    override fun log(info: String?) {
        if (!debug || info == null) return
        Log.d("le_sklad", info)
        super.log(info)
    }

}
