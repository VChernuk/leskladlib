package com.kophe.leskladlib.logging

import android.content.Context

class ProdLoggingUtil(context: Context, versionName: String, debug: Boolean) :
    LoggingUtil(context, versionName, debug) {

    override val fileName: String = "le_sklad_log.txt"
    override val maxLines: Int = 1000

}
