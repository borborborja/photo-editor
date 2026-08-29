package com.hinnka.mycamera.fossin

/**
 * Bounded preview budgets keep a gesture responsive on phones with modest memory while exports
 * always use the user's requested full-resolution path. They are intentionally deterministic so
 * the same project cannot oscillate between resolutions while a finger is on screen.
 */
internal object FossinPreviewQuality {
    const val SIMPLE_MAX_EDGE = 1_728
    const val BALANCED_MAX_EDGE = 1_440
    const val HEAVY_MAX_EDGE = 1_280

    fun maxEdge(
        isRaw: Boolean,
        enabledOperationCount: Int,
        expensiveOperationCount: Int,
    ): Int = when {
        isRaw || expensiveOperationCount >= 2 || enabledOperationCount >= 9 -> HEAVY_MAX_EDGE
        expensiveOperationCount == 1 || enabledOperationCount >= 5 -> BALANCED_MAX_EDGE
        else -> SIMPLE_MAX_EDGE
    }

    fun rawGestureDebounceMillis(isRaw: Boolean): Long = if (isRaw) 90L else 0L
}
