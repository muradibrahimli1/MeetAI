package com.sabahhub.meetai.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = TimeUnit.SECONDS.toMinutes(totalSec)
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(epochMs))
