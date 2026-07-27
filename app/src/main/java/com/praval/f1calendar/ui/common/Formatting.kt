package com.praval.f1calendar.ui.common

/**
 * Points are whole numbers in the modern era, but half points exist — the 1950s awarded them for a
 * shared fastest lap, and a shortened race pays half, as at Spa in 2021.
 */
fun Double.formatPoints(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
