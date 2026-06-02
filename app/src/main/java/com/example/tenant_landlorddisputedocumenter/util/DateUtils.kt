package com.example.tenant_landlorddisputedocumenter.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Date / time formatting helpers. All formats use the device locale by default; for embedding into
 * legal evidence we also expose an ISO-8601 UTC formatter that's parseable on any platform.
 */
object DateUtils {
    private val readable = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
    private val shortDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val isoUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun formatReadable(millis: Long?): String = millis?.let { readable.format(Date(it)) } ?: "—"

    fun formatShortDate(millis: Long?): String = millis?.let { shortDate.format(Date(it)) } ?: "—"

    fun formatIsoUtc(millis: Long): String = isoUtc.format(Date(millis))
}
