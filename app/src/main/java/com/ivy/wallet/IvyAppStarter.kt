package com.ivy.wallet

import android.content.Context
import android.content.Intent
import com.ivy.base.model.TransactionType
import com.ivy.domain.AppStarter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class IvyAppStarter @Inject constructor(
    @ApplicationContext
    private val context: Context
) : AppStarter {

    override fun getRootIntent(): Intent {
        return Intent(context, RootActivity::class.java)
    }

    override fun defaultStart() {
        context.startActivity(
            getRootIntent().apply {
                applyWidgetStartFlags()
            }
        )
    }

    override fun addTransactionStart(type: TransactionType) {
        context.startActivity(
            getRootIntent().apply {
                putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TYPE, type)
                applyWidgetStartFlags()
            }
        )
    }

    override fun getEditTransactionIntent(transactionId: UUID, type: TransactionType): Intent {
        return getRootIntent().apply {
            putExtra(RootViewModel.EXTRA_EDIT_TRANSACTION_ID, transactionId.toString())
            putExtra(RootViewModel.EXTRA_TRANSACTION_TYPE, type.name)
            applyWidgetStartFlags()
        }
    }

    private fun Intent.applyWidgetStartFlags() {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}
