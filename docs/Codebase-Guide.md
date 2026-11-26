# Codebase guide

This guide is for people changing the code. It explains how the repository is laid out and where each Ivy Wallet Plus feature lives. What each feature does for the person using the app is in the [user guide](User-Guide.md), and how to submit a change is in [Contributing](../CONTRIBUTING.md).

Most of the code is upstream Ivy Wallet. The fork started with the image attachments commit (`b591969c`, August 2026). Everything before it is upstream, so `git log b591969c^..main` shows the fork's work.

## Repository layout

| Path | What's in it |
|---|---|
| `app/` | The application module: `RootActivity`, `RootViewModel` (startup, deep links from notifications), and the SMS receivers and notification managers in `com.ivy.wallet.sms`. |
| `feature/*` | One module per screen or area, such as `edit-transaction`, `budgets`, `settings` and `reports`. |
| `shared/data/*` | Room database, entities, DAOs, repositories, mappers and backups (`core`), and the domain models (`model`). |
| `shared/domain/` | Use cases and `AppStarter`. |
| `shared/ui/*`, `shared/base`, `shared/common-ui` | Design system, navigation, strings and shared helpers. |
| `temp/legacy-code/` | Older code that's still in use, including the account form, the SMS parser, notifications and many domain actions. Much of the fork's SMS work lives here because the account form does. |
| `temp/old-design/` | Older UI components. |
| `widget/*` | Home screen widgets. |
| `buildSrc/` | Gradle convention plugins (`ivy.feature`, `ivy.hilt`, `ivy.room`, `ivy.paparazzi`, …). |
| `ci-actions/` | Kotlin programs that CI runs, such as the Compose stability check. |
| `scripts/` | Shell helpers for checks and baselines. |
| `docs/` | User and developer documentation. |

> [!NOTE]
> In `temp/legacy-code`, package names often don't match folders. For example, `IvyNotificationChannel` is in the package `com.ivy.wallet.android.notification` but in the folder `com/ivy/legacy/android/notification`. Search by class name rather than by path.

## Build variants

| Variant | Package | App name | Use it for |
|---|---|---|---|
| `debug` | `com.ivy.walletplus.debug` | Ivy Wallet+ Debug | Development. Greyscale launcher icon, so it's easy to tell apart. |
| `demo` | `com.ivy.walletplus.debug` | Ivy Wallet+ | A release-like build anyone can make. Minified, signed with the committed debug key. |
| `release` | `com.ivy.walletplus` | Ivy Wallet+ | The APK on GitHub Releases. Built by the [Release workflow](#releasing), which has the signing key. |

Debug and demo share a package, so installing one replaces the other. Neither replaces a release install.

## Releasing

Releases are built and signed by [`release.yml`](../.github/workflows/release.yml) when a version tag is pushed.

1. On a branch, set `version-name` and raise `version-code` by one in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Merge it into `main`.
2. Tag the merge commit with the same version and push the tag:
   ```bash
   git tag v0.6.0 origin/main
   git push origin v0.6.0
   ```
3. The workflow checks that the tag matches `version-name`, builds `assembleRelease` with the keystore from the `SIGNING_KEY` secret, and checks the APK's certificate against `EXPECTED_CERT_SHA256`. It then creates a **draft** release with `ivy-wallet-plus-v0.6.0.apk`, its `.sha256` file and notes generated from the merged pull requests. The R8 mapping file is kept as an artifact of the run.
4. Edit the draft's notes and publish it. **Settings → Releases** in the app shows the notes. It lists each line as an item and skips headings, so use short bullet points.

The signing secrets are `SIGNING_KEY` (the release keystore, base64-encoded), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. A release signed with any other key can't update an installed one, which is why the workflow fails on a certificate mismatch.

## Features and where they live

Paths are shortened: `legacy/` means `temp/legacy-code/src/main/java/com/ivy/legacy/`, and `app/sms/` means `app/src/main/java/com/ivy/wallet/sms/`.

| Feature | Main code | Tests |
|---|---|---|
| [Attachments](User-Guide.md#attachments) | `EditTransactionViewModel` / `EditTransactionScreen` (pick, capture, record, open, remove), [`AttachmentStorage`](../shared/data/core/src/main/java/com/ivy/data/attachment/AttachmentStorage.kt) (folder, type by extension, usage, purge), `file_paths.xml` (`FileProvider`), paperclip in `TransactionCard` | `AttachmentStorageTest` |
| [Disk usage](User-Guide.md#attachments) | [`DiskUsageModal`](../feature/settings/src/main/java/com/ivy/settings/DiskUsageModal.kt), `SettingsViewModel`, `TransactionDao.findAllWithAttachment()` | `AttachmentStorageTest` |
| [Locations](User-Guide.md#locations) | `Location` model, location columns on `TransactionEntity`, `EditTransactionViewModel` (capture, open in maps, remove), pin in `TransactionCard` | |
| [Changing a transaction's type](User-Guide.md#changing-a-transactions-type) | [`TransactionTypeConversion.kt`](../feature/edit-transaction/src/main/java/com/ivy/transaction/TransactionTypeConversion.kt) (pure), `EditTransactionViewModel`, `legacy/ui/component/edit/core/Toolbar.kt` | `TransactionTypeConversionTest` |
| [Tag picker](User-Guide.md#tag-picker) | `legacy/ui/component/tags/ShowTagModal.kt` | |
| [Transactions from bank SMS](User-Guide.md#transactions-from-bank-sms) | See [The SMS pipeline](#the-sms-pipeline) below | `SmsParserTest`, `SmsBalanceCheckTest`, `SmsCategoryNameTest` |
| [Account descriptions](User-Guide.md#account-descriptions) | `accounts.description` column, `AccountMapper`, `AccountModal`, `AccountsTab`, `TransactionsScreen` | `AccountMapperTest` |
| [Balance on any day](User-Guide.md#account-balance-on-any-day) | `TransactionsViewModel` (balance mode), `legacy/ui/component/transaction/HistoryDateDivider.kt` | |
| [Overdue planned payment alerts](User-Guide.md#overdue-planned-payment-alerts) | [`OverduePaymentReminderLogic`](../temp/legacy-code/src/main/java/com/ivy/legacy/notification/OverduePaymentReminderLogic.kt) and its worker, `PlannedPaymentsLogic` (refresh after pay or skip), `RootViewModel` (schedule on start) | |
| [Planned payments in budgets](User-Guide.md#planned-payments-in-budgets) | `BudgetViewModel`, `legacy/legacy/ui/theme/components/BudgetBattery.kt` (`budgetBatteryAmounts`) | `BudgetBatteryAmountsTest` |
| [Tags left out of income and expense](User-Guide.md#tags-left-out-of-income-and-expense) | [`ExcludedStatsTagsUseCase`](../shared/domain/src/main/java/com/ivy/domain/usecase/wallet/ExcludedStatsTagsUseCase.kt) (DataStore), `CalcIncomeExpenseAct`, `HistoryWithDateDivsAct`, `TrnDateDividers`, `FeaturesScreen` | `CalcIncomeExpenseActTest`, `TrnDateDividersTest` |
| [Reports filter](User-Guide.md#reports-filter) | `feature/reports/.../FilterOverlay.kt` | |
| [Name and logo](User-Guide.md#ivy-wallet-name-and-logo) | `app/build.gradle.kts` (`applicationId`, `app_name`), launcher icons in `app/src/*/res`, logos in `shared/ui/core` | |

## The SMS pipeline

The SMS feature has a few moving parts. This is what happens when a bank SMS arrives.

1. **[`SmsReceiver`](../app/src/main/java/com/ivy/wallet/sms/SmsReceiver.kt)** gets `SMS_RECEIVED`, joins the parts of a long SMS, and works in the background with `goAsync()`. Android ends a broadcast after about 10 seconds, so everything below has to fit in that time.
2. **Account match:** the first account with **Auto Listen to SMS** on whose sender filter (`SmsParser.senderMatches`), SIM (`smsSubscriptionId`) and receiving number (`smsReceiverPhone`) all fit gets the SMS.
3. **Parse:** [`SmsParser.parse`](../temp/legacy-code/src/main/java/com/ivy/legacy/sms/SmsParser.kt) applies the account's `SmsParsingRules` and returns every step, plus a `ParsedSmsTransaction` or the reason there's none. It's pure Kotlin, so it's unit tested.
4. **Location:** if the account captures it, [`SmsLocationProvider`](../app/src/main/java/com/ivy/wallet/sms/SmsLocationProvider.kt) waits up to about 6 seconds for a fix, then falls back to a recent last known location.
5. **Save:** inside `balanceLock`, the receiver saves the transaction with the account's `AUTO-` tag, then the fee as its own expense in the Fees category ([`SmsCategories`](../app/src/main/java/com/ivy/wallet/sms/SmsCategories.kt) finds or creates it, with names matched by `SmsCategoryName`).
6. **Notify:** each saved transaction goes on the [`SmsReviewQueue`](../temp/legacy-code/src/main/java/com/ivy/legacy/sms/SmsReviewQueue.kt) and gets an ongoing notification from `SmsTransactionNotificationManager`.
7. **Balance check:** if the SMS gave a balance, [`AccountOutOfSyncNotificationManager`](../app/src/main/java/com/ivy/wallet/sms/AccountOutOfSyncNotificationManager.kt) compares it with `CalcAccBalanceAct` through `SmsBalanceCheck`, and posts, replaces or clears the account's warning.

Around it:

- **The form:** the SMS settings are in [`AccountSmsSettings.kt`](../temp/legacy-code/src/main/java/com/ivy/legacy/legacy/ui/theme/modal/edit/AccountSmsSettings.kt), shown by `AccountModal`. `SmsTesterModal` and `SmsSamplePickerModal` share the inbox list.
- **The tester runs the same parser** with the form's unsaved values. If you change parsing, change `SmsParser`, not the receiver or the tester, so they can't drift apart.
- **The review queue** is a SharedPreferences list. A transaction leaves it when **Save** is tapped on its edit screen (`EditTransactionViewModel`), when it's deleted there, or when its notification is swiped away (`SmsNotificationDismissedReceiver`). `SmsNotificationRestoreReceiver` and `RootViewModel` post the notifications again after a reboot, an update or on app start.
- **Tapping a notification** opens the app through `RootViewModel`, which reads the intent extras (`EXTRA_EDIT_TRANSACTION_ID`, the out-of-sync extras) and navigates.

## Database

The Room database is `IvyRoomDatabase` in `shared/data/core`, at version 137. Each version's schema is exported to `shared/data/core/schemas/com.ivy.data.db.IvyRoomDatabase/`, and every fork version upgrades with an auto-migration. These are the fork's versions:

| Version | Added | For |
|---|---|---|
| 131 | `transactions`: `locationLat`, `locationLng`, `locationName` | Locations |
| 132 | `accounts`: `smsAutoListenEnabled`, `smsSenderPhone`, `smsSubscriptionId`, `smsParsingRegex`, `autoTagId` | SMS listening |
| 133 | `accounts`: `smsReceiverPhone` | Receiving number check |
| 134 | `accounts`: `useMultiRegex`, `smsIncomeRegex`, `smsExpenseRegex`, `smsAmountRegex`, `smsDateTimeRegex`, `smsDescriptionRegex`, `smsBalanceRegex` | Multi-regex patterns |
| 135 | `accounts`: `smsCaptureLocation` | Location on SMS transactions |
| 136 | `accounts`: `smsFeeRegex`, `smsCaptureDate`, `smsCaptureFee`, `smsCaptureDescription`, `smsCaptureBalance` | Capture menu and fees |
| 137 | `accounts`: `description` | Account descriptions |

`transactions.attachmentUrl` already existed upstream, so attachments needed no migration. [Contributing](../CONTRIBUTING.md#database-changes) has the checklist for adding a column.

> [!WARNING]
> The committed `130.json` was edited by the fork to include the location columns, so it no longer matches upstream's version 130. Don't edit committed schema files. Add a new version instead.

## Notifications and background work

| Channel ID | Name in Android settings | Posted by | When |
|---|---|---|---|
| `transaction_reminder` | Transaction reminder | `TransactionReminderWorker` | 20:00 daily, on days with no transactions (upstream) |
| `sms_transaction` | SMS Transactions | `SmsTransactionNotificationManager` | A transaction or fee was created from an SMS |
| `overdue_payment` | Overdue planned payments | `OverduePaymentReminderLogic` | 9:00 daily, and refreshed after pay, skip or app start |
| `account_out_of_sync` | Account out of sync | `AccountOutOfSyncNotificationManager` | An SMS balance is 10 or more away from the app's |

The channels are defined in `IvyNotificationChannel`. Both workers are `@HiltWorker`s scheduled from `RootViewModel`. They need `androidx.hilt:hilt-compiler`, which the `ivy.hilt` convention plugin adds. Without it, WorkManager can't create them and they never run.

Receivers declared in the manifest: `SmsReceiver` (`SMS_RECEIVED`), `SmsNotificationRestoreReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`), `SmsNotificationDismissedReceiver` and `AccountOutOfSyncDismissReceiver` (notification actions), plus upstream's widget receivers.

## Permissions added by the fork

| Permission | Used for | Asked for |
|---|---|---|
| `RECEIVE_SMS` | `SmsReceiver` | Never by the app. People allow SMS in Android's settings. |
| `READ_SMS` | Inbox list in the SMS tester and **SAMPLE SMS** | When either opens |
| `READ_CONTACTS` | Matching senders by contact name | With the SMS tester and **SAMPLE SMS**. **PICK** uses Android's contact picker, which needs no permission. |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS` | SIM list and receiving number | When the SIM list or **SAMPLE SMS** opens |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | **Add location**, SMS location | On **Add location**, or when **Location** is ticked in the Capture menu |
| `ACCESS_BACKGROUND_LOCATION` | SMS location while the app is closed | After foreground location, from the Capture menu |
| `RECEIVE_BOOT_COMPLETED` | Restoring SMS notifications after a reboot | Granted at install |

## Tests

Unit tests run with `./gradlew testDebugUnitTest`, or per module, for example `./gradlew :temp:legacy-code:testDebugUnitTest --tests com.ivy.legacy.sms.SmsParserTest`. The fork's tests:

| Test | Module | Covers |
|---|---|---|
| `SmsParserTest` | `temp:legacy-code` | Named and positional groups, dates, fees, capture flags, single-regex mode, invalid patterns |
| `SmsBalanceCheckTest` | `temp:legacy-code` | The rounding tolerance and the 10-or-more warning threshold |
| `SmsCategoryNameTest` | `temp:legacy-code` | Matching Fees and Rebalance category names |
| `BudgetBatteryAmountsTest` | `temp:legacy-code` | Free to spend and over-budget text |
| `CalcIncomeExpenseActTest`, `TrnDateDividersTest` | `temp:legacy-code` | Excluded tags in totals and daily sums |
| `TransactionTypeConversionTest` | `feature:edit-transaction` | Accounts and amounts when a type changes |
| `AttachmentStorageTest` | `shared:data:core` | Types, usage and purging |
| `AccountMapperTest` | `shared:data:core` | Mapping account fields, including the description |

Test data must be made up: SMS from "Example Bank" in USD, phone numbers in the reserved `+1 555 0100` range. Never paste a real SMS into a test, even with the numbers changed. Fake DAOs such as `FakeTransactionDao` live in `shared/data/core/.../dao/fake/`. When you add a DAO method, implement it there too.

For a quick look at Compose UI without a device, a throwaway [Paparazzi](guidelines/Screenshot-Testing.md) test can render it. Delete it before you commit, unless it's meant as a real screenshot test.

## Known issues

These are known and not fixed yet. They're good places to start.

**SMS**

- Amounts with a decimal comma, like `1.234,56`, are read wrong, because `SmsParser` removes every comma.
- An empty sender filter matches every sender, and the first matching account gets the SMS. The form and the tester warn about it.
- `SmsReceiver` logs sender numbers and contact names with `Log.i` in every build.
- `RECEIVE_SMS` is never requested at runtime.

**Attachments and backups**

- Backups store each attachment's path but not the file.
- Deleting a transaction leaves its attachment file behind. **Disk usage** counts and purges these files.
- `BackupDataUseCase` writes the export to `cacheDir`, which Android may clear when storage is low.

**Repository and build**

- The Detekt and Lint baselines were regenerated for v0.6.0, so they also cover findings in fork code, such as long SMS functions and Compose rules in the SMS settings. Fixing those and removing them from the baselines is welcome.
- `app/google-services.json` still points at the original Ivy Wallet Firebase project, with the package names changed. Crashlytics mapping uploads are off for that reason.
- `gradle/wrapper/gradle-wrapper.properties` has no `distributionSha256Sum`.
- New strings added by the fork are in English only.
