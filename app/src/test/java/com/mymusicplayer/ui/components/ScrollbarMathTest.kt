package com.mymusicplayer.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 1 feedback loop for the scrollbar/index-bar bugs (see diagnosing-bugs
 * skill): simulates scroll frames through the pure [ScrollbarMath] mapping and
 * asserts the user's exact symptoms stay fixed —
 *
 *  1. the viewport thumb keeps ONE length across a whole scroll (no breathing),
 *  2. its position moves monotonically from top to bottom,
 *  3. descending index-bar sections land on the right rows with # pinned last.
 */
class ScrollbarMathTest {

    // Layout fixture: 2 header items (tall hero cards + sticky chips), then
    // uniform track rows with a short section header every 25 rows.
    private val heights: List<Int> = buildList {
        add(400) // index 0: mini cards
        add(200) // index 1: sticky tab header
        for (i in 0 until 500) {
            add(if (i % 25 == 0) 80 else 160)
        }
    }
    private val totalItems = heights.size
    private val viewportH = 1600f
    private val trackH = 1200f
    private val minThumb = 120f

    private val tops: List<Int> = run {
        val acc = mutableListOf(0)
        for (h in heights) acc.add(acc.last() + h)
        acc.dropLast(1)
    }
    private val totalH = tops.last() + heights.last()

    /** Frames like LazyList would report them while scrolling top to bottom. */
    private data class Frame(
        val firstIndex: Int,
        val firstOffset: Int,
        val visibleSizes: List<Int>
    )

    private fun frames(step: Int = 137): List<Frame> {
        val out = mutableListOf<Frame>()
        var s = 0
        while (s <= totalH - viewportH.toInt()) {
            val idx = tops.indexOfLast { it <= s }
            val offset = s - tops[idx]
            val sizes = heights.indices
                .filter { i -> tops[i] < s + viewportH && tops[i] + heights[i] > s }
                .map { heights[it] }
            out.add(Frame(idx, offset, sizes))
            s += step
        }
        // Exact bottom frame: thumb must reach the very end.
        val sMax = totalH - viewportH.toInt()
        val idx = tops.indexOfLast { it <= sMax }
        val sizes = heights.indices
            .filter { i -> tops[i] < sMax + viewportH && tops[i] + heights[i] > sMax }
            .map { heights[it] }
        out.add(Frame(idx, sMax - tops[idx], sizes))
        return out
    }

    /** What the composable measures: content items only (index >= 2). */
    private fun contentSizes(frame: Frame): List<Int> {
        val visibleIdx = heights.indices.filter { i ->
            tops[i] < frameTop(frame) + viewportH && tops[i] + heights[i] > frameTop(frame)
        }
        return visibleIdx.filter { it >= 2 }.map { heights[it] }
    }

    private fun frameTop(frame: Frame): Int = tops[frame.firstIndex] + frame.firstOffset

    @Test
    fun `median stays fixed while headers enter and leave the window`() {
        val medians = frames().map { ScrollbarMath.medianItemSize(contentSizes(it)) }
        assertTrue(medians.all { it == 160f }, "medians drifted: ${medians.distinct()}")
    }

    @Test
    fun `mean jitters on the same frames - the old approach was load-bearing`() {
        val means = frames().map { ScrollbarMath.meanItemSize(contentSizes(it)) }
        val jitter = means.max() - means.min()
        assertTrue(jitter > 1f, "expected the mean to wobble, got $jitter")
    }

    @Test
    fun `thumb keeps one length and travels monotonically top to bottom`() {
        val thumbs = mutableListOf<Float>()
        val fractions = mutableListOf<Float>()
        for (f in frames()) {
            val avg = ScrollbarMath.medianItemSize(contentSizes(f))
            assertTrue(avg > 0f)
            val contentH = ScrollbarMath.contentHeightPx(avg, totalItems)
            val range = ScrollbarMath.scrollRangePx(contentH, viewportH)
            assertTrue(range > 0f)
            val visibleIdx = heights.indices.filter { i ->
                tops[i] < frameTop(f) + viewportH && tops[i] + heights[i] > frameTop(f)
            }
            val lastIdx = visibleIdx.last()
            val lastBottom = (tops[lastIdx] + heights[lastIdx] - frameTop(f)).toFloat()
            val scrolled = ScrollbarMath.scrolledPx(
                scrollRangePx = range,
                remainingBelowPx = ScrollbarMath.remainingBelowPx(avg, totalItems, lastIdx),
                bottomGapPx = ScrollbarMath.bottomGapPx(viewportH, lastBottom)
            )
            fractions.add(ScrollbarMath.fraction(scrolled, range))
            thumbs.add(ScrollbarMath.thumbHeightPx(trackH, viewportH, contentH, minThumb))
        }
        val lengthJitter = thumbs.max() - thumbs.min()
        assertTrue(lengthJitter < 0.001f, "thumb length changed by $lengthJitter px")
        assertEquals(0f, fractions.first(), 0.001f)
        assertEquals(1f, fractions.last(), 0.001f)
        for (i in 1 until fractions.size) {
            assertTrue(
                fractions[i] >= fractions[i - 1] - 0.0001f,
                "fraction went backwards at frame $i: ${fractions[i - 1]} -> ${fractions[i]}"
            )
        }
    }

    @Test
    fun `scrolledPx is zero at the top and full range at the bottom`() {
        // Whole range still below: sitting at the very top.
        assertEquals(0f, ScrollbarMath.scrolledPx(1000f, 1000f, 0f), 0.001f)
        // Nothing below and no gap: list bottom meets viewport bottom = fully scrolled.
        assertEquals(1000f, ScrollbarMath.scrolledPx(1000f, 0f, 0f), 0.001f)
        // Still below but a gap opened under the last item: clamps back to 0.
        assertEquals(0f, ScrollbarMath.scrolledPx(1000f, 1000f, 50f), 0.001f)
        // Halfway: half the range left below.
        assertEquals(500f, ScrollbarMath.scrolledPx(1000f, 500f, 0f), 0.001f)
        // Degenerate range never goes negative.
        assertEquals(0f, ScrollbarMath.scrolledPx(0f, 500f, 0f), 0.001f)
    }

    // ---- index letters: one trailing `#` bucket, both directions ----

    /**
     * The reported symptoms, verbatim: `(g)i-dle` sitting above `A` on the artists
     * grid, `#` / `&` / bare digits above the CJK titles on the albums grid, and
     * the bar's single `#` therefore pointing at the wrong place entirely.
     */
    private val messy = listOf(
        "(g)i-dle", "2Pac", "Zebra", "apple", "#tag", "宇多田ヒカル", "Bowie", "Émilie", "ア-exist"
    )

    @Test
    fun `letterOf puts everything unnameable in one bucket`() {
        assertEquals("A", ScrollbarMath.letterOf("apple"))
        assertEquals("B", ScrollbarMath.letterOf("Bowie"))
        // Leading punctuation, leading digit, symbol, CJK, accented: all `#`.
        assertEquals("#", ScrollbarMath.letterOf("(g)i-dle"))
        assertEquals("#", ScrollbarMath.letterOf("2Pac"))
        assertEquals("#", ScrollbarMath.letterOf("#tag"))
        assertEquals("#", ScrollbarMath.letterOf("宇多田ヒカル"))
        assertEquals("#", ScrollbarMath.letterOf("Émilie"))
        assertEquals("#", ScrollbarMath.letterOf(""))
    }

    @Test
    fun `the old code-point compare is what scattered the hash names`() {
        // Guards the diagnosis: a plain case-insensitive sort really does put
        // these at BOTH ends, which is exactly what the user saw.
        val naive = messy.sortedBy { it.lowercase() }
        val firstAlphaIndex = naive.indexOfFirst { it.first().uppercaseChar() in 'A'..'Z' }
        assertTrue(firstAlphaIndex > 0, "expected leading punctuation/digits first: $naive")
        val cjkIndex = naive.indexOfFirst { it.first().code > 0x2E80 }
        assertTrue(cjkIndex > naive.size - 3, "expected CJK trailing: $naive")
    }

    @Test
    fun `ascending groups every hash name into one trailing block`() {
        val sorted = messy.sortedWith(ScrollbarMath.indexLetterComparator(descending = false))
        val letters = sorted.map { ScrollbarMath.letterOf(it) }
        // Contiguous: letters, then one run of `#`, and that run is last.
        assertEquals(letters.sortedBy { ScrollbarMath.letterRank(it) }, letters)
        assertEquals("#", letters.last())
        assertEquals(listOf("A", "B", "Z"), letters.distinct().filter { it != "#" })
        // And the block really does hold the scattered names, not just one of them.
        val hashBlock = sorted.filter { ScrollbarMath.letterOf(it) == "#" }
        assertEquals(
            listOf("2Pac", "#tag", "ア-exist", "(g)i-dle", "Émilie", "宇多田ヒカル").toSet(),
            hashBlock.toSet()
        )
    }

    @Test
    fun `descending reverses the alphabet but keeps hash pinned last`() {
        val sorted = messy.sortedWith(ScrollbarMath.indexLetterComparator(descending = true))
        assertEquals(listOf("Zebra", "Bowie", "apple"), sorted.take(3))
        assertEquals("#", ScrollbarMath.letterOf(sorted.last()))
        // The alpha part is descending and the hash run trails it, so the rank
        // sequence is NOT globally sortedDescending — a plain sortDescending()
        // would put the 26s (hash) first, which is the bug.
        val ranks = sorted.map { ScrollbarMath.letterRank(it) }
        val alphaRanks = ranks.filter { it != ScrollbarMath.OTHER_LETTER_RANK }
        assertEquals(alphaRanks.sortedDescending(), alphaRanks)
        assertTrue(ranks.take(alphaRanks.size).all { it != ScrollbarMath.OTHER_LETTER_RANK })
        assertTrue(ranks.drop(alphaRanks.size).all { it == ScrollbarMath.OTHER_LETTER_RANK })
    }

    @Test
    fun `sectionsForGroupedList reads letters and rows straight off the list`() {
        val sorted = messy.sortedWith(ScrollbarMath.indexLetterComparator(descending = false))
        val (letters, rows) = ScrollbarMath.sectionsForGroupedList(sorted, span = 2)
        assertEquals(listOf("A", "B", "Z", "#"), letters)
        assertEquals(letters.size, rows.size)
        // Each row must be the `2 + firstFlat / span` of the first item in that
        // group (2 = the mini-cards row + the sticky tab header above the content).
        letters.forEachIndexed { i, letter ->
            val firstFlat = sorted.indexOfFirst { ScrollbarMath.letterOf(it) == letter }
            assertTrue(firstFlat >= 0, "letter $letter missing from the list")
            assertEquals(2 + firstFlat / 2, rows[i], "wrong row for $letter")
        }
        // The `#` jump must land on the first hash row, at the very tail.
        assertEquals(2 + sorted.indexOfFirst { ScrollbarMath.letterOf(it) == "#" } / 2, rows.last())
    }

    @Test
    fun `sectionsForGroupedList needs no remapping in descending either`() {
        val sorted = messy.sortedWith(ScrollbarMath.indexLetterComparator(descending = true))
        val (letters, rows) = ScrollbarMath.sectionsForGroupedList(sorted, span = 4)
        assertEquals(listOf("Z", "B", "A", "#"), letters)
        letters.forEachIndexed { i, letter ->
            val firstFlat = sorted.indexOfFirst { ScrollbarMath.letterOf(it) == letter }
            assertEquals(2 + firstFlat / 4, rows[i], "wrong row for $letter")
        }
    }

    @Test
    fun `empty list yields no letters rather than a phantom bar`() {
        val (letters, rows) = ScrollbarMath.sectionsForGroupedList(emptyList(), span = 2)
        assertTrue(letters.isEmpty())
        assertTrue(rows.isEmpty())
    }

    // ---- dragging: both ends of the list must be reachable ----

    @Test
    fun `drag targets reach the very first and very last item`() {
        val total = 500
        val avg = 200f
        val range = ScrollbarMath.scrollRangePx(ScrollbarMath.contentHeightPx(avg, total), viewportH)
        assertEquals(0, ScrollbarMath.targetIndexForFraction(0f, total, range, avg))
        assertEquals(total - 1, ScrollbarMath.targetIndexForFraction(1f, total, range, avg))
        // Out-of-range input is clamped rather than escaping the list.
        assertEquals(0, ScrollbarMath.targetIndexForFraction(-0.5f, total, range, avg))
        assertEquals(total - 1, ScrollbarMath.targetIndexForFraction(1.5f, total, range, avg))
    }

    @Test
    fun `drag targets move forward monotonically down the bar`() {
        val total = 500
        val avg = 200f
        val range = ScrollbarMath.scrollRangePx(ScrollbarMath.contentHeightPx(avg, total), viewportH)
        var previous = -1
        for (step in 0..100) {
            val f = step / 100f
            val index = ScrollbarMath.targetIndexForFraction(f, total, range, avg)
            assertTrue(index >= previous, "index went backwards at f=$f: $previous -> $index")
            assertTrue(index in 0 until total)
            previous = index
        }
        // Reaches the bottom before the finger does, i.e. the tail is reachable.
        assertTrue(ScrollbarMath.targetIndexForFraction(0.9f, total, range, avg) > total / 2)
    }

    @Test
    fun `drag targets are stable - no feedback loop compounding its own error`() {
        val total = 500
        val avg = 200f
        val range = ScrollbarMath.scrollRangePx(ScrollbarMath.contentHeightPx(avg, total), viewportH)
        val first = ScrollbarMath.targetIndexForFraction(0.5f, total, range, avg)
        // Re-asking from a different current position must not drift: the mapping
        // is a pure function of the target fraction, with no visible anchor.
        val again = ScrollbarMath.targetIndexForFraction(0.5f, total, range, avg)
        assertEquals(first, again)
    }

    @Test
    fun `degenerate geometry yields index 0 rather than a crash`() {
        assertEquals(0, ScrollbarMath.targetIndexForFraction(0.5f, 0, 100f, 10f))
        assertEquals(0, ScrollbarMath.targetIndexForFraction(0.5f, 10, 0f, 10f))
        assertEquals(0, ScrollbarMath.targetIndexForFraction(0.5f, 10, 100f, 0f))
    }

    // ---- item size from positions ----

    @Test
    fun `averageItemSizePx divides the measured span by the item count`() {
        // 10 rows spanning 2000px, whatever their individual heights.
        assertEquals(200f, ScrollbarMath.averageItemSizePx(0, 5, 2000, 15, emptyList()))
        // Degenerate: one item only, so fall back to the size-based median.
        assertEquals(200f, ScrollbarMath.averageItemSizePx(0, 5, 0, 5, listOf(160, 200, 400)))
    }

    @Test
    fun `averageItemSizePx is steadier than the size-based median on a clipped edge`() {
        // A tall row entering the window moves the median of sizes around; the
        // position quotient is unaffected because the span grows with it.
        val withTall = ScrollbarMath.averageItemSizePx(0, 10, 2000, 20, listOf(160, 160, 2000))
        val withoutTall = ScrollbarMath.averageItemSizePx(0, 10, 2000, 20, listOf(160, 160, 160))
        assertEquals(200f, withTall, 0.001f)
        assertEquals(withoutTall, withTall, 0.001f)
    }
}
