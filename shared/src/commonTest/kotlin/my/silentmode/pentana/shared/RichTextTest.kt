package my.silentmode.pentana.shared

import my.silentmode.pentana.shared.presentation.RichBlock
import my.silentmode.pentana.shared.presentation.RichSpan
import my.silentmode.pentana.shared.presentation.plainTextBlocks
import my.silentmode.pentana.shared.presentation.richTextBlocks
import kotlin.test.Test
import kotlin.test.assertEquals

class RichTextTest {

    @Test fun empty_input_has_no_blocks() {
        assertEquals(emptyList(), richTextBlocks(null))
        assertEquals(emptyList(), richTextBlocks(""))
        assertEquals(emptyList(), richTextBlocks("<p>  </p><p><br></p>"))
    }

    @Test fun paragraphs_keep_bold_italic_and_collapse_whitespace() {
        assertEquals(
            listOf(
                RichBlock.Paragraph(listOf(RichSpan("Bring "), RichSpan("water", bold = true), RichSpan(" and "), RichSpan("shoes", italic = true), RichSpan("."))),
                RichBlock.Paragraph(listOf(RichSpan("Second paragraph"))),
            ),
            richTextBlocks("<p>Bring   <strong>water</strong>\n and <em>shoes</em>.</p>\n<p> Second paragraph </p>"),
        )
    }

    @Test fun nested_bold_italic_and_b_i_tags() {
        assertEquals(
            listOf(RichBlock.Paragraph(listOf(RichSpan("both", bold = true, italic = true), RichSpan(" bold", bold = true)))),
            richTextBlocks("<p><b><i>both</i> bold</b></p>"),
        )
    }

    @Test fun line_breaks_stay_inside_the_paragraph() {
        assertEquals(
            listOf(RichBlock.Paragraph(listOf(RichSpan("Line one\nLine two")))),
            richTextBlocks("<p>Line one <br> Line two</p>"),
        )
    }

    @Test fun links_keep_safe_hrefs_only() {
        assertEquals(
            listOf(
                RichBlock.Paragraph(
                    listOf(
                        RichSpan("See "),
                        RichSpan("the map", link = "https://maps.example.com/?q=a&b=c"),
                        RichSpan(" or "),
                        RichSpan("mail us", link = "mailto:club@example.com"),
                        RichSpan(" not this"), // the javascript: link is dropped, its text merges with the plain run
                    ),
                ),
            ),
            richTextBlocks(
                """<p>See <a href="https://maps.example.com/?q=a&amp;b=c" target="_blank">the map</a> or """ +
                    """<a href='mailto:club@example.com'>mail us</a> not <a href="javascript:alert(1)">this</a></p>""",
            ),
        )
    }

    @Test fun unordered_and_ordered_lists() {
        assertEquals(
            listOf(
                RichBlock.ListItem("•", 1, listOf(RichSpan("Gloves"))),
                RichBlock.ListItem("•", 1, listOf(RichSpan("Hat"))),
                RichBlock.ListItem("1.", 1, listOf(RichSpan("Meet"))),
                RichBlock.ListItem("2.", 1, listOf(RichSpan("Walk", bold = true))),
            ),
            richTextBlocks("<ul>\n<li>Gloves</li>\n<li>Hat</li>\n</ul><ol><li>Meet</li><li><strong>Walk</strong></li></ol>"),
        )
    }

    @Test fun list_items_wrapping_paragraphs_keep_their_marker() {
        // TipTap (Filament's rich editor) wraps each list item's text in <p>.
        assertEquals(
            listOf(
                RichBlock.ListItem("•", 1, listOf(RichSpan("First"))),
                RichBlock.ListItem("", 1, listOf(RichSpan("More about first"))),
                RichBlock.ListItem("•", 1, listOf(RichSpan("Second"))),
                RichBlock.Paragraph(listOf(RichSpan("After"))),
            ),
            richTextBlocks("<ul><li><p>First</p><p>More about first</p></li><li><p>Second</p></li></ul><p>After</p>"),
        )
    }

    @Test fun nested_lists_carry_their_level() {
        assertEquals(
            listOf(
                RichBlock.ListItem("•", 1, listOf(RichSpan("Outer"))),
                RichBlock.ListItem("1.", 2, listOf(RichSpan("Inner"))),
                RichBlock.ListItem("•", 1, listOf(RichSpan("Next"))),
            ),
            richTextBlocks("<ul><li>Outer<ol><li>Inner</li></ol></li><li>Next</li></ul>"),
        )
    }

    @Test fun images_split_text_and_need_an_http_source() {
        assertEquals(
            listOf(
                RichBlock.Paragraph(listOf(RichSpan("Before"))),
                RichBlock.Image("https://pentana.silentmode.net/storage/activity-images/a.jpg"),
                RichBlock.Paragraph(listOf(RichSpan("After"))),
            ),
            richTextBlocks(
                """<p>Before<img alt="x" src="https://pentana.silentmode.net/storage/activity-images/a.jpg" />After</p>""" +
                    """<p><img src="data:image/png;base64,AAAA"><img></p>""",
            ),
        )
    }

    @Test fun headings_become_bold_paragraphs() {
        assertEquals(
            listOf(
                RichBlock.Paragraph(listOf(RichSpan("What to bring", bold = true))),
                RichBlock.Paragraph(listOf(RichSpan("Water"))),
            ),
            richTextBlocks("<h2>What to bring</h2><p>Water</p>"),
        )
    }

    @Test fun entities_are_decoded() {
        assertEquals(
            listOf(RichBlock.Paragraph(listOf(RichSpan("Fish & chips <3 \"yum\" it's 5 – 6 ✓")))),
            richTextBlocks("<p>Fish &amp; chips &lt;3 &quot;yum&quot; it&#39;s&nbsp;5 &ndash; 6 &#x2713;</p>"),
        )
    }

    @Test fun text_outside_tags_and_unknown_tags_is_kept() {
        assertEquals(
            listOf(RichBlock.Paragraph(listOf(RichSpan("Loose text with underline")))),
            richTextBlocks("Loose text with <u>underline</u>"),
        )
    }

    @Test fun plain_text_splits_on_blank_lines() {
        assertEquals(
            listOf(
                RichBlock.Paragraph(listOf(RichSpan("One\nstill one"))),
                RichBlock.Paragraph(listOf(RichSpan("Two"))),
            ),
            plainTextBlocks("  One\nstill one\n\n \n Two  "),
        )
        assertEquals(emptyList(), plainTextBlocks(null))
        assertEquals(emptyList(), plainTextBlocks("   "))
    }
}
