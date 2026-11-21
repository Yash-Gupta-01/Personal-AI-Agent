package com.aether.llm

import android.content.Context
import java.io.File

/**
 * Where the GGUF model lives on disk.
 *
 * Internal storage. Aether's whole premise is that your thoughts never leave
 * the device, and app-specific *external* storage
 * (`/sdcard/Android/data/<pkg>`) is reachable over adb and by some file
 * managers. Internal storage is genuinely private and removed on uninstall.
 *
 * DownloadManager cannot write here — it only targets external destinations —
 * so [ModelDownloader] downloads to a staging file and moves it in. That costs
 * one copy of ~700MB on first setup and nothing thereafter.
 */
class ModelStore(private val context: Context) {

    /** Where DownloadManager stages the file before it is moved in. */
    val stagingRelativePath: String get() = "$MODEL_DIRECTORY/${ModelCatalog.FILE_NAME}.part"

    /** The staging file's absolute location, in external storage. */
    val stagingFile: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, stagingRelativePath)

    val modelFile: File
        get() = File(context.filesDir, "$MODEL_DIRECTORY/${ModelCatalog.FILE_NAME}")

    /**
     * True when a complete, usable model is present.
     *
     * Compares the exact byte count rather than merely checking existence. An
     * interrupted download leaves a plausible-looking file behind, and
     * llama.cpp's failure on a truncated GGUF talks about tensors — an
     * unhelpful way to learn the network dropped.
     */
    fun isModelPresent(): Boolean =
        modelFile.let { it.isFile && it.length() == ModelCatalog.EXPECTED_BYTES }

    /**
     * True when a file exists but is the wrong size, i.e. a partial download.
     *
     * Distinguished from "absent" so the UI can offer to resume or clear it
     * instead of silently appearing to have done nothing.
     */
    fun isModelPartial(): Boolean =
        modelFile.let { it.isFile && it.length() != ModelCatalog.EXPECTED_BYTES }

    /** True when a complete download is staged but not yet moved in. */
    fun isStagedModelComplete(): Boolean =
        stagingFile.let { it.isFile && it.length() == ModelCatalog.EXPECTED_BYTES }

    fun deleteModel(): Boolean = modelFile.delete()

    /**
     * Moves the staged download into internal storage.
     *
     * Copies rather than renames because the two live on different volumes.
     * The staging file is deleted afterwards so the device is not left holding
     * 1.4GB for a 700MB model.
     */
    fun promoteStagedModel(): Boolean {
        val staged = stagingFile
        if (!staged.isFile || staged.length() != ModelCatalog.EXPECTED_BYTES) return false

        modelFile.parentFile?.mkdirs()
        return try {
            staged.inputStream().use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
            staged.delete()
            isModelPresent()
        } catch (_: Exception) {
            // Leave the staging file: a failed copy is usually low disk space,
            // and deleting it would force a fresh 700MB download.
            modelFile.delete()
            false
        }
    }

    private companion object {
        const val MODEL_DIRECTORY = "models"
    }
}
