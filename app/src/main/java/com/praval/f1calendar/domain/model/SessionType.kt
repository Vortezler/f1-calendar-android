package com.praval.f1calendar.domain.model

/**
 * The sessions a grand prix weekend can contain. Not every weekend has every session — sprint
 * weekends drop FP2/FP3 in favour of sprint qualifying and the sprint itself — so a [Race] only
 * carries the sessions the API actually reported.
 */
enum class SessionType(val label: String, val shortLabel: String) {
    FP1("Practice 1", "FP1"),
    FP2("Practice 2", "FP2"),
    FP3("Practice 3", "FP3"),
    SPRINT_QUALIFYING("Sprint Qualifying", "SQ"),
    SPRINT("Sprint", "SPR"),
    QUALIFYING("Qualifying", "QUAL"),
    RACE("Race", "RACE");

    companion object {
        fun fromName(value: String): SessionType? = entries.firstOrNull { it.name == value }
    }
}
