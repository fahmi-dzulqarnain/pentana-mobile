package my.silentmode.pentana.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import my.silentmode.pentana.core.parseIso
import my.silentmode.pentana.shared.model.DashboardLunchDto
import my.silentmode.pentana.shared.model.DashboardMenuDto
import my.silentmode.pentana.shared.presentation.HomeTodo
import my.silentmode.pentana.shared.presentation.dashboardLunchLabel
import my.silentmode.pentana.shared.presentation.dashboardLunchNames
import my.silentmode.pentana.ui.components.LeadingIcon
import my.silentmode.pentana.ui.theme.ChipStripActivity
import my.silentmode.pentana.ui.theme.ChipStripActivityText
import my.silentmode.pentana.ui.theme.ChipStripLunch
import my.silentmode.pentana.ui.theme.ChipStripLunchText
import my.silentmode.pentana.ui.theme.LocalPentanaColors
import my.silentmode.pentana.ui.theme.PentanaColors
import my.silentmode.pentana.ui.theme.PentanaTheme

// ---------------------------------------------------------------------------------------------
// Text for to-do rows (pure, unit-tested)
// ---------------------------------------------------------------------------------------------

/** Display strings for one "Needs you" row. */
data class TodoText(
    val title: String,
    val detail: String,
    val cue: String,
    /** Dues title: a money figure in the dues colour. */
    val titleIsMoney: Boolean = false,
)

/** Menu names for a lunch; the rule lives in :shared so iOS words lunches the same way. */
internal fun lunchNames(lunch: DashboardLunchDto): List<String> = dashboardLunchNames(lunch)

/** "Lunch · Nasi lemak · Chicken rice", or "Lunch" when nothing is named. */
internal fun lunchLabel(lunch: DashboardLunchDto): String = dashboardLunchLabel(lunch)

/** Title, detail and cue for [todo]; [zone] places the vote deadline's weekday. */
internal fun todoText(todo: HomeTodo, zone: ZoneId = ZoneId.systemDefault()): TodoText = when (todo) {
    is HomeTodo.RejectedProof -> {
        val reason = todo.reason?.trim().orEmpty()
        TodoText(
            title = "Proof rejected",
            detail = if (reason.isEmpty()) "MYR ${todo.amount}" else "MYR ${todo.amount} · $reason",
            cue = "Resubmit",
        )
    }
    is HomeTodo.Dues -> {
        val parts = mutableListOf<String>()
        if (todo.unpaidCount > 0) parts += if (todo.unpaidCount == 1) "1 bill" else "${todo.unpaidCount} bills"
        if (todo.inReview != null) {
            parts += "MYR ${todo.inReview} in review"
        } else if ((todo.credit.toBigDecimalOrNull()?.signum() ?: 0) > 0) {
            parts += "MYR ${todo.credit} credit"
        }
        TodoText(
            title = "MYR ${todo.amountDue} due",
            // Never a blank line: with nothing else to say, fall back to the bill count.
            detail = parts.ifEmpty { listOf("${todo.unpaidCount} bills") }.joinToString(" · "),
            cue = "Pay & upload",
            titleIsMoney = true,
        )
    }
    is HomeTodo.LunchVote -> {
        val lunch = todo.lunch
        val parts = mutableListOf<String>()
        if (lunch.menus.size > 1) {
            parts += "${lunch.menus.size} menus"
        } else {
            lunchNames(lunch).firstOrNull()?.let { parts += it }
        }
        lunch.voteDeadline?.let { deadline -> zoned(deadline, zone)?.let { parts += "closes ${weekday(it.toLocalDate())}" } }
        val day = ymd(lunch.date)?.let(::weekday) ?: lunch.date
        TodoText(
            title = "Vote for $day lunch",
            detail = parts.ifEmpty { listOf("Voting is open") }.joinToString(" · "),
            cue = "Vote",
        )
    }
    is HomeTodo.SupportReply -> TodoText(
        title = "Committee replied",
        detail = when {
            todo.count == 1 && !todo.subject.isNullOrBlank() -> todo.subject!!
            todo.count == 1 -> "1 ticket waiting on you"
            else -> "${todo.count} tickets waiting on you"
        },
        cue = "Reply",
    )
}

// ---------------------------------------------------------------------------------------------
// Dates (device time zone; English labels to match the rest of the app)
// ---------------------------------------------------------------------------------------------

private val WeekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

/** "2026-10-30" -> LocalDate, or null. */
internal fun ymd(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

/** ISO-8601 timestamp -> date-time in [zone], or null. */
internal fun zoned(iso: String, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? =
    parseIso(iso)?.let { Instant.ofEpochMilli(it).atZone(zone) }

/** "Fri" */
internal fun weekday(date: LocalDate): String = date.format(WeekdayFormat)

/** "8:00 PM" or "20:00", following the device's 12/24-hour setting. */
internal fun timeOfDay(time: ZonedDateTime, is24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.ENGLISH))

// ---------------------------------------------------------------------------------------------
// Section surface, header, divider
// ---------------------------------------------------------------------------------------------

/** One Home section: a tonal surfaceContainer card, 16dp radius, no elevation. */
@Composable
internal fun HomeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** Hairline between rows inside a [HomeCard]. */
@Composable
internal fun HomeRowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** 15sp semibold, sentence case, primary label; an optional count in secondary text. */
@Composable
internal fun SectionHeader(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (count != null) {
            Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Accessibility text sizes (font scale >= 1.5): rows stack the cue and lift the 1-line limit. */
@Composable
internal fun isLargeText(): Boolean = LocalDensity.current.fontScale >= 1.5f

@Composable
internal fun HomeChevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp),
    )
}

/** Circular 36dp domain icon tile. */
@Composable
internal fun HomeIcon(icon: ImageVector, tint: Color, container: Color) {
    LeadingIcon(icon, container, tint, size = 36.dp, iconSize = 18.dp, radius = 18.dp)
}

// ---------------------------------------------------------------------------------------------
// To-do row
// ---------------------------------------------------------------------------------------------

/** Icon and tint for a to-do type. */
internal data class TodoStyle(val icon: ImageVector, val tint: Color, val container: Color)

internal fun todoStyle(todo: HomeTodo, colors: PentanaColors): TodoStyle = when (todo) {
    is HomeTodo.RejectedProof -> TodoStyle(Icons.Rounded.Warning, colors.bad.color, colors.bad.container)
    is HomeTodo.Dues -> TodoStyle(Icons.Rounded.CreditCard, colors.dues.color, colors.dues.container)
    is HomeTodo.LunchVote -> TodoStyle(Icons.Rounded.Restaurant, colors.lunch.color, colors.lunch.container)
    is HomeTodo.SupportReply -> TodoStyle(Icons.Rounded.Forum, colors.proof.color, colors.proof.container)
}

/**
 * One "Needs you" row: icon tile, title + detail (1 line each, "…"), cue pill. The whole row is one
 * tap target and one screen-reader element ("{title}, {detail}. {cue}, button"); the pill is visual
 * only. At font scale >= 1.5 the pill moves below the text and the line limit is lifted.
 */
@Composable
internal fun TodoRow(style: TodoStyle, text: TodoText, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val large = isLargeText()
    val colors = LocalPentanaColors.current
    val maxLines = if (large) Int.MAX_VALUE else 1
    val texts: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text.title,
                style = if (text.titleIsMoney) {
                    TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                } else {
                    TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                },
                color = if (text.titleIsMoney) colors.dues.color else MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text.detail,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${text.title}, ${text.detail}. ${text.cue}" }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (large) 12.dp else 8.dp),
        verticalAlignment = if (large) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        HomeIcon(style.icon, style.tint, style.container)
        if (large) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                texts()
                CuePill(text.cue)
            }
        } else {
            Box(Modifier.weight(1f)) { texts() }
            CuePill(text.cue)
        }
    }
}

/** The indigo (light) / light-orange (dark) 12.5sp semibold pill used as the to-do cue. */
@Composable
internal fun CuePill(text: String, modifier: Modifier = Modifier) {
    val cue = LocalPentanaColors.current.cue
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(cue.container)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clearAndSetSemantics {},
        color = cue.color,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------------------------------------
// Coming up row, pill, date chip
// ---------------------------------------------------------------------------------------------

/** A "Coming up" row: leading chip or icon, 1-line title, a detail slot, and a chevron. One tap target. */
@Composable
internal fun ComingUpRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (isLargeText()) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
            )
            detail()
        }
        HomeChevron()
    }
}

/** Secondary detail text under a Coming up title. */
@Composable
internal fun DetailText(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isLargeText()) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Small status pill for Coming up rows. */
@Composable
internal fun HomePill(text: String, color: Color, container: Color) {
    Text(
        text,
        modifier = Modifier.clip(CircleShape).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

enum class DateChipKind { Lunch, Activity }

private val ChipWeekdayStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
private val ChipDayStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
private val ChipWeekdays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val ChipTextPadding = 8.dp

/**
 * The one width every date chip uses at the current font scale: 40dp at default text size, or the
 * widest weekday / two-digit day plus padding if that is wider. Measuring the widest case, not each
 * chip's own text, keeps the rows in a list aligned at any font scale (font scaling is non-linear,
 * so "MON" and "THU" grow by different amounts) and the weekday never clips.
 */
@Composable
private fun dateChipWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density) {
        val widestText = maxOf(
            ChipWeekdays.maxOf { measurer.measure(it, ChipWeekdayStyle).size.width },
            measurer.measure("00", ChipDayStyle).size.width,
        )
        maxOf(40.dp, with(density) { widestText.toDp() } + ChipTextPadding)
    }
}

/** A calendar tile: a coloured weekday strip over the day number, on the surface colour. */
@Composable
internal fun DateChip(weekday: String, day: String, kind: DateChipKind, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .width(dateChipWidth())
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
            .clearAndSetSemantics { contentDescription = "$weekday $day" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            weekday.uppercase(Locale.ENGLISH),
            modifier = Modifier
                .fillMaxWidth()
                .background(if (kind == DateChipKind.Lunch) ChipStripLunch else ChipStripActivity)
                .padding(horizontal = ChipTextPadding / 2, vertical = 2.dp),
            color = if (kind == DateChipKind.Lunch) ChipStripLunchText else ChipStripActivityText,
            style = ChipWeekdayStyle,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            day,
            modifier = Modifier.padding(horizontal = ChipTextPadding / 2).padding(top = 1.dp, bottom = 3.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = ChipDayStyle,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Loading skeleton
// ---------------------------------------------------------------------------------------------

/** Placeholder for "Needs you" and "Coming up" (two rows each), shown under the real greeting. */
@Composable
internal fun HomeSkeleton(modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Loading" }) {
        SkeletonBar(fill, 0.4f, Modifier.padding(top = 18.dp, bottom = 8.dp))
        SkeletonGroup(fill, chipWidth = 36, chipHeight = 36, titleWidth = 0.7f, detailWidth = 0.45f, circle = true)
        SkeletonBar(fill, 0.35f, Modifier.padding(top = 22.dp, bottom = 8.dp))
        SkeletonGroup(fill, chipWidth = 40, chipHeight = 42, titleWidth = 0.6f, detailWidth = 0.3f, circle = false)
    }
}

@Composable
private fun SkeletonBar(fill: Color, fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(start = 4.dp)
            .fillMaxWidth(fraction)
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(fill),
    )
}

@Composable
private fun SkeletonGroup(fill: Color, chipWidth: Int, chipHeight: Int, titleWidth: Float, detailWidth: Float, circle: Boolean) {
    HomeCard {
        repeat(2) { index ->
            if (index > 0) HomeRowDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    Modifier
                        .size(chipWidth.dp, chipHeight.dp)
                        .clip(if (circle) CircleShape else RoundedCornerShape(10.dp))
                        .background(fill),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth(titleWidth).height(12.dp).clip(RoundedCornerShape(6.dp)).background(fill))
                    Box(Modifier.fillMaxWidth(detailWidth).height(10.dp).clip(RoundedCornerShape(6.dp)).background(fill))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val PreviewTodos = listOf(
    HomeTodo.RejectedProof(1, "60.00", "Blurry photo, please retake it in better light"),
    HomeTodo.Dues(amountDue = "120.00", unpaidCount = 2, credit = "15.00", inReview = null),
    HomeTodo.LunchVote(
        DashboardLunchDto(
            id = 3, date = "2026-10-30", isOpen = true, responded = false,
            menus = listOf(DashboardMenuDto(menu = "Nasi lemak"), DashboardMenuDto(menu = "Chicken rice")),
            voteDeadline = "2026-10-29T10:00:00+08:00",
        ),
    ),
    HomeTodo.SupportReply(1, 7, "Can I pay the October dues in two parts?"),
)

@Composable
private fun HomeComponentsPreview() {
    val colors = LocalPentanaColors.current
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        SectionHeader("Needs you", count = PreviewTodos.size)
        HomeCard {
            PreviewTodos.forEachIndexed { index, todo ->
                if (index > 0) HomeRowDivider()
                TodoRow(todoStyle(todo, colors), todoText(todo), onClick = {})
            }
        }
        SectionHeader("Coming up")
        HomeCard {
            ComingUpRow(
                title = "Futsal night",
                onClick = {},
                leading = { DateChip("Sat", "7", DateChipKind.Activity) },
                detail = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        HomePill("Registered", colors.okStrong, colors.ok.container)
                        DetailText("8:00 PM")
                    }
                },
            )
            HomeRowDivider()
            ComingUpRow(
                title = "Lunch · Nasi lemak",
                onClick = {},
                leading = { DateChip("Fri", "30", DateChipKind.Lunch) },
                detail = { HomePill("Voted", colors.okStrong, colors.ok.container) },
            )
        }
        Spacer(Modifier.height(8.dp))
        HomeSkeleton()
    }
}

@Preview(name = "Home components · light", showBackground = true)
@Composable
private fun HomeComponentsLightPreview() {
    PentanaTheme(dark = false, dynamic = false) { HomeComponentsPreview() }
}

@Preview(name = "Home components · dark", showBackground = true)
@Composable
private fun HomeComponentsDarkPreview() {
    PentanaTheme(dark = true, dynamic = false) { HomeComponentsPreview() }
}

@Preview(name = "Home components · font 1.5", showBackground = true, fontScale = 1.5f)
@Composable
private fun HomeComponentsLargeTextPreview() {
    PentanaTheme(dark = false, dynamic = false) { HomeComponentsPreview() }
}
