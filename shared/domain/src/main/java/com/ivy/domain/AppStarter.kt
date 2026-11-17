package com.ivy.domain

import android.content.Intent
import com.ivy.base.model.TransactionType
import java.util.UUID

/**
 * A component used to start the **RootActivity** without knowing about it.
 */
interface AppStarter {
    fun getRootIntent(): Intent
    fun defaultStart()
    fun addTransactionStart(type: TransactionType)
    fun getEditTransactionIntent(transactionId: UUID, type: TransactionType): Intent
}
