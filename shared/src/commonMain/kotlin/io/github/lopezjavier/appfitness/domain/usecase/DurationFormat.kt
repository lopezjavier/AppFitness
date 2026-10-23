package io.github.lopezjavier.appfitness.domain.usecase

/** Pure mm:ss (h:mm:ss from one hour) formatting and parsing of set durations. */
object DurationFormat {
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    /** `90 -> "01:30"`, `3665 -> "1:01:05"`; negative values count as 0. */
    fun formatMmSs(totalSeconds: Int): String {
        val total = totalSeconds.coerceAtLeast(0)
        val hours = total / SECONDS_PER_HOUR
        val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        return if (hours > 0) {
            "$hours:${minutes.pad()}:${seconds.pad()}"
        } else {
            "${minutes.pad()}:${seconds.pad()}"
        }
    }

    /**
     * Parses `m:ss`, `mm:ss`, `h:mm:ss` or a plain number of seconds; null when empty, malformed or when minutes or
     * seconds of a multi-part value are 60 or more (`"1:75"`).
     */
    fun parseMmSs(text: String): Int? {
        val parts = text.trim().split(":")
        val numbers = parts.map { part -> part.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() }
        val valid = parts.size <= MAX_PARTS && numbers.all { it != null } &&
            numbers.drop(1).all { it != null && it < SECONDS_PER_MINUTE }
        return if (valid) numbers.filterNotNull().fold(0) { total, part -> total * SECONDS_PER_MINUTE + part } else null
    }

    private const val MAX_PARTS = 3

    private fun Int.pad() = toString().padStart(2, '0')
}
