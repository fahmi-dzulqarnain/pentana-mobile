package my.silentmode.pentana.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import my.silentmode.pentana.ui.theme.LocalPentanaColors

enum class ChipKind { Paid, Partial, Unpaid, Overdue, Registered, Waitlisted, Open, Closed, VoteNow, Responded }

private data class ChipSpec(
    val color: Color,
    val bg: Color?,
    val text: String,
    val icon: ImageVector? = null,
    val dot: Boolean = false,
    val outline: Boolean = false,
)

/** Status pill — colour AND label (never colour alone). Optional [label] overrides the default text. */
@Composable
fun StatusChip(kind: ChipKind, modifier: Modifier = Modifier, label: String? = null) {
    val colors = LocalPentanaColors.current
    val onVar = MaterialTheme.colorScheme.onSurfaceVariant
    val spec = when (kind) {
        ChipKind.Paid -> ChipSpec(colors.ok.color, colors.ok.container, "Paid", Icons.Filled.Check)
        ChipKind.Partial -> ChipSpec(colors.warn.color, colors.warn.container, "Partial", dot = true)
        ChipKind.Unpaid -> ChipSpec(onVar, null, "Unpaid", dot = true, outline = true)
        ChipKind.Overdue -> ChipSpec(colors.bad.color, colors.bad.container, "Overdue", dot = true)
        ChipKind.Registered -> ChipSpec(colors.ok.color, colors.ok.container, "Registered", Icons.Filled.Check)
        ChipKind.Waitlisted -> ChipSpec(colors.warn.color, colors.warn.container, "Waitlisted", Icons.Filled.Schedule)
        ChipKind.Open -> ChipSpec(colors.activ.color, colors.activ.container, "Open")
        ChipKind.Closed -> ChipSpec(onVar, null, "Closed", outline = true)
        ChipKind.VoteNow -> ChipSpec(colors.warn.color, colors.warn.container, "Vote now", dot = true)
        ChipKind.Responded -> ChipSpec(colors.ok.color, colors.ok.container, "Responded", Icons.Filled.Check)
    }
    val shape = RoundedCornerShape(8.dp)
    val box =
        if (spec.outline) modifier.clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
        else modifier.clip(shape).background(spec.bg ?: Color.Transparent)
    Row(
        box.padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (spec.dot) Box(Modifier.size(7.dp).clip(CircleShape).background(spec.color))
        if (spec.icon != null) Icon(spec.icon, null, tint = spec.color, modifier = Modifier.size(14.dp))
        Text(spec.text.takeIf { label == null } ?: label!!, color = spec.color, style = MaterialTheme.typography.labelLarge)
    }
}
