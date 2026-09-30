package com.mymusicplayer.ui.components

/**
 * Pure, UI-framework-free math behind [DragScrollbar] and the alphabet index
 * bar's descending mode.
 *
 * Extracted so the mapping can be unit-tested: feed simulated scroll frames in,
 * assert the thumb length never changes and the fraction/sections stay aligned.
 * The composables themselves must stay thin wrappers over these functions —
 * any measurement smoothing with retained state belongs here only if it is a
 * deterministic function of its inputs (a feedback loop that chases a moving
 * measurement made the thumb "breathe" while scrolling).
 */
object ScrollbarMath {

    /**
     * Representative item size for a scroll window.
     *
     * The median — not the mean: one short section header (or one tall hero
     * card) entering/leaving the window swings the mean and with it the thumb
     * length, while the median stays put as long as uniform rows dominate.
     * Returns 0 when there is nothing to measure.
     */
    fun medianItemSize(sizes: List<Int>): Float {
        if (sizes.isEmpty()) return 0f
        val sorted = sizes.sorted()
        val n = sorted.size
        return if (n % 2 == 1) {
            sorted[n / 2].toFloat()
        } else {
            (sorted[n / 2 - 1] + sorted[n / 2]).toFloat() / 2f
        }
    }

    /** Mean item size. Kept only to document why it must NOT drive the thumb. */
    fun meanItemSize(sizes: List<Int>): Float {
        if (sizes.isEmpty()) return 0f
        return sizes.sum().toFloat() / sizes.size
    }

    /**
     * Representative item size measured from *positions* rather than from the
     * sizes of whatever happens to be on screen.
     *
     * `[firstItemTopPx, lastItemTopPx]` is the exact distance spanned by
     * `lastItemIndex - firstItemIndex` rows, so the quotient is a true average
     * for that stretch of the list — partially clipped edge items included,
     * which is precisely where a size-based median lies. It also moves
     * smoothly, so the thumb does not change length while scrolling.
     */
    fun averageItemSizePx(
        firstItemTopPx: Int,
        firstItemIndex: Int,
        lastItemTopPx: Int,
        lastItemIndex: Int,
        fallbackSizes: List<Int>
    ): Float {
        val span = lastItemIndex - firstItemIndex
        if (span >= 1) {
            val local = (lastItemTopPx - firstItemTopPx).toFloat() / span
            if (local > 0f) return local
        }
        return medianItemSize(fallbackSizes)
    }

    /**
     * Empty space between the last visible item's bottom and the viewport
     * bottom. Zero when the last item reaches (or overflows) the bottom edge.
     */
    fun bottomGapPx(viewportHeightPx: Float, lastVisibleBottomPx: Float): Float =
        (viewportHeightPx - lastVisibleBottomPx).coerceAtLeast(0f)

    /** Content height still below the last visible item, at the average size. */
    fun remainingBelowPx(avgItemPx: Float, totalItems: Int, lastVisibleIndex: Int): Float =
        (totalItems - lastVisibleIndex - 1).coerceAtLeast(0) * avgItemPx

    /**
     * How far the list has scrolled, derived WITHOUT assuming uniform item
     * heights.
     *
     * The obvious `firstVisibleIndex * avgItem + scrollOffset` is wrong here:
     * the first items are tall outliers (hero cards, sticky header) while the
     * average is measured from uniform content rows, so crossing that boundary
     * makes the estimate jump backwards and the thumb stutter. (Caught by
     * ScrollbarMathTest: fraction went backwards when firstVisibleIndex ticked
     * over.)
     *
     * Instead: what is left to scroll = content below the last visible item +
     * the gap under it. Both are monotonically non-increasing while scrolling,
     * so this is monotone by construction — 0 at the very top, [scrollRangePx]
     * at the very bottom.
     */
    fun scrolledPx(
        scrollRangePx: Float,
        remainingBelowPx: Float,
        bottomGapPx: Float
    ): Float {
        val range = scrollRangePx.coerceAtLeast(0f)
        return (range - remainingBelowPx - bottomGapPx.coerceAtLeast(0f)).coerceIn(0f, range)
    }

    fun contentHeightPx(avgItemPx: Float, totalItems: Int): Float =
        avgItemPx * totalItems

    fun scrollRangePx(contentHeightPx: Float, viewportHeightPx: Float): Float =
        contentHeightPx - viewportHeightPx

    fun fraction(scrolledPx: Float, scrollRangePx: Float): Float {
        if (scrollRangePx <= 0f) return 0f
        return (scrolledPx / scrollRangePx).coerceIn(0f, 1f)
    }

    fun thumbHeightPx(
        trackHeightPx: Float,
        viewportHeightPx: Float,
        contentHeightPx: Float,
        minThumbPx: Float
    ): Float {
        if (trackHeightPx <= 0f || contentHeightPx <= 0f) return minThumbPx
        return (trackHeightPx * (viewportHeightPx / contentHeightPx))
            .coerceIn(minThumbPx.coerceAtMost(trackHeightPx), trackHeightPx)
    }

    // ---------------------------------------------------------------------
    // Index letters
    // ---------------------------------------------------------------------

    const val OTHER_LETTER = "#"

    /** Rank used for everything that is not an ASCII letter. Highest = last. */
    const val OTHER_LETTER_RANK = 26

    /**
     * The index bucket a display name belongs to: `"A".."Z"`, or `"#"`.
     *
     * Note what is *not* here: no special-casing of CJK or of accented letters.
     * Everything the alphabet bar cannot name lands in one single bucket, and
     * the list is sorted so that bucket is contiguous — see
     * [indexLetterComparator].
     */
    fun letterOf(name: String): String {
        val c = name.firstOrNull()?.uppercaseChar() ?: return OTHER_LETTER
        return if (c in 'A'..'Z') c.toString() else OTHER_LETTER
    }

    /** Position of [name]'s bucket in the index bar: 0 for `A` … 26 for `#`. */
    fun letterRank(name: String): Int {
        val c = name.firstOrNull()?.uppercaseChar() ?: return OTHER_LETTER_RANK
        return if (c in 'A'..'Z') c - 'A' else OTHER_LETTER_RANK
    }

    /**
     * Display order for any list that carries an alphabet index bar.
     *
     * The list must be grouped the same way the bar is drawn, so this orders
     * `A..Z` first and puts **one** trailing bucket for every name the bar
     * shows as `#` — symbols, digits, accented and CJK titles alike. Sorting
     * those names by raw code point instead (which is what a plain
     * `compareBy { it.lowercase() }` does) scatters them around the list —
     * `(` before `A`, CJK after `Z` — while the bar still offers a single `#`
     * at the end, so the jump target and the list order disagree. That is the
     * bug this comparator exists to remove.
     *
     * [descending] reverses the alphabet and the names within it, but the `#`
     * bucket stays pinned to the end in both directions, matching the bar.
     */
    fun indexLetterComparator(descending: Boolean): Comparator<String> = Comparator { a, b ->
        val ra = letterRank(a)
        val rb = letterRank(b)
        val rank = when {
            ra == rb -> 0
            // `#` is pinned last regardless of direction.
            ra == OTHER_LETTER_RANK -> 1
            rb == OTHER_LETTER_RANK -> -1
            descending -> rb - ra
            else -> ra - rb
        }
        if (rank != 0) {
            rank
        } else {
            val byName = a.compareTo(b, ignoreCase = true)
            if (descending) -byName else byName
        }
    }

    /**
     * Index-bar letters and their jump rows for a list that is *already
     * grouped* by [indexLetterComparator].
     *
     * Because the grouping is guaranteed, both the letter order and the start
     * of each group are simply first appearance in the displayed list — no
     * ascending/descending remapping needed, and correct for both directions by
     * construction.
     *
     * @param names display names, in the order they are shown.
     * @param span grid columns per row (1 for plain lists).
     * @param headerItems LazyColumn items above the content (mini cards +
     * sticky header = 2 on Home).
     * @return letters to their LazyColumn row indices, parallel.
     */
    fun sectionsForGroupedList(
        names: List<String>,
        span: Int,
        headerItems: Int = 2
    ): Pair<List<String>, List<Int>> {
        require(span >= 1)
        if (names.isEmpty()) return emptyList<String>() to emptyList()
        val letters = ArrayList<String>()
        val firstFlat = HashMap<String, Int>(names.size)
        names.forEachIndexed { flat, name ->
            val letter = letterOf(name)
            // Guarded insert, not `put(...) == null`: put() would overwrite an
            // existing entry with each later item, leaving the LAST flat of the
            // group instead of the first, and every jump target would land on the
            // wrong row.
            if (!firstFlat.containsKey(letter)) {
                firstFlat[letter] = flat
                letters.add(letter)
            }
        }
        val rows = letters.map { headerItems + (firstFlat[it] ?: 0) / span }
        return letters to rows
    }

    // ---------------------------------------------------------------------
    // Thumb dragging
    // ---------------------------------------------------------------------

    /**
     * Inverse of the thumb's position mapping: which item to scroll to for a
     * target [fraction] of the scroll range.
     *
     * Anchoring on the currently visible item (the obvious
     * `anchor + delta/avg`) makes the drag a feedback loop — the estimate
     * compounds its own error and long lists stop responding. Inverting the
     * "what is left below" formula directly is stable, and the two endpoints
     * are special-cased so the list can actually reach its very first and very
     * last item instead of stopping short.
     */
    fun targetIndexForFraction(
        fraction: Float,
        totalItems: Int,
        scrollRangePx: Float,
        avgItemPx: Float
    ): Int {
        if (totalItems <= 0) return 0
        if (fraction <= 0f) return 0
        if (fraction >= 1f) return totalItems - 1
        if (avgItemPx <= 0f || scrollRangePx <= 0f) return 0
        val rowsBelow = (scrollRangePx * (1f - fraction)) / avgItemPx
        return (totalItems - 1 - rowsBelow.toInt()).coerceIn(0, totalItems - 1)
    }
}
