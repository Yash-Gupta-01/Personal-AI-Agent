package com.aether.domain.memory

/**
 * Builds the classification prompt and parses the model's answer.
 *
 * Pure functions with no dependency on any runtime, so the whole
 * prompt-and-parse contract is testable on the JVM in milliseconds without a
 * model present. This is the part most likely to need iteration, and it would
 * be miserable to tune through a device test.
 *
 * **The model is never asked for JSON.** A 1.5B model emits malformed JSON
 * often enough to be a real source of failure. Instead it is asked for one word
 * from a closed set, and [parse] validates against [MemoryType], falling back to
 * [MemoryType.UNCLASSIFIED] on anything unexpected. Robustness comes from the
 * output space being tiny, not from asking the model nicely.
 */
object MemoryClassifier {

    /** Small on purpose: one label is a handful of tokens, and this bounds latency. */
    const val MAX_TOKENS: Int = 8

    /**
     * Labels the model may choose from.
     *
     * Derived from [MemoryType] rather than hardcoded, so adding a type cannot
     * silently leave the prompt out of date.
     */
    private val selectableTypes: List<MemoryType> =
        MemoryType.entries.filterNot { it == MemoryType.UNCLASSIFIED }

    /**
     * The template is trimmed *before* the labels and note are substituted.
     *
     * `"""...$labels...""".trimIndent()` looks equivalent and is not:
     * interpolation happens first, so the injected block's unindented lines
     * make the common indent zero and every other line keeps its source
     * indentation. The model then reads a prompt where half the lines are
     * indented twelve spaces and half are not — which reliably degraded the
     * answer to whichever category was listed first.
     */
    private val TEMPLATE: String = """
        Classify the note into exactly one label.
        Choose the label that best matches the main meaning of the note.

        Labels:
        {labels}

        Decision order — use the first rule that fits:
        {rules}

        Examples:
        {examples}

        Rules:
        - Reply with the label name only.
        - No explanation, no punctuation, no extra words.

        Note: {note}
        Label:
    """.trimIndent()

    fun buildPrompt(content: String): String =
        TEMPLATE
            .replace(
                "{labels}",
                selectableTypes.joinToString("\n") { "- ${it.name}: ${it.description}" },
            )
            .replace(
                "{rules}",
                selectableTypes.withIndex().joinToString("\n") { (i, type) ->
                    "${i + 1}. If the note is about ${type.description}, choose ${type.name}."
                },
            )
            .replace(
                "{examples}",
                selectableTypes.joinToString("\n") { "Note: ${it.example}\nLabel: ${it.name}" },
            )
            // Substituted last, so a note containing "{labels}" cannot inject.
            .replace("{note}", content.trim())

    /**
     * Reads a [MemoryType] out of the model's raw output.
     *
     * Tolerant of the ways a small model deviates while still being right —
     * lowercase, surrounding whitespace, a trailing full stop, a leading
     * "Label:", or a short preamble before the label. Genuinely unrecognised
     * output becomes [MemoryType.UNCLASSIFIED], which leaves the memory in the
     * queue rather than mislabelling it.
     */
    fun parse(rawOutput: String): MemoryType {
        val normalised = rawOutput
            .substringAfterLast("Label:", rawOutput)
            .uppercase()
            .replace(NON_LETTER, " ")
            .trim()

        if (normalised.isEmpty()) return MemoryType.UNCLASSIFIED

        val words = normalised.split(WHITESPACE).filter { it.isNotBlank() }

        // Exact match on the first word is the overwhelmingly common case.
        selectableTypes.firstOrNull { it.name == words.firstOrNull() }?.let { return it }

        // Otherwise accept a single label appearing anywhere. Requiring exactly
        // one match matters: output naming two categories is genuinely
        // ambiguous, and guessing between them would be worse than deferring.
        val mentioned = selectableTypes.filter { type -> words.any { it == type.name } }
        return mentioned.singleOrNull() ?: MemoryType.UNCLASSIFIED
    }

    private val NON_LETTER = Regex("[^A-Z]")
    private val WHITESPACE = Regex("\\s+")
}
