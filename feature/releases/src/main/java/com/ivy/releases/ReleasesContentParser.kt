package com.ivy.releases

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class ReleasesContentParser @Inject constructor() {
    fun toCommitsList(commits: String?): ImmutableList<String> {
        if (commits.isNullOrBlank()) return persistentListOf()

        // Release notes are Markdown: show each list item as plain text, skip headings and blank lines
        return commits.lines()
            .map { it.trim() }
            .filterNot { it.isEmpty() || it.startsWith("#") }
            .map { line ->
                line.removePrefix("- ")
                    .removePrefix("* ")
                    .replace(MarkdownLink) { it.groupValues[1] }
                    .replace("**", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .toImmutableList()
    }

    companion object {
        private const val REDUNDANT_CHARS_COUNT = 10
        private val MarkdownLink = Regex("""\[([^\]]+)]\([^)]+\)""")
    }

    fun toReleaseDate(date: String): String {
        // e.g. transforms original "2023-09-16T17:42:08Z" into "2023-09-16"
        return date.dropLast(REDUNDANT_CHARS_COUNT)
    }
}
