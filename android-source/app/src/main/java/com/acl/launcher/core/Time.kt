package com.acl.launcher.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 时间格式化工具。 */
object Time {
    private val hhmm = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun now(): String = hhmm.format(Date())
    fun nowFull(): String = full.format(Date())
}
