package my.silentmode.pentana.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Tri(val color: Color, val container: Color, val onContainer: Color)
data class Pair2(val color: Color, val container: Color)

data class PentanaColors(
    val dues: Tri, val lunch: Tri, val activ: Tri, val proof: Tri,
    val ok: Pair2, val warn: Pair2, val bad: Pair2,
    /** Green text on [ok]'s container (pills, "open to join"): #0B6E42 in light for 5.5:1. */
    val okStrong: Color,
    /** Home to-do cue pill: [Pair2.color] is the label, [Pair2.container] the fill. */
    val cue: Pair2,
)

/** Date-chip weekday strips (same in light and dark): lunch orange and activity green. */
val ChipStripLunch = Color(0xFFF7931E)
val ChipStripLunchText = Color(0xFF14121F)
val ChipStripActivity = Color(0xFF0B6E42)
val ChipStripActivityText = Color(0xFFFFFFFF)

val LightPentana = PentanaColors(
    dues = Tri(Color(0xFF15489E), Color(0xFFD9E2FF), Color(0xFF001A43)),
    lunch = Tri(Color(0xFF9A4A00), Color(0xFFFFDCC2), Color(0xFF3A1A00)),
    activ = Tri(Color(0xFF006C4C), Color(0xFF88F8C5), Color(0xFF002115)),
    proof = Tri(Color(0xFF5B3FD0), Color(0xFFE6DEFF), Color(0xFF1C0066)),
    ok = Pair2(Color(0xFF006C4C), Color(0xFF88F8C5)),
    warn = Pair2(Color(0xFF8A5000), Color(0xFFFFDDB3)),
    bad = Pair2(Color(0xFFBA1A1A), Color(0xFFFFDAD6)),
    okStrong = Color(0xFF0B6E42),
    cue = Pair2(Color(0xFFFFFFFF), Color(0xFF1B1464)),
)
val DarkPentana = PentanaColors(
    dues = Tri(Color(0xFFAFC6FF), Color(0xFF1A3878), Color(0xFFD9E2FF)),
    lunch = Tri(Color(0xFFFFB784), Color(0xFF763500), Color(0xFFFFDCC2)),
    activ = Tri(Color(0xFF6BDBAA), Color(0xFF005138), Color(0xFF88F8C5)),
    proof = Tri(Color(0xFFCBBEFF), Color(0xFF43308F), Color(0xFFE6DEFF)),
    ok = Pair2(Color(0xFF6BDBAA), Color(0xFF005138)),
    warn = Pair2(Color(0xFFFFB95C), Color(0xFF5E3F00)),
    bad = Pair2(Color(0xFFFFB4AB), Color(0xFF93000A)),
    okStrong = Color(0xFF6BDBAA),
    cue = Pair2(Color(0xFF14121F), Color(0xFFFBB04C)),
)

val LocalPentanaColors = staticCompositionLocalOf { LightPentana }
