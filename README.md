# F1 Calendar

Android app for the Formula 1 season: race calendar, session times in your local timezone,
results and qualifying classifications, championship standings, alarms before sessions start, and
live timing while a session is running.

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

## Data sources

Both are open and need no API key or account.

**[Jolpica-F1](https://api.jolpi.ca/ergast/f1/)** — schedule, results, qualifying, standings. The
community successor to the retired Ergast API. It splits a session timestamp across two fields
(`date`: `2026-03-08`, `time`: `04:00:00Z`) and reports all scalars — including numbers — as
strings, so the DTO layer mirrors that shape verbatim and the mapper layer does the typing.

**[OpenF1](https://openf1.org)** — live timing for the Live tab. Every endpoint returns the full
matching *history* rather than a current snapshot, so the repository keeps a running picture in
memory and bounds each poll with a timestamp filter; an unbounded `intervals` request for a whole
grand prix is megabytes. Its `interval` and `gap_to_leader` fields are numbers until a driver is
lapped, at which point they become strings like `+1 LAP`, so they're parsed as raw JSON.

Every timestamp from both APIs is UTC; conversion to the display timezone happens in the UI layer
only.

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

Live timing is the one exception: it bypasses Room entirely and is polled straight into the UI.

```
app/
  core/           Res<T>, country→flag emoji, team livery colours
  data/
    remote/       Jolpica Retrofit interface, DTOs, error→message mapping
    live/         OpenF1 interface, DTOs, polling repository
    local/        Room entities, DAOs, database
    mapper/       DTO→entity→domain
    prefs/        DataStore settings
    repository/   RaceRepository, StandingsRepository
  domain/model/   Race, sessions, results, standings, alarm rules, live timing
  notifications/  scheduler, alarm receiver, boot receiver, sync worker
  ui/             calendar, live, standings, settings, nav, theme, common
  di/             Hilt modules
```

## The calendar screen

The screen rests on a single line naming the selected round. Tapping it opens a **drum picker**:
rows rotate about the X axis in proportion to their distance from the centre of the viewport,
fading and shrinking with it, so the list reads as a physical wheel turning rather than a flat list
scrolling. The transform runs in a deferred `graphicsLayer` block, so it is recalculated at draw
time on every scroll frame without recomposing the rows. Picking a round collapses it again, so the
wheel only occupies the screen while it is actually being used.

Selection is reported *continuously* while the wheel is still moving, so the page beneath tracks the
spin. Network fetches for the selected round are debounced by 350 ms so scrolling past ten rounds
doesn't fire ten requests.

Everything about the chosen round lives on that one page: where and when, every session with its
own alarm toggle and countdown, the race and qualifying classifications, and the drivers'
championship. There is no separate race-detail page — a tapped alarm spins the wheel to that round
instead of pushing a new screen.

## Themes

Settings offers seven accent palettes. Each is defined by a **single seed colour**; the full
Material 3 light and dark schemes are derived from it by re-saturating and re-lightening that hue
per colour role. Fixing saturation and value per role is what keeps contrast predictable across
every theme — body text always lands near value 0.15 on a near-white surface in light mode, and the
reverse in dark. Adding a theme is one line in `AppTheme`.

Error colours stay red regardless of the accent: an error that matches the theme stops reading as
an error.

## Alarms

Two layers decide whether a session rings, and neither is an alarm by itself:

1. a **standing rule per session type** — "every qualifying, 30 minutes ahead" — set in Settings and
   applied to every weekend on the calendar. Out of the box the race gets an hour's notice,
   qualifying and the sprint sessions half an hour, and free practice is off.
2. an optional **per-weekend override**, toggled from a race's page, for the one session that
   differs. Setting a session back to its type's default drops the override rather than pinning it,
   so later rule changes keep applying.

`NotificationScheduler.rescheduleAll()` is the single place that reconciles those preferences
against the cached calendar and the clock, and it is idempotent. It runs after any preference
change, and daily from `ScheduleSyncWorker`, which also re-runs after a reboot, an app update, or a
timezone change — alarms don't survive any of those.

Only sessions within a **10-day horizon** are armed. A season is ~24 weekends of up to five sessions
each, and holding 120 exact alarms a year out is both wasteful and pointless: the daily worker rolls
the window forward, and the API's provisional times for distant rounds change anyway.

From Android 12, exact alarms are a user-grantable permission that defaults to **denied** for apps
that aren't alarm clocks. The scheduler checks `canScheduleExactAlarms()` and falls back to
`setAndAllowWhileIdle`, which can slip by a few minutes; Settings surfaces this with a shortcut to
grant it. The notification reads the remaining time at delivery rather than trusting the configured
lead time, so a late inexact alarm doesn't claim "starts in 30 min" when it doesn't.

## Live timing

The **Live** tab only exists while a session is running — it appears about 15 minutes before the
green light and stays for 20 minutes past the scheduled end, because sessions overrun. If the tab
disappears while you're on it, the nav host moves you back to the calendar.

A race is ordered by track position and shows interval and gap to the leader. Practice and
qualifying are ranked by best lap, using the lap time actually displayed rather than OpenF1's
position field, which can disagree with it mid-session. Out-laps are excluded from best-lap
calculations.

Live data is polled every 8 seconds and held in memory only — it is meaningless outside the session
and superseded within seconds, so none of it is written to Room.

## Known limitations

- Only user preferences are included in cloud backup. The Room database is excluded — it is mostly
  rebuildable cache, and backing it up safely would mean including its `-wal`/`-shm` sidecars — so
  alarm rules and per-weekend overrides do not survive a device transfer.
- Upgrading from v1.0 runs a destructive Room migration (schema v1 → v2), which resets alarm
  preferences to their defaults.
- OpenF1 only covers recent seasons, so the Live tab is inert for historical ones. It also has no
  concept of sector-by-sector timing here — the tab shows position, interval/gap and lap times.
- Sessions the API publishes without a start time are omitted from the sessions list; the grand prix
  itself is always shown, dated even when untimed. This only affects pre-2005 seasons.
- Times display on a 24-hour clock regardless of locale, matching F1 timing convention.
- Not affiliated with Formula 1 or the FIA.
