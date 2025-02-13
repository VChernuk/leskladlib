package com.kophe.leskladlib.repository.common

import com.kophe.leskladlib.logging.Logger
import com.kophe.leskladlib.logging.LoggingUtil
import com.kophe.leskladlib.replaceUnsupportedChars
import com.kophe.leskladlib.transliterate
import com.kophe.leskladlib.validated
import java.util.Date

abstract class BaseRepository(override val loggingUtil: LoggingUtil) : Logger {

    internal fun key(date: Date, item: Item) = key(date, item.titleString())

    internal fun key(date: Date, title: String) =
        (title + "_" + date.hashCode().toString()).validated().transliterate()
            .replaceUnsupportedChars()

}
