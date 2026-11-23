package com.ivy.transaction

import com.ivy.base.model.TransactionType
import com.ivy.legacy.datamodel.Account

/**
 * The accounts and amount a transaction ends up with after its type changes.
 * Everything else (title, date, category, description, tags, attachment, location) is kept as is.
 */
internal data class ConvertedTransaction(
    val account: Account?,
    val toAccount: Account?,
    val amount: Double,
)

/**
 * Moves the accounts to where the new [to] type expects them:
 * - Income → transfer: the account received the money, so it becomes the destination.
 *   The source is left empty for the user to pick.
 * - Expense → transfer: the account paid, so it stays the source. The destination is left empty.
 * - Transfer → income: the destination becomes the account, with the amount it received ([toAmount]).
 * - Transfer → expense: the source stays the account, with the amount it sent.
 *
 * If the side a transfer needs is empty, the other side is used, so switching back and forth
 * before picking it keeps the original account. Income ↔ expense keeps the account.
 */
internal fun convertTransactionType(
    from: TransactionType,
    to: TransactionType,
    account: Account?,
    toAccount: Account?,
    amount: Double,
    toAmount: Double,
): ConvertedTransaction {
    val unchanged = ConvertedTransaction(account = account, toAccount = toAccount, amount = amount)
    return when {
        from == to -> unchanged
        to == TransactionType.TRANSFER -> when (from) {
            TransactionType.INCOME -> ConvertedTransaction(
                account = null,
                toAccount = account,
                amount = amount
            )

            else -> ConvertedTransaction(account = account, toAccount = null, amount = amount)
        }

        from == TransactionType.TRANSFER -> {
            val useDestination = when (to) {
                TransactionType.INCOME -> toAccount != null
                else -> account == null
            }
            if (useDestination) {
                ConvertedTransaction(account = toAccount, toAccount = null, amount = toAmount)
            } else {
                ConvertedTransaction(account = account, toAccount = null, amount = amount)
            }
        }

        else -> unchanged
    }
}
