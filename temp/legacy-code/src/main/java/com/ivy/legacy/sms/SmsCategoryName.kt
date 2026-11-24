package com.ivy.legacy.sms

import java.util.Locale

/** Finds a category SMS features use, like Fees or Rebalance, among the user's own, so it isn't made twice. */
object SmsCategoryName {
    /**
     * The item of [existing] whose name means [name]: the same name whatever its case or spacing,
     * or else its singular or plural ("Fee" for "Fees"). Null when there's none.
     */
    fun <T> match(name: String, existing: List<T>, nameOf: (T) -> String): T? {
        val same = normalized(name)
        val similar = singular(name)
        return existing.find { normalized(nameOf(it)) == same }
            ?: existing.find { singular(nameOf(it)) == similar }
    }

    private fun normalized(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private fun singular(name: String): String = normalized(name).removeSuffix("s")
}
