package com.ivy.data.attachment

import android.content.Context
import com.ivy.base.TestDispatchersProvider
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.fake.FakeTransactionDao
import com.ivy.data.db.entity.TransactionEntity
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class AttachmentStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val transactionDao = FakeTransactionDao()

    private lateinit var attachmentsDir: File
    private lateinit var storage: AttachmentStorage

    @Before
    fun setup() {
        val context = mockk<Context> {
            every { filesDir } returns tempFolder.root
        }
        attachmentsDir = AttachmentStorage.attachmentsDir(context)
        storage = AttachmentStorage(
            appContext = context,
            transactionDao = transactionDao,
            writeTransactionDao = transactionDao,
            dispatchers = TestDispatchersProvider,
        )
    }

    @Test
    fun `classifies attachments by extension`() {
        AttachmentType.of("/data/attachments/attach_1.m4a") shouldBe AttachmentType.Audio
        AttachmentType.of("/data/attachments/attach_2.MP3") shouldBe AttachmentType.Audio
        AttachmentType.of("/data/attachments/attach_3.jpg") shouldBe AttachmentType.Image
        AttachmentType.of("/data/attachments/attach_4") shouldBe AttachmentType.Image
    }

    @Test
    fun `usage is zero when nothing was ever attached`() = runTest {
        storage.usage() shouldBe mapOf(
            AttachmentType.Image to AttachmentUsage(files = 0, bytes = 0),
            AttachmentType.Audio to AttachmentUsage(files = 0, bytes = 0),
        )
    }

    @Test
    fun `usage adds up the files of each type`() = runTest {
        attachment("a.jpg", bytes = 300)
        attachment("b.jpg", bytes = 200)
        attachment("c.m4a", bytes = 50)

        storage.usage() shouldBe mapOf(
            AttachmentType.Image to AttachmentUsage(files = 2, bytes = 500),
            AttachmentType.Audio to AttachmentUsage(files = 1, bytes = 50),
        )
    }

    @Test
    fun `purge deletes only that type and detaches it from transactions`() = runTest {
        val image = attachment("a.jpg", bytes = 300)
        val leftover = attachment("b.jpg", bytes = 200)
        val audio = attachment("c.m4a", bytes = 50)
        val imageTrn = transaction(attachmentUrl = image.absolutePath)
        val audioTrn = transaction(attachmentUrl = audio.absolutePath)
        val plainTrn = transaction(attachmentUrl = null)

        storage.purge(AttachmentType.Image)

        image.exists() shouldBe false
        leftover.exists() shouldBe false
        audio.exists() shouldBe true
        transactionDao.findById(imageTrn.id)?.attachmentUrl shouldBe null
        transactionDao.findById(audioTrn.id)?.attachmentUrl shouldBe audio.absolutePath
        transactionDao.findById(plainTrn.id) shouldBe plainTrn
        storage.usage() shouldBe mapOf(
            AttachmentType.Image to AttachmentUsage(files = 0, bytes = 0),
            AttachmentType.Audio to AttachmentUsage(files = 1, bytes = 50),
        )
    }

    private fun attachment(name: String, bytes: Int): File {
        attachmentsDir.mkdirs()
        return File(attachmentsDir, name).apply { writeBytes(ByteArray(bytes)) }
    }

    private suspend fun transaction(attachmentUrl: String?): TransactionEntity =
        TransactionEntity(
            accountId = UUID.randomUUID(),
            type = TransactionType.EXPENSE,
            amount = 10.0,
            attachmentUrl = attachmentUrl,
        ).also { transactionDao.save(it) }
}
