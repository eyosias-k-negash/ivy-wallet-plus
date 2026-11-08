# Ivy Wallet Plus

[![APK](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/apk.yml/badge.svg)](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/apk.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!NOTE]
> **Ivy Wallet Plus is a fork of [Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet).**
> The original maintainers stopped working on Ivy Wallet on Nov 5th, 2024 and encouraged forks under the GPL-3.0 license. This fork is developed independently, isn't affiliated with Ivy Apps Ltd, and isn't published on the Play Store.

Ivy Wallet Plus builds on the Ivy Wallet money manager. It adds:

- [Image and audio attachments](#attachments) on transactions
- [Transactions from bank SMS](#transactions-from-bank-sms), recorded automatically
- [Locations](#locations) on transactions
- [Account balance on any day](#account-balance-on-any-day) in an account's history
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

- **Audio note** opens a recorder app through Android's "record sound" request. Not every phone has a recorder that supports it.
- Backups don't include attachment files. A backup keeps each transaction's file path, so after restoring on another device the paperclip still shows but tapping it says "File not found".
- Deleting a transaction leaves its attachment file in app storage.

## Transactions from bank SMS

Ivy Wallet Plus can read your bank's SMS alerts and record the transactions for you.

**Set it up** by editing an account:

1. Turn on **Auto Listen to SMS**.
2. Enter the bank's sender name or number, or tap **PICK** to choose a contact. An SMS counts when its sender, or the sender's contact name, contains this text.
3. Optionally, choose a **SIM slot** or enter the **receiving phone number**, so only messages that arrive on that SIM count.
4. Enter an **SMS Parsing Regex**. A message must match it, and its first capture group is read as the amount. If you leave it empty, a generic pattern is used, but you'll usually need one that fits your bank's wording.
5. Optionally, turn on **Use Advanced Multi-Regex** to use separate patterns, described below. **IMPORT CONFIG** loads all the patterns from a JSON file with any of these keys: `masterMatch`, `useMultiRegex`, `incomeRegex`, `expenseRegex`, `amountRegex`, `dateTimeRegex`, `descriptionRegex` and `balanceRegex`.

| Pattern | What it does |
|---|---|
| Income / Expense Identification | If either matches anywhere in the message, it sets the transaction type. |
| Amount Extraction | The first capture group is the amount. Commas are stripped. |
| Date/Time Extraction | Capture groups are day, month (a number or an English short name like `Jan`) and year (2 or 4 digits), optionally followed by hour, minute and second. |
| Description Extraction | Every capture group from every match, one per line. |
| Balance Extraction | The first capture group is added to the description as `(Bal: …)`. |

Without a type match, or without multi-regex, words like "credited", "received", "deposited" or "added" make the transaction income, and anything else makes it an expense. Without a date match, the transaction is dated when the SMS arrives.

**What happens.** When a matching SMS arrives, the app saves the transaction to that account right away, titled `AUTO IN` or `AUTO OUT` plus the account name and a short code made from the message. It tags it `AUTO-<account name>`, a tag created when you save an account with SMS listening on. A "Transaction created" notification then lets you open the transaction to check or edit it.

**Permissions.** The app doesn't ask for these itself. Allow them in Android's settings under **Apps → Ivy Wallet+ → Permissions**:

- **SMS** is required.
- **Phone** lets the SIM picker list your SIMs.
- **Contacts** lets the app match senders by contact name.

## Locations

- **Add:** tap the pin next to the transaction's date and time to save where you are. If needed, the app asks for location permission and to turn location on. It saves coordinates, not a place name.
- **View:** once a location is saved, the pin is highlighted. Tap it to open the spot in your maps app.
- **Remove:** long-press the pin and confirm.
- **Spot:** transactions with a location show a pin next to the amount in transaction lists.

## Account balance on any day

Open an account's transaction list and tap a day's date header. That day and every newer day then show the account's end-of-day balance (`Bal: …`) instead of the day's cash flow. Tap the same day again to switch back.

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
