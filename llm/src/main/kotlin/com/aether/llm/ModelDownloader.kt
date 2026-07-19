package com.aether.llm

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Progress of the model download.
 *
 * [bytesDownloaded] and [totalBytes] are reported so the UI can show real
 * progress. A 700MB download with an indeterminate spinner is indistinguishable
 * from a hang, and the natural response is to kill the app mid-download.
 */
sealed interface DownloadState {
    data object Idle : DownloadState
    data object Pending : DownloadState
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState {
        val fraction: Float
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }

    /** Copying the finished download into private internal storage. */
    data object Finalising : DownloadState
    data object Completed : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Downloads the GGUF model using Android's [DownloadManager].
 *
 * DownloadManager rather than a hand-rolled HTTP loop: it survives the app
 * being backgrounded or killed, resumes across connectivity drops, and handles
 * redirects — all of which a 700MB transfer on a phone will actually encounter,
 * and none of which is worth reimplementing.
 */
class ModelDownloader(
    private val context: Context,
    private val modelStore: ModelStore,
) {

    private val manager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /**
     * Starts the download and emits progress until it finishes or fails.
     *
     * Progress is polled because DownloadManager offers no observable API. The
     * interval is deliberately coarse: a 700MB download does not need
     * sub-second UI updates, and each poll is a database query.
     */
    fun download(): Flow<DownloadState> = flow {
        if (modelStore.isModelPresent()) {
            emit(DownloadState.Completed)
            return@flow
        }

        // A partial file from an earlier attempt would make DownloadManager
        // write alongside it rather than replace it, leaving two useless files.
        if (modelStore.isModelPartial()) {
            modelStore.deleteModel()
        }

        // A complete download interrupted before it was moved in: finish the
        // job rather than re-downloading 700MB.
        if (modelStore.isStagedModelComplete()) {
            emit(DownloadState.Finalising)
            emit(promote())
            return@flow
        }

        emit(DownloadState.Pending)

        val request = DownloadManager.Request(Uri.parse(ModelCatalog.DOWNLOAD_URL))
            .setTitle("Aether language model")
            .setDescription(ModelCatalog.DISPLAY_NAME)
            .setDestinationInExternalFilesDir(context, null, modelStore.stagingRelativePath)
            .setAllowedOverMetered(false) // 700MB should not land on a mobile bill
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )

        val downloadId = try {
            manager.enqueue(request)
        } catch (e: Exception) {
            emit(DownloadState.Failed(e.message ?: "Could not start the download"))
            return@flow
        }

        while (true) {
            val status = query(downloadId)
            emit(status)
            when (status) {
                is DownloadState.Failed -> return@flow
                is DownloadState.Finalising -> {
                    emit(promote())
                    return@flow
                }

                else -> delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun promote(): DownloadState =
        if (modelStore.promoteStagedModel()) {
            DownloadState.Completed
        } else {
            DownloadState.Failed("Could not move the model into private storage")
        }

    private fun query(downloadId: Long): DownloadState {
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return DownloadState.Failed("The download disappeared")
            }

            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL ->
                    // Verified rather than trusted: DownloadManager reports
                    // success for an HTML error page saved under the right name.
                    if (modelStore.isStagedModelComplete()) {
                        DownloadState.Finalising
                    } else {
                        DownloadState.Failed(
                            "Downloaded file is the wrong size — it may be incomplete",
                        )
                    }

                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                    )
                    DownloadState.Failed(describeFailure(reason))
                }

                DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING ->
                    DownloadState.Pending

                else -> DownloadState.Downloading(
                    downloaded,
                    if (total > 0) total else ModelCatalog.EXPECTED_BYTES,
                )
            }
        }
    }

    /** Turns DownloadManager's numeric reasons into something actionable. */
    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE ->
            "Not enough storage — the model needs about ${ModelCatalog.APPROX_MEGABYTES} MB"

        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No storage available to download to"
        DownloadManager.ERROR_CANNOT_RESUME -> "The download could not resume; try again"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "The connection dropped during download"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The download link redirected too many times"
        else -> "Download failed (code $reason)"
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 500L
    }
}
