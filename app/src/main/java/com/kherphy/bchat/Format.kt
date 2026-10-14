package com.kherphy.bchat

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {

    private val clock = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dayMonth = SimpleDateFormat("MMM d", Locale.getDefault())

    fun dayLabel(timestamp: Long): String = when {
        DateUtils.isToday(timestamp) -> "Today"
        DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> dayMonth.format(Date(timestamp))
    }

    fun listTime(timestamp: Long): String =
        if (DateUtils.isToday(timestamp)) clock.format(Date(timestamp)) else dayLabel(timestamp)
}