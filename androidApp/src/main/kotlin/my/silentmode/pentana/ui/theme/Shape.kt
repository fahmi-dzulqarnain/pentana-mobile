package my.silentmode.pentana.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PentShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Expressive asymmetric shape reserved for the Home greeting hero.
val HeroShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 10.dp)
