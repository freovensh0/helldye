package dev.scdeal.helldye.assistant

/** Shared image limits. The gateway enforces the same envelope; the app enforces them before send. */
object ImageRules {
    const val MAX_EDGE_PX = 1_568
    const val JPEG_QUALITY = 80
    const val MAX_BYTES_PER_IMAGE = 1_500_000
    const val MAX_IMAGES = 4
    const val MAX_TOTAL_BYTES = 6_000_000

    /** Scales one edge to [maxEdge] while preserving aspect ratio; never upscales. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return (width * scale).toInt().coerceAtLeast(1) to
            (height * scale).toInt().coerceAtLeast(1)
    }

    fun decodedBytes(base64: String): Int = (base64.trimEnd('=').length * 3) / 4

    fun withinBudget(existing: List<Attachment.Photo>, incoming: Attachment.Photo): Boolean {
        if (existing.size >= MAX_IMAGES) return false
        val total = existing.sumOf { decodedBytes(it.base64) } + decodedBytes(incoming.base64)
        return total <= MAX_TOTAL_BYTES
    }
}
