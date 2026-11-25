package com.ivy.data.attachment

import android.content.Context
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class AttachmentType {
    Image,
    Audio;

    companion object {
        private val AudioExtensions = setOf("m4a", "mp3")

        // Audio notes are saved as .m4a. Everything else came from the camera or the photo picker.
        fun of(path: String): AttachmentType {
            val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return if (extension in AudioExtensions) Audio else Image
        }
    }
}

data class AttachmentUsage(
    val files: Int,
    val bytes: Long,
)

/**
 * The files attached to transactions, copied into the app's private storage.
 */
class AttachmentStorage @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val transactionDao: TransactionDao,
    private val writeTransactionDao: WriteTransactionDao,
    private val dispatchers: DispatchersProvider,
) {
    /**
     * Space taken by each type of attachment. Counts every file in the folder, including ones
     * left behind by deleted transactions.
     */
    suspend fun usage(): Map<AttachmentType, AttachmentUsage> = withContext(dispatchers.io) {
        val filesByType = attachmentFiles().groupBy { AttachmentType.of(it.name) }
        AttachmentType.entries.associateWith { type ->
            val files = filesByType[type].orEmpty()
            AttachmentUsage(files = files.size, bytes = files.sumOf { it.length() })
        }
    }

    /**
     * Deletes every attachment file of [type] and removes it from the transactions it was
     * attached to. The transactions themselves are kept.
     */
    suspend fun purge(type: AttachmentType): Unit = withContext(dispatchers.io) {
        // Detach first: if this is interrupted, a leftover file is only counted until the next
        // purge, but a transaction pointing at a deleted file would say "File not found".
        val detached = transactionDao.findAllWithAttachment()
            .filter { AttachmentType.of(it.attachmentUrl.orEmpty()) == type }
            .map { it.copy(attachmentUrl = null) }
        writeTransactionDao.saveMany(detached)

        attachmentFiles()
            .filter { AttachmentType.of(it.name) == type }
            .forEach { it.delete() }
    }

    private fun attachmentFiles(): List<File> =
        attachmentsDir(appContext).listFiles()?.filter { it.isFile }.orEmpty()

    companion object {
        fun attachmentsDir(context: Context): File = File(context.filesDir, "attachments")
    }
}
