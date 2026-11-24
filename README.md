# Ivy Wallet Plus

[![APK](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/apk.yml/badge.svg)](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/apk.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!NOTE]
> **Ivy Wallet Plus is a fork of [Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet).**
> The original maintainers stopped working on Ivy Wallet on Nov 5th, 2024 and encouraged forks under the GPL-3.0 license. This fork is developed independently, isn't affiliated with Ivy Apps Ltd, and isn't published on the Play Store.

Ivy Wallet Plus builds on the Ivy Wallet money manager. It adds:

- [Image and audio attachments](#attachments) on transactions
- [Transactions from bank SMS](#transactions-from-bank-sms), recorded automatically
- [Out-of-sync warnings](#out-of-sync-warnings) when a bank SMS reports a different balance
- [Locations](#locations) on transactions
- [Account balance on any day](#account-balance-on-any-day) in an account's history
- [Account descriptions](#account-descriptions)
- [Alerts for overdue planned payments](#overdue-planned-payment-alerts)
- [Planned payments in budgets](#planned-payments-in-budgets), shown as a lighter fill
- [Changing a transaction's type](#changing-a-transactions-type) between income, expense and transfer, keeping its details
- A [tidier reports filter](#reports-filter)
- The [Ivy Wallet+ name and logo](#ivy-wallet-name-and-logo)

## Attachments

Attach a photo or a voice note, such as a receipt, an invoice or a reminder of what a payment was for, to any expense, income or transfer.

- **Attach:** on the transaction screen, tap **Add attachment**, then choose **Take photo** (camera), **Audio note** (your phone's voice recorder) or **Choose from gallery** (Android photo picker).
- **View or play:** once something is attached, the button reads **Image attached** or **Audio note attached**. Tap it to open the file in your image viewer or audio player.
- **Remove:** long-press the button and confirm. The file is deleted too. Each transaction holds one attachment, so remove the current one before adding another.
- **Spot:** transactions with an attachment show a paperclip next to the amount in transaction lists.

**How it's stored.** The app copies the file into its private storage (`files/attachments/`), so it stays available if you move or delete the original. The transaction keeps the copy's path in the `attachmentUrl` database column. Ivy Wallet's database already had that column but never filled it, so attachments need no migration. When you edit an existing transaction, attaching or removing a file saves right away. On a new transaction, it's saved when you save the transaction.

**Limitations.**

- **Audio note** opens a recorder app through Android's "record sound" request. Not every phone has a recorder that supports it. If yours doesn't, the app says "No voice recorder app found" and nothing is attached.
- Backups don't include attachment files. A backup keeps each transaction's file path, so after restoring on another device the paperclip still shows but tapping it says "File not found".
- Deleting a transaction leaves its attachment file in app storage.

## Transactions from bank SMS

Ivy Wallet Plus can read your bank's SMS alerts and record the transactions for you.

**Set it up** by editing an account and turning on **Auto Listen to SMS**. The settings come in three parts. Each part shows what can be filled in for you. Tap its header (**MANUAL** or **DETAILS**) to also show the fields you fill in yourself, and again to hide them.

1. **Profile** says which SMS belong to the account.
   - **SAMPLE SMS** lets you pick an SMS from your bank, and fills in its sender and the SIM it came in on.
   - **Bank phone no. / contact**: tap **PICK** to choose a contact, or open **MANUAL** to type the sender's name or number. An SMS counts when its sender, or the sender's contact name, contains this text. Left empty, it matches every sender.
   - **Contact SIM no.**: optionally choose a SIM so only messages that arrive on it count. The list shows each SIM as `carrier (ID): phone number · SIM name`. The name is the one you gave the SIM in Android's settings, if any. The number only appears when Android knows it, and it's then checked as well. To type the receiving phone number and SIM slot yourself, choose **Manual Entry** or open **MANUAL**.
2. **Capture menu** picks what a transaction gets besides its amount and type: **Location**, **Date**, **Fee**, **Description** and **Balance & rebalance**. All but **Fee** are on by default. **DETAILS** explains each one. Date, fee, description and balance come from their own patterns or from named groups in the master pattern. A **NO REGEX** tag shows when neither can give a ticked value. See [Locations](#locations) and [Out-of-sync warnings](#out-of-sync-warnings).
3. **SMS structure** holds the patterns. **IMPORT CONFIG** loads them all from a JSON file with any of these keys: `masterMatch`, `useMultiRegex`, `incomeRegex`, `expenseRegex`, `amountRegex`, `dateTimeRegex`, `descriptionRegex`, `balanceRegex` and `feeRegex`.
   - **Simple** has the **Master regex**, always shown. A message must match it, and its first capture group, or a group named `amount`, is read as the amount. If you leave it empty, a generic pattern is used, but you'll usually need one that fits your bank's wording.
   - **MANUAL** reveals **Use advanced multi-regex**, which turns on the separate patterns below. **HIDE** and **SHOW** fold them away. A pattern whose box is off in the Capture menu is tagged **OFF IN CAPTURE MENU**.
   - **TEST WITH AN SMS**, at the bottom, tries the patterns on a real SMS.

| Pattern | What it does |
|---|---|
| Income / Expense identification | If either matches anywhere in the message, it sets the transaction type. |
| Amount | The `amount` group is the amount. Commas are stripped. |
| Date/time | Groups named `day`, `month` (a number or an English name like `Jan`), `year` (2 or 4 digits), `hour`, `minute`, `second` and `ampm`, in any order. Only day and month are required. Used when **Date** is on. |
| Description | Every `description` group from every match, one per line. Used when **Description** is on. |
| Balance | The `balance` group is added to the description as `(Bal: …)`, and checked against the account's balance. Used when **Balance & rebalance** is on. See [Out-of-sync warnings](#out-of-sync-warnings). |
| Fee | Every `fee` group from every match is added up, so a service charge and its VAT become one fee. Used when **Fee** is on: see below. |

**Named groups** like `(?<day>\d{2})` let the values come in any order, so one pattern can read dates written as `20/11/2026` in one SMS and `2026-11-21` in the next. The master pattern can name values too. They're used when a value's own pattern is empty or finds nothing, and in single-regex mode. Patterns without named groups still work by position: group 1 is the amount or balance, the date groups are day, month, year, hour, minute and second, and the description and fee take every group. [Writing SMS patterns](docs/SMS-Patterns.md) lists every name, with examples and tips for sharing a config.

Without a type match, words like "credited", "received", "deposited" or "added" make the transaction income, and anything else makes it an expense. Without a date match, or with **Date** off, the transaction is dated when the SMS arrives. An empty pattern field is ignored, and so is an invalid pattern whose box is off in the Capture menu.

Every pattern runs case-insensitive, with `DOTALL` (`.` also matches new lines) and `MULTILINE` (`^` and `$` match at each line). A long SMS that arrives in several parts is read as one message.

**Test your patterns.** Tap **TEST WITH AN SMS**, at the bottom of **SMS structure**, and pick any SMS from your inbox. The tester runs the same parser as the SMS listener, with the values in the form, including edits you haven't saved. It shows:

- whether the sender and SIM checks pass,
- what each pattern matched and every capture group, and whether the Capture menu leaves it out,
- the transaction that would be created, with its fee, or why none would be.

New lines, no-break spaces and other invisible characters show as orange markers. They're often lost when you copy an SMS to a computer, which is why a pattern can work there but fail on the phone. The picked SMS stays selected, so you can edit a pattern and test again. The tester doesn't check the receiving phone number, or whether another account with a matching sender would get the SMS first.

**Add an SMS by hand.** When the picked SMS gives a transaction, tap **Add transaction** at the bottom of the tester, for example for an SMS that arrived before you turned on listening. It opens a new transaction for this account, filled in with the type, amount, date, title, description and `AUTO-` tag the listener would use. Nothing is saved until you tap **Add** there. It doesn't include the fee, which you add as its own expense. The account has to be saved first, because opening the transaction closes the account form.

**What happens.** When a matching SMS arrives, the app saves the transaction to that account right away, titled `AUTO IN` or `AUTO OUT` plus the account name and a short code made from the message. It tags it `AUTO-<account name>`, a tag created when you save an account with SMS listening on. A "Transaction created" notification then lets you open the transaction to check or edit it. With **Location** on, the transaction also gets the phone's location when the SMS arrived, if one is found within a few seconds.

**Fees.** With **Fee** on and a fee found, a second expense is saved in your Fees category, titled `AUTO FEE` plus the account name and the same short code. It has the same date, location and tag as the transaction, and its own notification. Leave **Fee** off if your bank's amount already includes the fee, or it's counted twice.

**Fees and Rebalance categories.** The app uses the category you already have with that name, even in another case or spacing, or singular instead of plural (`Fee` for Fees). Only when there's none does it create one, the first time it's needed.

**Permissions.** **SAMPLE SMS** and the tester ask for SMS and Contacts access when you open them. **SAMPLE SMS** and the SIM list also ask for Phone access. Otherwise the app doesn't ask for these itself, so allow them in Android's settings under **Apps → Ivy Wallet+ → Permissions**:

- **SMS** is required.
- **Phone** lets the SIM list show your SIMs and their phone numbers.
- **Contacts** lets the app match senders by contact name.
- **Location** set to **Allow all the time** lets the Capture menu's **Location** work while the app is closed. Ticking **Location**, or tapping **ALLOW LOCATION ACCESS** below it, asks for this. With **Allow only while using the app**, SMS transactions get a location only when the app is open.

### Out-of-sync warnings

When a pattern reads the balance from the SMS and **Balance & rebalance** is on in the Capture menu, every SMS transaction (and its fee) is followed by a balance check. If the balance in the SMS differs from the account's balance in the app by a cent or more, an "Out of sync" notification shows both balances. Usually a transaction is missing, doubled or has the wrong amount. From the notification, you can:

- **Tap it** to open Add transaction with the account, your Rebalance category (reused if you have one, otherwise created) and the difference already filled in, as income if the app shows too little or an expense if it shows too much. The difference is worked out again when you tap, so it accounts for changes you made in the meantime, like correcting the SMS transaction. If nothing is left to fix, a message says so.
- **Fix manually** to open the account's transactions and find the problem yourself.
- **Dismiss** it.

Each account has at most one warning. The next SMS for the account replaces it, or removes it once the balances match. Warnings use their own channel, **Account out of sync**, which you can turn off in Android's notification settings for the app. Unlike "Transaction created" notifications, they don't come back after a restart.

## Locations

- **Add:** tap **Add location**, below the date and time on the transaction screen, to save where you are. If needed, the app asks for location permission and to turn location on. It saves coordinates, not a place name.
- **View:** once a location is saved, the row reads **Location attached**. Tap it to open the spot in your maps app.
- **Remove:** long-press **Location attached** and confirm.
- **Spot:** transactions with a location show a pin next to the amount in transaction lists.
- **From SMS:** transactions recorded from a bank SMS get the phone's location automatically, unless **Location** is off in the account's Capture menu. Location must be on, and it's skipped if no fix is found within a few seconds.

## Account balance on any day

Open an account's transaction list and tap a day's date header. That day and every newer day then show the account's end-of-day balance (`Bal: …`) instead of the day's cash flow. Tap the same day again to switch back.

## Account descriptions

Each account can have a description, for example what the account is for or who shares it. Type it under the account's name when you create or edit an account. It shows under the name on the Accounts tab, cut to two lines, and in full at the top of the account's screen. Clearing the text removes it. Descriptions are kept in backups.

## Overdue planned payment alerts

Every morning at 9:00, the app checks for planned payments that are past their due date and still unpaid. Each one gets its own notification with the amount, the account and how many days late it is. They're bundled together and alert once for the whole bundle. Tap one to open that payment, where **Pay** or **Get** settles it. Tap the bundle to open Home and its Overdue section.

- **Repeats:** the alerts come back each morning until the payment is paid or skipped, even if you swiped them away.
- **Clears itself:** paying or skipping a payment anywhere in the app removes its notification right away. Opening the app also clears notifications for payments you edited or deleted.
- **Priority:** the notifications use their own high-importance channel, **Overdue planned payments**, and pop up on screen. To let them through Do Not Disturb, add Ivy Wallet+ to Do Not Disturb's allowed apps, or allow reminders there.
- **Off switch:** turn them off with **Settings → Show notifications** in the app, which also stops the daily transaction reminder, or turn off just this channel in Android's notification settings for the app.

## Planned payments in budgets

On the Budgets screen, each budget's bar also counts the month's planned expenses that are still unpaid, including overdue ones. What you've spent is the solid fill. Planned payments follow it as a lighter fill, so the empty part is what's free to spend. The bar takes its color from spent plus planned, so it turns orange or red before the payments land. The amount reads **Free to spend**, or **Planned exceeds budget by** when the planned payments would take you over. Once a payment is paid, it moves from the lighter fill to the solid one. Budgets with no planned payments look the same as before.

## Changing a transaction's type

On the transaction screen, tap the type button at the top (**Income**, **Expense** or **Transfer**) and pick another type. Ivy Wallet showed no such button on transfers, so a transfer couldn't be changed back. The title, date, category, description, tags, attachment and location stay as they are. The account moves to where the new type needs it:

| From | To | Accounts |
|---|---|---|
| Income | Transfer | The account becomes the destination. Pick the account the money came from. |
| Expense | Transfer | The account stays the source. Pick the destination. |
| Transfer | Income | The destination becomes the account, with the amount it received. |
| Transfer | Expense | The source stays the account, with the amount it sent. |

Until you pick the missing account, the app asks for it and the transfer can't be saved, so an existing transaction keeps its old type. If you switch back before picking one, the original account comes back. When an income becomes a transfer, the destination keeps receiving the income's amount. If the source is in another currency, the amount sent is worked out from it at the current exchange rate. Changing either account or the rate on the exchange-rate card works it out again. Once you type the amount sent yourself, the transfer works like any other: the amount sent stays and the amount received follows the rate.

A loan's own transaction can't become a transfer, and a loan record's type still can't be changed.

## Reports filter

In the reports filter, accounts and categories show as compact chips that wrap onto several lines, in a scrollable area of limited height, instead of a single row you scroll sideways.

## Ivy Wallet+ name and logo

The app is called **Ivy Wallet+** and has a new gradient logo. Its package name is `com.ivy.walletplus`, with `.debug` added for debug and demo builds. So it installs alongside the original Ivy Wallet instead of replacing it, and your Ivy Wallet data doesn't move over by itself. To bring it across, export a backup in Ivy Wallet and import it in Ivy Wallet+.

## Install

There are no store releases. Either download the `Ivy-Wallet-Demo.apk` artifact from a recent [APK workflow run](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/apk.yml) on `main` (you need to be signed in to GitHub), or build it yourself.

## Build

You need Java 17+ and the latest stable Android Studio.

```bash
git clone https://github.com/eyosias-k-negash/ivy-wallet-plus.git
cd ivy-wallet-plus
./gradlew assembleDemo
```

The APK is written to `app/build/outputs/apk/demo/app-demo.apk`. Run `./gradlew testDebugUnitTest` for the unit tests. [CI troubleshooting](docs/CI-Troubleshooting.md) explains each CI check and how to run it locally.

## About Ivy Wallet

_A condensed version of the original project's README._

| | | | |
|:-:|:-:|:-:|:-:|
| ![1](https://user-images.githubusercontent.com/5564499/189540998-4d6cdcd3-ab4d-40f7-85d4-c82fe8a017d1.png) | ![2](https://user-images.githubusercontent.com/5564499/189541011-1ebbd8b6-50fe-432a-91e2-59206efe99ce.png) | ![3](https://user-images.githubusercontent.com/5564499/189541023-35e7f163-d639-4466-9a91-c56890d5a28e.png) | ![4](https://user-images.githubusercontent.com/5564499/189541027-d352314c-fd5c-43eb-82ad-4aba14c7b0fa.png) |

Ivy Wallet is a free, open-source money manager for Android, written in 100% Kotlin and Jetpack Compose. It's a manual expense tracker meant to replace a spreadsheet for your personal finances. It helps you answer three questions: how much money you have in total, how much you spent this month and on what, and how much you can spend while still meeting your goals.

It doesn't have every feature bigger apps have. Its strengths are its UI and UX, its simplicity and its customization options, which earned it spots in YouTube tech reviewers' ["Top/Best Android App in 2021/2022" charts](https://youtube.com/playlist?list=PLguJN0waG1-eSzKMuFMIULrR3MlqJ3cAE).

### Tech stack

- **Core:** [Kotlin](https://kotlinlang.org/), [Jetpack Compose](https://developer.android.com/jetpack/compose), [Material3](https://m3.material.io/), [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) and [Flow](https://kotlinlang.org/docs/flow.html), [Hilt](https://dagger.dev/hilt/), [Arrow](https://arrow-kt.io/)
- **Persistence:** [Room](https://developer.android.com/training/data-storage/room), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Networking:** [Ktor client](https://ktor.io/docs/getting-started-ktor-client.html), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- **Testing:** [JUnit4](https://github.com/junit-team/junit4), [Kotest](https://kotest.io/) assertions, [Paparazzi](https://github.com/cashapp/paparazzi) screenshot tests
- **Build and CI:** Gradle Kotlin DSL with [convention plugins](https://docs.gradle.org/current/samples/sample_convention_plugins.html) and a [version catalog](https://developer.android.com/build/migrate-to-catalogs), [GitHub Actions](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions)
- **Linting:** [Detekt](https://github.com/detekt/detekt), [ktlint](https://github.com/pinterest/ktlint), [Slack's compose-lints](https://slackhq.github.io/compose-lints/)
- **Other:** [Firebase Crashlytics](https://firebase.google.com/products/crashlytics), [Timber](https://github.com/JakeWharton/timber)

### For developers

- [Contributing](CONTRIBUTING.md): how to build, check and submit a change to Ivy Wallet Plus.
- [Developer guidelines](docs/Guidelines.md): architecture, data modeling, error handling and testing conventions.
- [Learning resources](docs/resources/): books, articles and videos recommended by the original team.
- [CI troubleshooting](docs/CI-Troubleshooting.md): what each check does and how to fix it.

### Community project

The original README lists [ivy-wallet-web](https://github.com/pratikkabade/ivy-wallet-web) by [Pratik Kabade](https://github.com/pratikkabade), which lets you use Ivy Wallet from a web browser. It's developed independently and isn't affiliated with Ivy Apps Ltd or this fork, so use it at your own risk.

## Credits and license

Ivy Wallet was created by Ivy Apps Ltd and [its contributors](https://github.com/Ivy-Apps/ivy-wallet/graphs/contributors). Co-founder Stefan Ilijev created the [Ivy design system](https://www.figma.com/file/kSwIa07jcHEHZXo6rzx7dn/Design-System?node-id=0%3A1&mode=dev). Ivy Wallet Plus builds on their work.

Ivy Wallet Plus is licensed under the [GPL-3.0](LICENSE), like the original. It's provided as is, without warranty of any kind.
