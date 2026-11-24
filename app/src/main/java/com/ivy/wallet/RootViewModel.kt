package com.ivy.wallet

import android.content.Intent
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.base.Toaster
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.Theme
import com.ivy.base.legacy.stringRes
import com.ivy.base.model.TransactionType
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.repository.LegalRepository
import com.ivy.frp.test.TestIdlingResource
import com.ivy.legacy.IvyWalletCtx
import com.ivy.legacy.notification.OverduePaymentReminderLogic
import com.ivy.legacy.utils.ioThread
import com.ivy.legacy.utils.readOnly
import com.ivy.navigation.DisclaimerScreen
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.MainScreen
import com.ivy.navigation.Navigation
import com.ivy.navigation.OnboardingScreen
import com.ivy.navigation.TransactionsScreen
import com.ivy.ui.R
import com.ivy.wallet.domain.deprecated.logic.notification.TransactionReminderLogic
import com.ivy.wallet.migrations.MigrationsManager
import com.ivy.wallet.sms.AccountOutOfSyncNotificationManager
import com.ivy.wallet.sms.SmsTransactionNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class RootViewModel @Inject constructor(
    private val ivyContext: IvyWalletCtx,
    private val nav: Navigation,
    private val settingsDao: SettingsDao,
    private val sharedPrefs: SharedPrefs,
    private val transactionReminderLogic: TransactionReminderLogic,
    private val overduePaymentReminderLogic: OverduePaymentReminderLogic,
    private val migrationsManager: MigrationsManager,
    private val legalRepo: LegalRepository,
    private val smsNotificationManager: SmsTransactionNotificationManager,
    private val outOfSyncNotificationManager: AccountOutOfSyncNotificationManager,
    private val toaster: Toaster,
) : ViewModel() {

    companion object {
        const val EXTRA_ADD_TRANSACTION_TYPE = "add_transaction_type_extra"
        const val EXTRA_EDIT_TRANSACTION_ID = "edit_transaction_id_extra"
        const val EXTRA_TRANSACTION_TYPE = "transaction_type_extra"
        const val EXTRA_AMOUNT = "amount_extra"
        const val EXTRA_TITLE = "title_extra"
        const val EXTRA_DESCRIPTION = "description_extra"
        const val EXTRA_DATE_TIME = "date_time_extra"
        const val EXTRA_ACCOUNT_ID = "account_id_extra"
        const val EXTRA_TAG_IDS = "tag_ids_extra"
        const val EXTRA_REBALANCE_ACCOUNT_ID = "rebalance_account_id_extra"
        const val EXTRA_REBALANCE_SMS_BALANCE = "rebalance_sms_balance_extra"
        const val EXTRA_OPEN_ACCOUNT_ID = "open_account_id_extra"

        const val USER_INACTIVITY_TIME_LIMIT = 60 // Time in seconds
    }

    private var appLockEnabled = false

    private val _appLocked = MutableStateFlow<Boolean?>(null)
    val appLocked = _appLocked.readOnly()

    fun start(systemDarkMode: Boolean, intent: Intent) {
        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                val theme = settingsDao.findAll().firstOrNull()?.theme
                    ?: if (systemDarkMode) Theme.DARK else Theme.LIGHT
                ivyContext.switchTheme(theme)

                ivyContext.initStartDayOfMonthInMemory(sharedPrefs = sharedPrefs)
            }

            TestIdlingResource.decrement()
        }

        viewModelScope.launch {
            TestIdlingResource.increment()

            ioThread {
                appLockEnabled = sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false)
                // initial app locked state
                _appLocked.value = appLockEnabled

                if (isOnboardingCompleted()) {
                    navigateOnboardedUser(intent)
                } else {
                    nav.navigateTo(OnboardingScreen)
                }
                if (!legalRepo.isDisclaimerAccepted()) {
                    nav.navigateTo(DisclaimerScreen)
                }
            }

            TestIdlingResource.decrement()
        }

        viewModelScope.launch {
            migrationsManager.executeMigrations()
        }

        viewModelScope.launch {
            // A force stop clears the app's notifications, and nothing can run again until it's opened
            ioThread { smsNotificationManager.restorePendingNotifications() }
        }
    }

    private suspend fun navigateOnboardedUser(intent: Intent) {
        overduePaymentReminderLogic.scheduleReminder()
        viewModelScope.launch(Dispatchers.IO) {
            // clear notifications of payments that were settled, edited or deleted meanwhile
            overduePaymentReminderLogic.refresh()
        }

        if (!handleRebalanceStart(intent) && !handleOpenAccountStart(intent) && !handleSpecialStart(intent)) {
            nav.navigateTo(MainScreen)
            transactionReminderLogic.scheduleReminder()
        }
    }

    @Suppress("SwallowedException")
    private fun handleSpecialStart(intent: Intent): Boolean {
        val editTrnIdStr = intent.getStringExtra(EXTRA_EDIT_TRANSACTION_ID)
        if (editTrnIdStr != null) {
            val transactionId = try {
                UUID.fromString(editTrnIdStr)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (transactionId != null) {
                val trnTypeStr = intent.getStringExtra(EXTRA_TRANSACTION_TYPE)
                    ?: intent.getStringExtra(EXTRA_ADD_TRANSACTION_TYPE)
                val trnType = try {
                    trnTypeStr?.let { TransactionType.valueOf(it) } ?: TransactionType.EXPENSE
                } catch (e: IllegalArgumentException) {
                    TransactionType.EXPENSE
                }

                nav.navigateTo(
                    EditTransactionScreen(
                        initialTransactionId = transactionId,
                        type = trnType
                    )
                )
                return true
            }
        }

        val addTrnType: TransactionType? = try {
            intent.getSerializableExtra(EXTRA_ADD_TRANSACTION_TYPE) as? TransactionType
                ?: TransactionType.valueOf(intent.getStringExtra(EXTRA_ADD_TRANSACTION_TYPE) ?: "")
        } catch (e: IllegalArgumentException) {
            null
        }

        if (addTrnType != null) {
            val amount = if (intent.hasExtra(EXTRA_AMOUNT)) intent.getDoubleExtra(EXTRA_AMOUNT, 0.0) else null
            val title = intent.getStringExtra(EXTRA_TITLE)
            val description = intent.getStringExtra(EXTRA_DESCRIPTION)
            val dateTime = if (intent.hasExtra(EXTRA_DATE_TIME)) intent.getLongExtra(EXTRA_DATE_TIME, 0L) else null
            val accountIdStr = intent.getStringExtra(EXTRA_ACCOUNT_ID)
            val accountId = accountIdStr?.let { UUID.fromString(it) }
            val tagIdsStrList = intent.getStringArrayExtra(EXTRA_TAG_IDS)
            val tagIds = tagIdsStrList?.map { UUID.fromString(it) } ?: emptyList()

            nav.navigateTo(
                EditTransactionScreen(
                    initialTransactionId = null,
                    type = addTrnType,
                    amount = amount,
                    title = title,
                    description = description,
                    dateTime = dateTime,
                    accountId = accountId,
                    tagIds = tagIds
                )
            )

            return true
        }

        return false
    }

    /** A tap on an account's out-of-sync warning opens Add transaction with the difference. */
    private suspend fun handleRebalanceStart(intent: Intent): Boolean {
        val accountId = intent.uuidExtra(EXTRA_REBALANCE_ACCOUNT_ID) ?: return false
        outOfSyncNotificationManager.dismiss(accountId)
        val screen = outOfSyncNotificationManager.rebalanceScreen(
            accountId = accountId,
            smsBalance = intent.getDoubleExtra(EXTRA_REBALANCE_SMS_BALANCE, 0.0)
        )
        if (screen != null) {
            nav.navigateTo(screen)
        } else {
            toaster.show("The account already matches the bank's balance")
        }
        return screen != null
    }

    /** "Fix manually" on an account's out-of-sync warning opens the account's transactions. */
    private fun handleOpenAccountStart(intent: Intent): Boolean {
        val accountId = intent.uuidExtra(EXTRA_OPEN_ACCOUNT_ID) ?: return false
        outOfSyncNotificationManager.dismiss(accountId)
        // Home underneath, so Back leaves the account for the rest of the app
        nav.navigateTo(MainScreen)
        nav.navigateTo(TransactionsScreen(accountId = accountId, categoryId = null))
        return true
    }

    private fun Intent.uuidExtra(name: String): UUID? =
        getStringExtra(name)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    @Suppress("EmptyFunctionBlock")
    fun handleBiometricAuthResult(
        onAuthSuccess: () -> Unit = {}
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.d(stringRes(R.string.authentication_succeeded))
                unlockApp()
                onAuthSuccess()
            }

            override fun onAuthenticationFailed() {
                Timber.d(stringRes(R.string.authentication_failed))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            }
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        return sharedPrefs.getBoolean(SharedPrefs.ONBOARDING_COMPLETED, false)
    }

    // App Lock & UserInactivity --------------------------------------------------------------------
    fun isAppLockEnabled(): Boolean {
        return appLockEnabled
    }

    fun isAppLocked(): Boolean {
        // by default we assume that the app is locked
        return appLocked.value ?: true
    }

    fun lockApp() {
        _appLocked.value = true
    }

    fun unlockApp() {
        _appLocked.value = false
    }

    private val userInactiveTime = AtomicLong(0)
    private var userInactiveJob: Job? = null

    @Suppress("MagicNumber")
    fun startUserInactiveTimeCounter() {
        if (userInactiveJob != null && userInactiveJob!!.isActive) return

        userInactiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (userInactiveTime.get() < USER_INACTIVITY_TIME_LIMIT &&
                userInactiveJob != null && !userInactiveJob?.isCancelled!!
            ) {
                delay(1000)
                userInactiveTime.incrementAndGet()
            }

            if (!isAppLocked()) {
                lockApp()
            }

            cancel()
        }
    }

    fun checkUserInactiveTimeStatus() {
        if (userInactiveTime.get() < USER_INACTIVITY_TIME_LIMIT) {
            if (userInactiveJob != null && userInactiveJob?.isCancelled == false) {
                userInactiveJob?.cancel()
                resetUserInactiveTimer()
            }
        }
    }

    fun resetUserInactiveTimer() {
        userInactiveTime.set(0)
    }
}
