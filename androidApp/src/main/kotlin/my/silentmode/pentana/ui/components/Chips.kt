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
    val p = LocalPentanaColors.current
    val onVar = MaterialTheme.colorScheme.onSurfaceVariant
    val s = when (kind) {
        ChipKind.Paid -> ChipSpec(p.ok.color, p.ok.container, "Paid", Icons.Filled.Check)
        ChipKind.Partial -> ChipSpec(p.warn.color, p.warn.container, "Partial", dot = true)
        ChipKind.Unpaid -> ChipSpec(onVar, null, "Unpaid", dot = true, outline = true)
        ChipKind.Overdue -> ChipSpec(p.bad.color, p.bad.container, "Overdue", dot = true)
        ChipKind.Registered -> ChipSpec(p.ok.color, p.ok.container, "Registered", Icons.Filled.Check)
        ChipKind.Waitlisted -> ChipSpec(p.warn.color, p.warn.container, "Waitlisted", Icons.Filled.Schedule)
        ChipKind.Open -> ChipSpec(p.activ.color, p.activ.container, "Open")
        ChipKind.Closed -> ChipSpec(onVar, null, "Closed", outline = true)
        ChipKind.VoteNow -> ChipSpec(p.warn.color, p.warn.container, "Vote now", dot = true)
        ChipKind.Responded -> ChipSpec(p.ok.color, p.ok.container, "Responded", Icons.Filled.Check)
    }
    val shape = RoundedCornerShape(8.dp)
    val box =
        if (s.outline) modifier.clip(shape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
        else modifier.clip(shape).background(s.bg ?: Color.Transparent)
    Row(
        box.padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (s.dot) Box(Modifier.size(7.dp).clip(CircleShape).background(s.color))
        if (s.icon != null) Icon(s.icon, null, tint = s.color, modifier = Modifier.size(14.dp))
        Text(s.text.takeIf { label == null } ?: label!!, color = s.color, style = MaterialTheme.typography.labelLarge)
    }
}
