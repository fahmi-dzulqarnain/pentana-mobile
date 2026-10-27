package my.silentmode.pentana.shared.presentation

import my.silentmode.pentana.shared.model.LunchDto

/**
 * The Lunch tab as an agenda: one row per day, a banner for pending votes, and a sheet for voting.
 * Everything here is a pure function of the lunches, the current instant and the device's UTC
 * offset, so both apps show identical wording and recompute it on a timer to keep countdowns live.
 */
data class LunchAgenda(
    /** Null when nothing needs a vote. */
    val banner: LunchBanner?,
    val sections: List<LunchAgendaSection>,
)

data class LunchBanner(
    val title: String,
    /** One line per pending day, soonest first (at most two, then an "and N more" line). */
    val lines: List<String>,
    val actionLabel: String,
    /** The soonest-closing lunch; the banner's button opens its sheet. */
    val targetLunchId: Long,
)

data class LunchAgendaSection(val title: String, val rows: List<LunchAgendaRow>)

enum class LunchRowKind {
    /** A day before today: one compact, dimmed line. */
    Past,

    /** A closed day from today on that isn't today (e.g. tomorrow after its 4 pm deadline). */
    Closed,
    NeedsVote,

    /** Still open and already answered. */
    Ordered,
}

enum class LunchRowAction { Vote, Change, View }

data class LunchAgendaRow(
    val lunchId: Long,
    val kind: LunchRowKind,
    val isToday: Boolean,
    /** "THU" */
    val weekday: String,
    /** "29" */
    val day: String,
    /** Small label above the title on today's row ("Today · You're having"), else null. */
    val eyebrow: String?,
    /** The pick, "Not voted yet", "Not attending" or "You didn't order". */
    val title: String,
    /** The pick's dish ("Nasi Ayam Penyet"), shown beside or under the title. */
    val detail: String?,
    /** Deadline or state line ("Closes today 4:00 pm · in 6 h"). */
    val subtitle: String?,
    /** What's on offer, for days not voted yet ("Nasi Ayam Penyet or Grilled Chicken Salad"). */
    val menuPreview: String?,
    /** Under 24 h to the deadline: the subtitle is shown in the warning colour. */
    val urgent: Boolean,
    val action: LunchRowAction,
    /** VoiceOver / TalkBack label for the whole row. */
    val accessibilityLabel: String,
)

data class LunchSheet(
    val lunchId: Long,
    /** "Thursday, 29 Oct" */
    val title: String,
    /** "Closes today 4:00 pm · in 6 h", "Change by Thu 29 Oct, 4:00 pm" or "Ordering closed". */
    val status: String,
    val urgent: Boolean,
    val isOpen: Boolean,
    val menus: List<LunchSheetMenu>,
    /** Options from a server older than multi-menu days; shown read-only. */
    val legacyOptions: List<LunchSheetLegacyOption>,
    val notAttendingSelected: Boolean,
    /** Closed days: "Ordering closed: you ordered …". */
    val closedSummary: String?,
    /** Another day that still needs a vote, offered once this one is answered. */
    val next: LunchSheetNext?,
)

data class LunchSheetMenu(val heading: String, val options: List<LunchSheetOption>)
data class LunchSheetOption(val lunchOptionId: Long, val name: String, val selected: Boolean)
data class LunchSheetLegacyOption(val mealOptionId: Long, val name: String, val selected: Boolean)
data class LunchSheetNext(val lunchId: Long, val label: String)

fun lunchAgenda(lunches: List<LunchDto>, nowEpochSeconds: Long, utcOffsetSeconds: Int): LunchAgenda {
    val clock = LunchClock(nowEpochSeconds, utcOffsetSeconds)
    val days = lunches.mapNotNull { lunch -> epochDayOf(lunch.date)?.let { lunch to it } }.sortedBy { it.second }
    val thisWeekStart = clock.today - weekdayIndex(clock.today)

    val pending = days.filter { (lunch, _) -> needsVote(lunch, clock) }
        .sortedBy { (lunch, _) -> deadlineOf(lunch) ?: Long.MAX_VALUE }
    val banner = pending.firstOrNull()?.let { (first, firstDay) ->
        val lines = pending.take(2).map { (lunch, day) ->
            val deadline = deadlineOf(lunch)
            "${shortDay(day)} closes " + (deadline?.let { clock.deadlinePhrase(it) } ?: "soon")
        } + listOfNotNull(if (pending.size > 2) "and ${pending.size - 2} more" else null)
        LunchBanner(
            title = if (pending.size == 1) "1 lunch needs your vote" else "${pending.size} lunches need your vote",
            lines = lines,
            actionLabel = "Vote for ${WEEKDAYS_SHORT[weekdayIndex(firstDay)]}",
            targetLunchId = first.id,
        )
    }

    val rows = days.map { (lunch, day) -> row(lunch, day, clock) to day }
    val sections = listOf(
        "This week" to rows.filter { it.second < thisWeekStart + 7 },
        "Next week" to rows.filter { it.second >= thisWeekStart + 7 && it.second < thisWeekStart + 14 },
        "Later" to rows.filter { it.second >= thisWeekStart + 14 },
    ).filter { it.second.isNotEmpty() }.map { (title, list) -> LunchAgendaSection(title, list.map { it.first }) }

    return LunchAgenda(banner, sections)
}

fun lunchSheet(lunches: List<LunchDto>, lunchId: Long, nowEpochSeconds: Long, utcOffsetSeconds: Int): LunchSheet? {
    val clock = LunchClock(nowEpochSeconds, utcOffsetSeconds)
    val lunch = lunches.firstOrNull { it.id == lunchId } ?: return null
    val day = epochDayOf(lunch.date) ?: return null
    val open = isOpenNow(lunch, clock)
    val deadline = deadlineOf(lunch)
    val status = when {
        !open -> "Ordering closed"
        deadline == null -> "Ordering open"
        lunch.responded -> "Change by " + clock.deadlinePhrase(deadline)
        else -> "Closes " + clock.deadlinePhrase(deadline)
    }
    val notAttending = lunch.responded && lunch.myLunchOptionId == null && lunch.myMealOptionId == null
    val next = lunches.asSequence()
        .filter { it.id != lunchId && needsVote(it, clock) }
        .sortedBy { deadlineOf(it) ?: Long.MAX_VALUE }
        .firstOrNull()
        ?.let { other -> epochDayOf(other.date)?.let { LunchSheetNext(other.id, "Next: ${shortDay(it)}") } }
    return LunchSheet(
        lunchId = lunch.id,
        title = "${WEEKDAYS_LONG[weekdayIndex(day)]}, ${civilOf(day).let { "${it.day} ${MONTHS[it.month - 1]}" }}",
        status = status,
        urgent = open && deadline != null && deadline - clock.now < URGENT_SECONDS,
        isOpen = open,
        menus = lunch.menus.map { menu ->
            LunchSheetMenu(
                heading = listOfNotNull(menu.caterer, menu.menu).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "Menu" },
                options = menu.options.map {
                    LunchSheetOption(it.lunchOptionId, it.name ?: "Option", lunch.responded && lunch.myLunchOptionId == it.lunchOptionId)
                },
            )
        },
        legacyOptions = if (lunch.menus.isEmpty()) {
            lunch.options.map { LunchSheetLegacyOption(it.mealOptionId, it.name ?: "Option", lunch.myMealOptionId == it.mealOptionId) }
        } else {
            emptyList()
        },
        notAttendingSelected = notAttending,
        closedSummary = if (open) null else lunchClosedSummary(lunch),
        next = if (open && lunch.responded) next else null,
    )
}

/** Short day label used in banners, toasts and "Next:" links: "Thu 29 Oct". */
fun lunchDayLabel(lunch: LunchDto): String = epochDayOf(lunch.date)?.let { shortDay(it) } ?: lunch.date

private fun row(lunch: LunchDto, day: Long, clock: LunchClock): LunchAgendaRow {
    val open = isOpenNow(lunch, clock)
    val isToday = day == clock.today
    val deadline = deadlineOf(lunch)
    val urgent = open && deadline != null && deadline - clock.now < URGENT_SECONDS
    val pick = lunchPickedOptionName(lunch)
    val notAttending = lunch.responded && lunch.myLunchOptionId == null && lunch.myMealOptionId == null
    val answered = pick ?: if (notAttending) "Not attending" else null
    val kind = when {
        open && !lunch.responded -> LunchRowKind.NeedsVote
        open -> LunchRowKind.Ordered
        day < clock.today -> LunchRowKind.Past
        else -> LunchRowKind.Closed
    }
    val title = when (kind) {
        LunchRowKind.NeedsVote -> "Not voted yet"
        else -> answered ?: if (lunch.responded) "Order placed" else "You didn't order"
    }
    val subtitle = when (kind) {
        LunchRowKind.NeedsVote -> deadline?.let { "Closes " + clock.deadlinePhrase(it) } ?: "Ordering open"
        LunchRowKind.Ordered -> deadline?.let { "Change by " + clock.deadlinePhrase(it) }
        LunchRowKind.Closed -> if (isToday) null else "Ordering closed"
        LunchRowKind.Past -> null
    }
    val eyebrow = when {
        !isToday -> null
        pick != null && !open -> "Today · You're having"
        else -> "Today"
    }
    val detail = pickedDish(lunch)?.let { dish ->
        // Today's row has room for the caterer too; other rows stay short.
        if (isToday && !open) listOfNotNull(dish, pickedCaterer(lunch)).joinToString(" · ") else dish
    }
    val menuPreview = if (kind == LunchRowKind.NeedsVote) {
        lunch.menus.mapNotNull { it.menu ?: it.caterer }.filter { it.isNotBlank() }.joinToString(" or ").ifEmpty { lunch.menu }
    } else {
        null
    }
    val action = when (kind) {
        LunchRowKind.NeedsVote -> LunchRowAction.Vote
        LunchRowKind.Ordered -> LunchRowAction.Change
        else -> LunchRowAction.View
    }
    val spokenDay = "${WEEKDAYS_LONG[weekdayIndex(day)]} ${civilOf(day).day} ${MONTHS_LONG[civilOf(day).month - 1]}"
    return LunchAgendaRow(
        lunchId = lunch.id,
        kind = kind,
        isToday = isToday,
        weekday = WEEKDAYS_SHORT[weekdayIndex(day)].uppercase(),
        day = civilOf(day).day.toString(),
        eyebrow = eyebrow,
        title = title,
        detail = detail,
        subtitle = subtitle,
        menuPreview = menuPreview,
        urgent = urgent,
        action = action,
        accessibilityLabel = listOfNotNull(
            if (isToday) "Today, $spokenDay" else spokenDay,
            if (pick != null && isToday && !open) "you're having $title" else title,
            detail,
            subtitle,
            menuPreview,
        ).joinToString(", "),
    )
}

private const val URGENT_SECONDS = 24 * 60 * 60L

private fun needsVote(lunch: LunchDto, clock: LunchClock) = isOpenNow(lunch, clock) && !lunch.responded

/** The server's is_open, also closed once the deadline passes while the app sits open. */
private fun isOpenNow(lunch: LunchDto, clock: LunchClock): Boolean {
    val deadline = deadlineOf(lunch)
    return lunch.isOpen && (deadline == null || clock.now <= deadline)
}

private fun deadlineOf(lunch: LunchDto): Long? = lunch.deadline?.let { parseIsoInstant(it) }

private fun pickedMenu(lunch: LunchDto) =
    lunch.myLunchOptionId?.let { id -> lunch.menus.firstOrNull { m -> m.options.any { it.lunchOptionId == id } } }

private fun pickedDish(lunch: LunchDto): String? =
    pickedMenu(lunch)?.menu?.takeIf { it.isNotBlank() } ?: if (lunch.myMealOptionId != null) lunch.menu else null

private fun pickedCaterer(lunch: LunchDto): String? =
    pickedMenu(lunch)?.caterer?.takeIf { it.isNotBlank() } ?: if (lunch.myMealOptionId != null) lunch.caterer else null

private fun shortDay(epochDay: Long): String =
    civilOf(epochDay).let { "${WEEKDAYS_SHORT[weekdayIndex(epochDay)]} ${it.day} ${MONTHS[it.month - 1]}" }

private class LunchClock(val now: Long, private val offsetSeconds: Int) {
    val today: Long = (now + offsetSeconds).floorDiv(86_400L)

    /** "today 4:00 pm · in 6 h", "tomorrow 4:00 pm", "Sun 1 Nov, 4:00 pm". */
    fun deadlinePhrase(deadline: Long): String {
        val local = deadline + offsetSeconds
        val day = local.floorDiv(86_400L)
        val time = clockTime(local.mod(86_400L))
        val remaining = deadline - now
        val countdown = when {
            remaining < 0 -> null
            remaining < 60 * 60 -> "in ${maxOf(1, remaining / 60)} min"
            remaining < URGENT_SECONDS -> "in ${remaining / 3600} h"
            else -> null
        }
        val whenText = when (day) {
            today -> "today $time"
            today + 1 -> "tomorrow $time"
            else -> "${shortDay(day)}, $time"
        }
        return listOfNotNull(whenText, countdown).joinToString(" · ")
    }

    private fun clockTime(secondsOfDay: Long): String {
        val hour = (secondsOfDay / 3600).toInt()
        val minute = ((secondsOfDay % 3600) / 60).toInt()
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12:${minute.toString().padStart(2, '0')} ${if (hour < 12) "am" else "pm"}"
    }
}

private val WEEKDAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val WEEKDAYS_LONG = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val MONTHS_LONG = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** 0 = Monday … 6 = Sunday. Epoch day 0 (1970-01-01) was a Thursday. */
internal fun weekdayIndex(epochDay: Long): Int = (epochDay + 3).mod(7L).toInt()

internal data class CivilDate(val year: Int, val month: Int, val day: Int)

/** "2026-10-28" → days since 1970-01-01 (Howard Hinnant's days_from_civil). */
internal fun epochDayOf(isoDate: String): Long? {
    val parts = isoDate.take(10).split("-")
    if (parts.size != 3) return null
    val y0 = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    val d = parts[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
    val y = if (m <= 2) y0 - 1 else y0
    val era = y.floorDiv(400)
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

internal fun civilOf(epochDay: Long): CivilDate {
    val z = epochDay + 719_468L
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
    return CivilDate(y, m, d)
}

/** "2026-10-28T16:00:00+08:00" (Laravel's toIso8601String; "Z" and fractions also accepted) → epoch seconds. */
internal fun parseIsoInstant(value: String): Long? {
    if (value.length < 19 || value[10] != 'T') return null
    val day = epochDayOf(value.substring(0, 10)) ?: return null
    val hour = value.substring(11, 13).toIntOrNull() ?: return null
    val minute = value.substring(14, 16).toIntOrNull() ?: return null
    val second = value.substring(17, 19).toIntOrNull() ?: return null
    var rest = value.substring(19)
    if (rest.startsWith(".")) rest = rest.dropWhile { it == '.' || it.isDigit() }
    val offset = when {
        rest.isEmpty() || rest == "Z" -> 0
        (rest[0] == '+' || rest[0] == '-') && rest.length >= 6 -> {
            val sign = if (rest[0] == '-') -1 else 1
            val oh = rest.substring(1, 3).toIntOrNull() ?: return null
            val om = rest.substring(4, 6).toIntOrNull() ?: return null
            sign * (oh * 3600 + om * 60)
        }
        else -> return null
    }
    return day * 86_400L + hour * 3600 + minute * 60 + second - offset
}
