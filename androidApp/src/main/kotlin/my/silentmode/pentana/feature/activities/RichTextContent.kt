package my.silentmode.pentana.feature.activities

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import my.silentmode.pentana.core.RemoteImages
import my.silentmode.pentana.shared.presentation.RichBlock
import my.silentmode.pentana.shared.presentation.RichSpan

/**
 * An activity's description, drawn natively from the shared [RichBlock]s: styled text in the
 * app's type scale, links that open in the browser (Compose's default URI handler), images fitted
 * to the width.
 */
@Composable
fun RichTextContent(blocks: List<RichBlock>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is RichBlock.Paragraph -> RichParagraph(block.spans)
                is RichBlock.ListItem -> Row(Modifier.padding(start = (16 * (block.level - 1)).dp)) {
                    Text(
                        block.marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 22.dp),
                    )
                    RichParagraph(block.spans, Modifier.weight(1f))
                }
                is RichBlock.Image -> DescriptionImage(block.url)
            }
        }
    }
}

@Composable
private fun RichParagraph(spans: List<RichSpan>, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(spans, linkColor) { annotated(spans, linkColor) }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = modifier)
}

private fun annotated(spans: List<RichSpan>, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.SemiBold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
        )
        val link = span.link
        if (link != null) {
            withLink(LinkAnnotation.Url(link, linkStyles)) { withStyle(style) { append(span.text) } }
        } else {
            withStyle(style) { append(span.text) }
        }
    }
}

private sealed interface ImageLoad {
    data object Loading : ImageLoad
    class Ready(val bitmap: ImageBitmap) : ImageLoad
    data object Failed : ImageLoad
}

@Composable
private fun DescriptionImage(url: String) {
    val shape = RoundedCornerShape(12.dp)
    val load by produceState<ImageLoad>(RemoteImages.cached(url)?.let { ImageLoad.Ready(it) } ?: ImageLoad.Loading, url) {
        if (value is ImageLoad.Ready) return@produceState
        value = RemoteImages.load(url)?.let { ImageLoad.Ready(it) } ?: ImageLoad.Failed
    }
    when (val current = load) {
        is ImageLoad.Ready -> Image(
            bitmap = current.bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(current.bitmap.width.toFloat() / current.bitmap.height.coerceAtLeast(1))
                .clip(shape),
        )
        ImageLoad.Loading -> Box(
            Modifier.fillMaxWidth().height(180.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
        ImageLoad.Failed -> Row(
            Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text("Image unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
