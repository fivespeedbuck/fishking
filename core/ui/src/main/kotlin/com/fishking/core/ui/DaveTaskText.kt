package com.fishking.core.ui

internal data class DaveTaskText(
    val title: String,
    val tags: List<String>,
)

/**
 * Keep the stored title untouched while presenting inline #tokens on DaveList's
 * dedicated metadata line. A later repository projection can supply linked tags.
 */
internal fun splitDaveTaskText(raw: String): DaveTaskText {
    val matches = tagPattern.findAll(raw).toList()
    if (matches.isEmpty()) return DaveTaskText(raw, emptyList())
    val tags = matches.map { it.groupValues[1] }.distinct()
    val title = tagPattern.replace(raw, " ").replace(whitespacePattern, " ").trim()
    return DaveTaskText(title.ifEmpty { raw }, tags)
}

private val tagPattern = Regex("(?<!\\S)#([^\\s#]+)")
private val whitespacePattern = Regex("\\s+")

/** Keep tags on the existing title storage path so edit scope and cancel still apply. */
fun replaceDaveTaskTags(raw: String, input: String): String {
    val title = tagPattern.replace(raw, " ").replace(whitespacePattern, " ").trim()
    val tags = input.split(Regex("[\\s#,，]+"))
        .map(String::trim).filter(String::isNotEmpty).distinct()
    return (listOf(title).filter(String::isNotEmpty) + tags.map { "#$it" }).joinToString(" ")
}

fun daveTaskTags(raw: String): List<String> = splitDaveTaskText(raw).tags
