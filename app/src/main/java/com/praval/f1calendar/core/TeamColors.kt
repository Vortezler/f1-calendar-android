package com.praval.f1calendar.core

/**
 * Livery colour per Ergast `constructorId`, as 0xAARRGGBB. Used for the accent stripe on result
 * and standings rows. Historical teams are included so past seasons still read correctly.
 */
object TeamColors {

    private const val UNKNOWN = 0xFF9E9E9E

    private val byConstructorId: Map<String, Long> = mapOf(
        "mercedes" to 0xFF27F4D2,
        "ferrari" to 0xFFE8002D,
        "red_bull" to 0xFF3671C6,
        "mclaren" to 0xFFFF8000,
        "aston_martin" to 0xFF229971,
        "alpine" to 0xFF00A1E8,
        "williams" to 0xFF1868DB,
        "rb" to 0xFF6692FF,
        "alphatauri" to 0xFF2B4562,
        "toro_rosso" to 0xFF469BFF,
        "sauber" to 0xFF52E252,
        "alfa" to 0xFFC92D4B,
        "haas" to 0xFFB6BABD,
        "audi" to 0xFF009597,
        "cadillac" to 0xFFC4A661,
        "renault" to 0xFFFFF500,
        "racing_point" to 0xFFF596C8,
        "force_india" to 0xFFFF80C7,
        "lotus_f1" to 0xFFFFB800,
        "manor" to 0xFF323232,
        "marussia" to 0xFF6E0000,
        "caterham" to 0xFF0B361F,
        "brawn" to 0xFFB8FD6E,
        "honda" to 0xFFFFFFFF,
        "toyota" to 0xFFCC0000,
        "bmw_sauber" to 0xFF006EFF,
        "jordan" to 0xFFF9C800,
        "benetton" to 0xFF00A551,
        "jaguar" to 0xFF0A5C36,
        "minardi" to 0xFF000000,
        "tyrrell" to 0xFF0000FF,
        "lotus" to 0xFF004225,
        "brabham" to 0xFF1E5AA8,
        "team_lotus" to 0xFF004225,
    )

    fun forConstructor(constructorId: String?): Long =
        byConstructorId[constructorId?.lowercase()] ?: UNKNOWN
}
