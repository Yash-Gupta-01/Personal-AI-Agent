// JNI surface for llama.cpp.
//
// Deliberately narrow: load a model, generate from a prompt, free it. Every
// llama.cpp symbol used by Aether appears in this file and nowhere else, so a
// backend swap replaces this file and its Kotlin binding, nothing more.
//
// Ownership rule: the Kotlin side holds an opaque handle and is responsible for
// calling freeModel exactly once. Nothing here frees a handle implicitly, so a
// use-after-free is a Kotlin lifecycle bug rather than an invisible native one.

#include <jni.h>

#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "AetherLlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

/** Everything one loaded model needs. Freed together, never individually. */
struct AetherModel {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    const llama_vocab *vocab = nullptr;
};

/** Backend init is global and must happen exactly once per process. */
void ensureBackendInitialised() {
    static bool initialised = false;
    if (!initialised) {
        llama_backend_init();
        initialised = true;
    }
}

std::string tokenToText(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    const int32_t n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        return {};
    }
    return std::string(buf, n);
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_aether_llm_NativeLlama_systemInfo(JNIEnv *env, jobject) {
    const char *info = llama_print_system_info();
    return env->NewStringUTF(info == nullptr ? "" : info);
}

JNIEXPORT jboolean JNICALL
Java_com_aether_llm_NativeLlama_isLoaded(JNIEnv *, jobject) {
    return JNI_TRUE;
}

/**
 * Loads a GGUF model and returns an opaque handle, or 0 on failure.
 *
 * Returns 0 rather than throwing: a missing or corrupt model is an expected
 * state (the user may not have downloaded one), and the Kotlin side turns it
 * into LlmUnavailableException where that decision belongs.
 */
JNIEXPORT jlong JNICALL
Java_com_aether_llm_NativeLlama_loadModel(
        JNIEnv *env, jobject, jstring modelPath, jint contextSize, jint threads) {
    ensureBackendInitialised();

    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    llama_model_params modelParams = llama_model_default_params();
    // CPU only. The device's Adreno GPU is not worth the complexity while the
    // ARM int8 matmul kernels are doing the work.
    modelParams.n_gpu_layers = 0;

    llama_model *model = llama_model_load_from_file(path, modelParams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (model == nullptr) {
        LOGE("failed to load model");
        return 0;
    }

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = static_cast<uint32_t>(contextSize);
    ctxParams.n_batch = static_cast<uint32_t>(contextSize);
    ctxParams.n_threads = threads;
    ctxParams.n_threads_batch = threads;

    llama_context *ctx = llama_init_from_model(model, ctxParams);
    if (ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(model);
        return 0;
    }

    // Greedy sampling. Classification wants the single most likely label, and
    // determinism means the same note always yields the same type — a
    // temperature would make classification quietly unreproducible.
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    auto *handle = new AetherModel{model, ctx, sampler, llama_model_get_vocab(model)};

    // Logged because these are the settings that silently determine speed and
    // whether output is coherent at all. Reading them beats guessing later.
    LOGI("model loaded: n_ctx=%u threads=%d chat_template=%s",
         llama_n_ctx(ctx),
         threads,
         llama_model_chat_template(model, nullptr) != nullptr ? "present" : "MISSING");

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL
Java_com_aether_llm_NativeLlama_freeModel(JNIEnv *, jobject, jlong handle) {
    if (handle == 0) {
        return;
    }
    auto *m = reinterpret_cast<AetherModel *>(handle);
    llama_sampler_free(m->sampler);
    llama_free(m->ctx);
    llama_model_free(m->model);
    delete m;
}

/**
 * Formats a single user message using the model's own chat template.
 *
 * The template is read from the GGUF metadata rather than hardcoded. Every
 * model family expects a different format, and feeding one the wrong format
 * produces fluent nonsense — a failure indistinguishable from the model simply
 * being incapable. Reading it from the file makes swapping models safe.
 *
 * Returns null when the model carries no template, which the caller treats as
 * "send the raw prompt" rather than guessing at a format.
 */
JNIEXPORT jstring JNICALL
Java_com_aether_llm_NativeLlama_applyChatTemplate(
        JNIEnv *env, jobject, jlong handle, jstring userMessage) {
    if (handle == 0) {
        return nullptr;
    }
    auto *m = reinterpret_cast<AetherModel *>(handle);

    const char *tmpl = llama_model_chat_template(m->model, nullptr);
    if (tmpl == nullptr) {
        return nullptr;
    }

    const char *content = env->GetStringUTFChars(userMessage, nullptr);
    llama_chat_message message{"user", content};

    // add_ass = true appends the assistant turn opener, without which the model
    // continues the user's message instead of replying to it.
    std::vector<char> buf(strlen(content) * 4 + 1024);
    int32_t written = llama_chat_apply_template(
            tmpl, &message, 1, /* add_ass */ true, buf.data(), static_cast<int32_t>(buf.size()));

    if (written > static_cast<int32_t>(buf.size())) {
        buf.resize(written + 1);
        written = llama_chat_apply_template(
                tmpl, &message, 1, true, buf.data(), static_cast<int32_t>(buf.size()));
    }

    env->ReleaseStringUTFChars(userMessage, content);

    if (written < 0) {
        LOGE("chat template failed");
        return nullptr;
    }
    return env->NewStringUTF(std::string(buf.data(), written).c_str());
}

/**
 * Generates up to maxTokens of text from prompt. Returns null on failure.
 *
 * The KV cache is cleared first so each call is independent. Classification
 * runs many unrelated prompts through one loaded model, and leaking state
 * between them would let one note's content influence the next note's label.
 */
JNIEXPORT jstring JNICALL
Java_com_aether_llm_NativeLlama_generate(
        JNIEnv *env, jobject, jlong handle, jstring prompt, jint maxTokens) {
    if (handle == 0) {
        return nullptr;
    }
    auto *m = reinterpret_cast<AetherModel *>(handle);

    llama_memory_clear(llama_get_memory(m->ctx), true);
    llama_sampler_reset(m->sampler);

    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    const auto promptLen = static_cast<int32_t>(strlen(promptChars));

    // Negative return is the required token count; llama.cpp uses it to ask
    // for a correctly sized buffer rather than truncating silently.
    int32_t needed = -llama_tokenize(
            m->vocab, promptChars, promptLen, nullptr, 0, true, true);
    std::vector<llama_token> tokens(needed);
    const int32_t nTokens = llama_tokenize(
            m->vocab, promptChars, promptLen, tokens.data(),
            static_cast<int32_t>(tokens.size()), true, true);
    env->ReleaseStringUTFChars(prompt, promptChars);

    if (nTokens <= 0) {
        LOGE("tokenisation failed");
        return nullptr;
    }

    const auto nCtx = static_cast<int32_t>(llama_n_ctx(m->ctx));
    if (nTokens + maxTokens > nCtx) {
        // Refuses rather than truncating. A silently truncated prompt loses the
        // end of the note, which is often the part that determines its type.
        LOGE("prompt too long: %d tokens + %d generated exceeds context %d",
             nTokens, maxTokens, nCtx);
        return nullptr;
    }

    tokens.resize(nTokens);
    llama_batch batch = llama_batch_get_one(tokens.data(), nTokens);
    if (llama_decode(m->ctx, batch) != 0) {
        LOGE("prompt decode failed");
        return nullptr;
    }

    std::string output;
    for (int32_t i = 0; i < maxTokens; i++) {
        llama_token token = llama_sampler_sample(m->sampler, m->ctx, -1);
        if (llama_vocab_is_eog(m->vocab, token)) {
            break;
        }
        output += tokenToText(m->vocab, token);

        llama_batch next = llama_batch_get_one(&token, 1);
        if (llama_decode(m->ctx, next) != 0) {
            LOGE("decode failed at token %d", i);
            break;
        }
    }

    return env->NewStringUTF(output.c_str());
}

} // extern "C"
