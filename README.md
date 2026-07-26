# F1 Calendar

Android app for the Formula 1 season: race calendar, session times in your local timezone,
results and qualifying classifications, championship standings, and reminders before sessions start.

## Build

```bash
gradlew.bat :app:assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

```bash
gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleRelease
```

`gradle.properties` pins `org.gradle.java.home` to Android Studio's bundled JBR, because the JDK on
`PATH` (Java 26) is newer than this AGP/Gradle pair supports. Change that line if your Studio lives
somewhere else.

## Stack

Kotlin + Jetpack Compose (Material 3), MVVM with `StateFlow`, Hilt for DI, Retrofit + kotlinx.serialization
for networking, Room for the offline cache, DataStore for preferences, WorkManager + AlarmManager for
reminders. `minSdk 26`, `compileSdk`/`targetSdk 35`.

| | |
|---|---|
| AGP | 8.13.0 |
| Kotlin | 2.2.20 |
| Gradle | 9.5 |
| Compose BOM | 2025.06.01 |

## Data source

[Jolpica-F1](https://api.jolpi.ca/ergast/f1/), the community successor to the retired Ergast API.
No key required. Every timestamp it returns is UTC; conversion to the display timezone happens in
the UI layer only.

The API splits a session timestamp across two fields (`date`: `2026-03-08`, `time`: `04:00:00Z`) and
reports all scalars — including numbers — as strings, so the DTO layer mirrors that shape verbatim
and the mapper layer does the typing.

## Architecture

```
Compose UI  →  ViewModel (StateFlow)  →  Repository  →  Retrofit (remote)
                                                     →  Room (local, source of truth for reads)
```

Screens observe Room, never the network. Refreshes write into Room and the UI follows, so every
screen keeps working offline once visited, and a failed refresh shows a dismissible banner above
cached data rather than replacing it with an error page.

Cache TTLs: schedule 1h, results/qualifying 1h, standings 6h. A classification for a race that
finished more than 24h ago is treated as final and never refetched.

```
app/
  core/           Res<T>, country→flag emoji, team livery colours
  data/
    remote/       Retrofit interface, DTOs, error→message mapping
    local/        Room entities, DAOs, database
    mapper/       DTO→entity→domain
    prefs/        DataStore settings
    repository/   RaceRepository, StandingsRepository
  domain/model/   Race, sessions, results, standings
  notifications/  scheduler, alarm receiver, boot receiver, sync worker
  ui/             calendar, racedetail, standings, settings, nav, theme, common
  di/             Hilt modules
```

## Reminders

Reminder rows in Room are the source of truth for *what* the user wants; the alarms themselves are
derived state, rebuilt whenever the schedule changes, the lead time changes, the device reboots, or
the timezone changes (`ScheduleSyncWorker`, daily + on those events).

From Android 12, exact alarms are a user-grantable permission that defaults to **denied** for apps
that aren't alarm clocks. The scheduler checks `canScheduleExactAlarms()` and falls back to
`setAndAllowWhileIdle`, which can slip by a few minutes; Settings surfaces this with a shortcut to
grant it. The notification reads the remaining time at delivery rather than trusting the configured
lead time, so a late inexact alarm doesn't claim "starts in 30 min" when it doesn't.

## Known limitations

- Only user preferences are included in cloud backup. The Room database is excluded — it is mostly
  rebuildable cache, and backing it up safely would mean including its `-wal`/`-shm` sidecars — so
  starred reminders do not survive a device transfer.
- Sessions the API publishes without a start time are omitted from the sessions list; the grand prix
  itself is always shown, dated even when untimed. This only affects pre-2005 seasons.
- Times display on a 24-hour clock regardless of locale, matching F1 timing convention.
- Not affiliated with Formula 1 or the FIA.
