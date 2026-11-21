package com.aether.llm

/**
 * The on-device decision intelligence model Aether runs on.
 *
 * Pinned to the open-source Jev decision model (`feder-cr/jev`).
 *
 * **Why jevos-v4-q4_k_m**:
 * - MIT licensed, open-weights decision model optimized for on-device CPU inference.
 * - Single-forward-pass logit scoring (zero token decoding) over typed queries (`noul`, `choice`, `score`).
 * - Calibrated probabilities with sub-50ms execution on mobile ARM64.
 * - Pinned filename and size match [LocalJevDecisionAdapter] and [DecisionBenchmarkTest].
 */
object ModelCatalog {

    const val DISPLAY_NAME = "Jev Decision Engine (v4)"

    const val FILE_NAME = "jevos-v4-q4_k_m.gguf"

    const val DOWNLOAD_URL =
        "https://huggingface.co/feder-cr/jev/resolve/main/$FILE_NAME"

    /**
     * Exact byte size from the server: 619,289,312 bytes (~590 MB).
     *
     * Used to detect truncated or corrupted downloads before handing the file
     * to llama.cpp's native parser.
     */
    const val EXPECTED_BYTES = 619_289_312L

    /** Roughly 590 MB, for showing the user what they are about to download. */
    const val APPROX_MEGABYTES = EXPECTED_BYTES / (1024 * 1024)
}
