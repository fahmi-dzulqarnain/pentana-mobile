package my.silentmode.pentana.shared.presentation

/**
 * A run of text with one style. [link] is an http(s)/mailto URL when the run sits inside `<a>`.
 */
data class RichSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val link: String? = null,
)

/**
 * One block of an activity's full description, rendered natively by each platform: text blocks
 * become styled text, images load from their public URL and fit the screen width.
 */
sealed interface RichBlock {
    data class Paragraph(val spans: List<RichSpan>) : RichBlock

    /**
     * A list entry. [marker] is "•" or "1." etc.; it is empty for a second paragraph inside the
     * same `<li>`, which lines up with the item's text. [level] starts at 1 for a top-level list.
     */
    data class ListItem(val marker: String, val level: Int, val spans: List<RichSpan>) : RichBlock

    data class Image(val url: String) : RichBlock
}

/**
 * Splits the backend's sanitised description HTML (`<p>`, `<br>`, `<strong>`/`<b>`, `<em>`/`<i>`,
 * `<a>`, `<ul>`/`<ol>`/`<li>`, `<img>`, headings) into [RichBlock]s. Unknown tags are dropped and
 * their text kept. Only http(s) images and http(s)/mailto links survive. Empty blocks are removed.
 */
fun richTextBlocks(html: String?): List<RichBlock> {
    if (html.isNullOrBlank()) return emptyList()
    return RichTextParser().parse(html)
}

/** Plain text (e.g. the activity overview) as paragraphs, split on blank lines. */
fun plainTextBlocks(text: String?): List<RichBlock> =
    text.orEmpty()
        .split(Regex("\\n\\s*\\n"))
        .map { paragraph -> paragraph.trim() }
        .filter { paragraph -> paragraph.isNotEmpty() }
        .map { paragraph -> RichBlock.Paragraph(listOf(RichSpan(paragraph))) }

private class RichTextParser {
    private sealed interface BlockKind {
        data object Paragraph : BlockKind
        data class Item(val marker: String, val level: Int) : BlockKind
    }

    private class ListContext(val ordered: Boolean, var counter: Int = 0)

    private val blocks = mutableListOf<RichBlock>()
    private val spans = mutableListOf<RichSpan>()
    private var kind: BlockKind = BlockKind.Paragraph
    private val lists = ArrayDeque<ListContext>()
    private val links = ArrayDeque<String?>()
    private var boldDepth = 0
    private var italicDepth = 0
    private var openItems = 0

    fun parse(html: String): List<RichBlock> {
        var cursor = 0
        for (match in TAG.findAll(html)) {
            appendText(html.substring(cursor, match.range.first))
            val closing = match.groupValues[1] == "/"
            val name = match.groupValues[2].lowercase()
            val attributes = match.groupValues[3]
            if (closing) closeTag(name) else openTag(name, attributes)
            cursor = match.range.last + 1
        }
        appendText(html.substring(cursor))
        flush()
        return blocks
    }

    private fun openTag(name: String, attributes: String) {
        when (name) {
            "br" -> spans += RichSpan("\n", bold = boldDepth > 0, italic = italicDepth > 0, link = links.lastOrNull())
            "strong", "b" -> boldDepth++
            "em", "i" -> italicDepth++
            "a" -> links.addLast(attribute(attributes, "href")?.takeIf(::isSafeLink))
            "ul", "ol" -> {
                flush()
                lists.addLast(ListContext(ordered = name == "ol"))
            }
            "li" -> {
                flush()
                openItems++
                val list = lists.lastOrNull()
                val marker = when {
                    list == null -> "•"
                    list.ordered -> "${++list.counter}."
                    else -> "•"
                }
                kind = BlockKind.Item(marker, level = lists.size.coerceAtLeast(1))
            }
            "img" -> {
                flush()
                attribute(attributes, "src")?.takeIf(::isHttpUrl)?.let { url -> blocks += RichBlock.Image(url) }
            }
            in HEADINGS -> {
                startBlock()
                boldDepth++
            }
            in BLOCKS -> startBlock()
        }
    }

    private fun closeTag(name: String) {
        when (name) {
            "strong", "b" -> boldDepth = (boldDepth - 1).coerceAtLeast(0)
            "em", "i" -> italicDepth = (italicDepth - 1).coerceAtLeast(0)
            "a" -> links.removeLastOrNull()
            "ul", "ol" -> {
                flush()
                lists.removeLastOrNull()
                kind = continuationKind()
            }
            "li" -> {
                flush()
                openItems = (openItems - 1).coerceAtLeast(0)
                kind = continuationKind()
            }
            in HEADINGS -> {
                flush()
                boldDepth = (boldDepth - 1).coerceAtLeast(0)
            }
            in BLOCKS -> flush()
        }
    }

    /** A block tag opened: keep a just-opened list item's marker, otherwise start a fresh block. */
    private fun startBlock() {
        if (spans.any { span -> span.text.isNotBlank() }) flush()
    }

    /** After a block inside an `<li>` ends, further text in that item aligns under its marker. */
    private fun continuationKind(): BlockKind =
        if (openItems > 0) BlockKind.Item(marker = "", level = lists.size.coerceAtLeast(1)) else BlockKind.Paragraph

    private fun appendText(raw: String) {
        if (raw.isEmpty()) return
        val text = decodeEntities(raw.replace(WHITESPACE, " "))
        if (text.isEmpty()) return
        spans += RichSpan(text, bold = boldDepth > 0, italic = italicDepth > 0, link = links.lastOrNull())
    }

    private fun flush() {
        val merged = trimmed(merge(spans))
        spans.clear()
        if (merged.isNotEmpty()) {
            blocks += when (val current = kind) {
                BlockKind.Paragraph -> RichBlock.Paragraph(merged)
                is BlockKind.Item -> RichBlock.ListItem(current.marker, current.level, merged)
            }
        }
        // A list item's second paragraph lines up with its text instead of repeating the marker.
        kind = continuationKind()
    }

    private fun merge(input: List<RichSpan>): List<RichSpan> {
        val output = mutableListOf<RichSpan>()
        for (span in input) {
            val previous = output.lastOrNull()
            if (previous != null && previous.bold == span.bold && previous.italic == span.italic && previous.link == span.link) {
                output[output.lastIndex] = previous.copy(text = previous.text + span.text)
            } else {
                output += span
            }
        }
        return output
    }

    /** Collapses spaces around line breaks and trims the block's outer whitespace. */
    private fun trimmed(input: List<RichSpan>): List<RichSpan> {
        val cleaned = input.map { span -> span.copy(text = span.text.replace(Regex(" *\\n *"), "\n")) }.toMutableList()
        while (cleaned.isNotEmpty()) {
            val first = cleaned.first().copy(text = cleaned.first().text.trimStart())
            if (first.text.isEmpty()) cleaned.removeAt(0) else { cleaned[0] = first; break }
        }
        while (cleaned.isNotEmpty()) {
            val last = cleaned.last().copy(text = cleaned.last().text.trimEnd())
            if (last.text.isEmpty()) cleaned.removeAt(cleaned.lastIndex) else { cleaned[cleaned.lastIndex] = last; break }
        }
        // Whitespace between two spans (e.g. "</strong> <em>") collapses to one space.
        for (index in 1 until cleaned.size) {
            if (cleaned[index - 1].text.endsWith(" ") && cleaned[index].text.startsWith(" ")) {
                cleaned[index] = cleaned[index].copy(text = cleaned[index].text.trimStart(' '))
            }
        }
        return cleaned.filter { span -> span.text.isNotEmpty() }
    }

    private companion object {
        val TAG = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*)>")
        val WHITESPACE = Regex("\\s+")
        val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val BLOCKS = setOf("p", "div", "blockquote", "figure", "figcaption", "pre")

        fun attribute(attributes: String, name: String): String? {
            val match = Regex("""(?:^|\s)$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
                .find(attributes) ?: return null
            val value = match.groupValues.drop(1).firstOrNull { group -> group.isNotEmpty() } ?: return null
            return decodeEntities(value).trim().ifEmpty { null }
        }

        fun isHttpUrl(url: String): Boolean =
            url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

        fun isSafeLink(url: String): Boolean = isHttpUrl(url) || url.startsWith("mailto:", ignoreCase = true)
    }
}

private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
    "ldquo" to "“", "rdquo" to "”", "copy" to "©",
)

/** Decodes the named entities above plus numeric `&#123;` / `&#x7B;` references. */
internal fun decodeEntities(text: String): String =
    Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(text) { match ->
        val entity = match.groupValues[1]
        val codePoint = when {
            entity.startsWith("#x") || entity.startsWith("#X") -> entity.drop(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()
            else -> null
        }
        when {
            codePoint != null && codePoint in 1..0xFFFF -> codePoint.toChar().toString()
            codePoint != null && codePoint in 0x10000..0x10FFFF -> {
                val offset = codePoint - 0x10000
                charArrayOf((0xD800 + (offset shr 10)).toChar(), (0xDC00 + (offset and 0x3FF)).toChar()).concatToString()
            }
            else -> NAMED_ENTITIES[entity.lowercase()] ?: match.value
        }
    }
