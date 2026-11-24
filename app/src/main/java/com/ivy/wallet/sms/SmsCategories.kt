package com.ivy.wallet.sms

import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.design.l0_system.Ivy
import com.ivy.legacy.sms.SmsCategoryName
import com.ivy.wallet.domain.deprecated.logic.CategoryCreator
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The categories SMS features file transactions under, like Rebalance and Fees. */
@Singleton
class SmsCategories @Inject constructor(
    private val categoryDao: CategoryDao,
    private val categoryCreator: CategoryCreator,
) {
    // One at a time, so an SMS and a tapped notification can't both create the same category
    private val lock = Mutex()

    /**
     * The user's category called [name], also matching another case or spacing, or its singular
     * or plural. Only when there's none is it created, with [icon].
     */
    suspend fun findOrCreate(name: String, icon: String): UUID? = lock.withLock {
        // Reads the database, not the repository's cache, which a restored backup leaves out of date
        SmsCategoryName.match(name, categoryDao.findAll()) { it.name }
            ?.let { return@withLock it.id }

        var createdId: UUID? = null
        categoryCreator.createCategory(
            CreateCategoryData(name = name, color = Ivy, icon = icon)
        ) { createdId = it.id.value }
        createdId
    }
}
