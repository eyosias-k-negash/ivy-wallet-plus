# Ivy Wallet Plus

[![CI](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/ci.yml/badge.svg)](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/eyosias-k-negash/ivy-wallet-plus)](https://github.com/eyosias-k-negash/ivy-wallet-plus/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!NOTE]
> **Ivy Wallet Plus is a fork of [Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet).**
> The original maintainers stopped working on Ivy Wallet on Nov 5th, 2024 and encouraged forks under the GPL-3.0 license. This fork is developed independently, isn't affiliated with Ivy Apps Ltd, and isn't published on the Play Store.

Ivy Wallet Plus is a free, open-source money manager for Android. It keeps everything [Ivy Wallet](#about-ivy-wallet) does and adds what it was missing: transactions recorded automatically from your bank's SMS, receipts and locations on transactions, budgets that count your planned payments, and reminders for bills you haven't paid.

## Highlights

### Transactions from bank SMS

Turn on **Auto Listen to SMS** for an account, and every matching SMS from your bank becomes a transaction, with no typing. [How it works →](docs/User-Guide.md#transactions-from-bank-sms)

- **Works with any bank or wallet.** You describe its SMS with regular expressions, using named groups like `amount`, `balance` and `day`, and can share them as a JSON config. [Writing SMS patterns →](docs/SMS-Patterns.md)
- **Built-in tester.** Try your patterns on any SMS in your inbox and see what each one matched, step by step, before you save.
- **Set up from a sample.** Pick one of your bank's messages, and the sender and SIM are filled in for you.
- **More than the amount.** Each transaction can also get the SMS's date and description, the phone's location, and any fee, saved as its own expense.
- **Out-of-sync warnings.** When the balance in the SMS is 10 or more away from the app's, a notification says so, and one tap opens the transaction that fixes it.
- **Nothing slips by.** A transaction's notification stays until you've reviewed it, even after a reboot.

### Transactions

- **[Attachments](docs/User-Guide.md#attachments):** add a photo or a voice note, like a receipt or an invoice, to any transaction. **Settings → Disk usage** shows how much space they take and frees it up.
- **[Locations](docs/User-Guide.md#locations):** save where a transaction happened and open it in your maps app.
- **[Change the type](docs/User-Guide.md#changing-a-transactions-type)** between income, expense and transfer, keeping the title, category, tags and everything else.
- **[Compact tag picker](docs/User-Guide.md#tag-picker)** that stays usable with many tags.

### Accounts

- **[Descriptions](docs/User-Guide.md#account-descriptions)** for what an account is for or who shares it.
- **[Balance on any day](docs/User-Guide.md#account-balance-on-any-day):** tap a date in an account's history to see its end-of-day balance.

### Planned payments and budgets

- **[Overdue alerts](docs/User-Guide.md#overdue-planned-payment-alerts)** every morning for each planned payment that's past due, until it's paid or skipped.
- **[Planned payments in budgets](docs/User-Guide.md#planned-payments-in-budgets),** shown as a lighter fill, so a budget bar shows what's really free to spend.

### Home and reports

- **[Leave tags out of income and expense](docs/User-Guide.md#tags-left-out-of-income-and-expense),** for transfers between your own accounts or costs you'll be paid back for.
- **[Tidier reports filter](docs/User-Guide.md#reports-filter)** with accounts and categories that wrap instead of scrolling sideways.

### Runs next to Ivy Wallet

The app is called **[Ivy Wallet+](docs/User-Guide.md#ivy-wallet-name-and-logo)** and has its own package name and logo, so it installs alongside the original Ivy Wallet. Move your data across with a backup.

## Install

Download `ivy-wallet-plus-vX.Y.Z.apk` from the [latest release](https://github.com/eyosias-k-negash/ivy-wallet-plus/releases/latest), then open it on your phone to install. It isn't on the Play Store. New releases install over older ones and keep your data.

Each release also has a `.sha256` file to check the download against.

### Build it yourself

You need Java 17+ and the latest stable Android Studio.

```bash
git clone https://github.com/eyosias-k-negash/ivy-wallet-plus.git
cd ivy-wallet-plus
./gradlew assembleDemo
```

The APK is written to `app/build/outputs/apk/demo/app-demo.apk`. Copy it to your phone and open it to install. It's a separate app from a downloaded release, so the two install side by side.

For development, `./gradlew installDebug` installs **Ivy Wallet+ Debug**, which sits next to a regular install. Run `./gradlew testDebugUnitTest` for the unit tests.

## Documentation

| For | Read |
|---|---|
| Using the app | [User guide](docs/User-Guide.md): every Ivy Wallet Plus feature, step by step |
| Setting up a bank's SMS | [Writing SMS patterns](docs/SMS-Patterns.md): pattern names, a full example config, tips |
| Contributing | [Contributing](CONTRIBUTING.md): build, checks, pull request format, privacy rules |
| Finding your way in the code | [Codebase guide](docs/Codebase-Guide.md): where each feature lives, the SMS pipeline, database versions, known issues |
| Code style and architecture | [Developer guidelines](docs/Guidelines.md), from the original Ivy Wallet team |
| Failing CI checks | [CI troubleshooting](docs/CI-Troubleshooting.md) |

## Contributing

Contributions are welcome. Branch off `main`, follow the [pull request template](.github/PULL_REQUEST_TEMPLATE.md), and read [Contributing](CONTRIBUTING.md) first. It explains the checks to run and how to keep personal data out of a public repository.

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

### Community project

The original README lists [ivy-wallet-web](https://github.com/pratikkabade/ivy-wallet-web) by [Pratik Kabade](https://github.com/pratikkabade), which lets you use Ivy Wallet from a web browser. It's developed independently and isn't affiliated with Ivy Apps Ltd or this fork, so use it at your own risk.

## Credits and license

Ivy Wallet was created by Ivy Apps Ltd and [its contributors](https://github.com/Ivy-Apps/ivy-wallet/graphs/contributors). Co-founder Stefan Ilijev created the [Ivy design system](https://www.figma.com/file/kSwIa07jcHEHZXo6rzx7dn/Design-System?node-id=0%3A1&mode=dev). Ivy Wallet Plus builds on their work.

Ivy Wallet Plus is licensed under the [GPL-3.0](LICENSE), like the original. It's provided as is, without warranty of any kind.
