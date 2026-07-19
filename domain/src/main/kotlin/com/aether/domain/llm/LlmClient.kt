package com.aether.domain.llm

/**
 * Port for language-model inference.
 *
 * Declared in the domain and implemented by infrastructure, so nothing outside
 * `:llm` ever learns which runtime is underneath. Swapping llama.cpp for
 * another backend means providing another implementation of this interface and
 * changing one DI binding.
 *
 * Only [generate] exists because only [generate] has a caller. `embeddings()`
 * arrives with semantic search, and `tokenize()`/`healthCheck()` when something
 * needs them. An unimplemented method declared today is a burden every future
 * backend has to satisfy for no benefit.
 */
interface LlmClient {

    /**
     * Runs [prompt] and returns the generated text.
     *
     * @param maxTokens hard cap on the response. Callers doing closed-set
     *   classification should pass a very small value — it bounds latency and
     *   leaves the model no room to ramble past the answer.
     *
     * @throws LlmUnavailableException when no model is loaded.
     */
    suspend fun generate(prompt: String, maxTokens: Int): String
}

/**
 * Thrown when inference cannot run at all — typically no model downloaded yet.
 *
 * Distinct from a model returning nonsense: this means "ask again later",
 * whereas nonsense means "this memory could not be classified".
 */
class LlmUnavailableException(message: String) : Exception(message)
