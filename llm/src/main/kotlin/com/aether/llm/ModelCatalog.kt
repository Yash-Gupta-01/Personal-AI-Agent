package com.aether.llm

/**
 * The model Aether runs on.
 *
 * A single place, so changing model is editing these constants — nothing else
 * in the app names a model. That is the payoff of choosing GGUF: the whole of
 * HuggingFace stays available without a code change.
 *
 * **Why LFM2.5-1.2B-Instruct**, checked against the pinned llama.cpp (b10068):
 * - `LLM_ARCH_LFM2` is supported.
 * - Its chat template is ChatML-like, so llama.cpp's heuristic detector
 *   recognises it. This matters because llama.cpp is explicitly *not* a Jinja
 *   parser: an unrecognised template returns -1 and silently degrades to a raw,
 *   unformatted prompt.
 * - No thinking/reasoning mode, so an 8-token budget yields a bare label rather
 *   than an unterminated `<think>` block.
 * - Hybrid conv + GQA design targets fast on-device prefill, which is the real
 *   cost here — classification generates only ~8 tokens, so prefill and model
 *   load dominate, not decode speed.
 *
 * Licensed under LFM1.0, Liquid's own licence — not OSI-approved. Fine for
 * personal use; worth re-reading before this is ever distributed.
 * Qwen3.5-0.8B (Apache-2.0, 507MB) is the known-good fallback if the newer
 * LFM2 path misbehaves on ARM.
 */
object ModelCatalog {

    const val DISPLAY_NAME = "LFM2.5 1.2B Instruct"

    const val FILE_NAME = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"

    const val DOWNLOAD_URL =
        "https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF/resolve/main/$FILE_NAME"

    /**
     * Exact size from the server, used to detect a truncated download.
     *
     * A partial GGUF fails inside llama.cpp with a message about tensors, which
     * is a miserable way to discover the network dropped. Checking the byte
     * count turns that into "the download did not finish".
     *
     * The file's SHA-256, per HuggingFace's etag, is
     * 93e807019ab026414a5c8def0d7815957049512ecd3d629060573d108634cbbb
     * Recorded for manual verification but not enforced: HTTPS already rules out
     * silent corruption, and a legitimate upstream re-upload would otherwise
     * lock the app out of a perfectly good model.
     */
    const val EXPECTED_BYTES = 730_895_168L

    /** Roughly 697 MB, for showing the user what they are about to download. */
    const val APPROX_MEGABYTES = EXPECTED_BYTES / (1024 * 1024)
}
