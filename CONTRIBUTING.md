# Contributing to Ivy Wallet Plus

Ivy Wallet Plus is a fork of [Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet). The original project is no longer maintained, so contributions go to this repository.

New to the code? Read the [codebase guide](docs/Codebase-Guide.md) first. It maps every Ivy Wallet Plus feature to the files that implement it.

GitHub Issues are turned off on this repository. For questions, bug reports or ideas, get in touch through [eyosias-k-negash.github.io](https://eyosias-k-negash.github.io).

## 1. Fork and clone

[Fork eyosias-k-negash/ivy-wallet-plus](https://github.com/eyosias-k-negash/ivy-wallet-plus/fork) on GitHub, then clone your fork:

```bash
git clone https://github.com/<your-username>/ivy-wallet-plus.git
cd ivy-wallet-plus
```

You need Java 17+ and the latest stable Android Studio.

## 2. Create a branch

Branch off `main` and name the branch after your change:

```bash
git checkout -b fix-sms-decimal-comma
```

## 3. Make your change

Read the [developer guidelines](docs/Guidelines.md) before you start. In short: keep it simple, and don't break existing features.

Build and install the debug app with `./gradlew installDebug`, or build the APK with `./gradlew assembleDebug` (written to `app/build/outputs/apk/debug/`). The debug app is called **Ivy Wallet+ Debug**, has a grey icon and installs next to a regular build, so you can test without touching your real data. Try your change on a device or emulator before you open a pull request.

### Keep the docs in step

If your change affects what people see in the app, update the [user guide](docs/User-Guide.md) in the same pull request. A new feature also gets a line in the README's **Highlights**. Changes to how SMS patterns are read go in [Writing SMS patterns](docs/SMS-Patterns.md).

### Database changes

Room exports a schema file for every database version, and the app upgrades with auto-migrations. To add a column:

1. Add the field to the entity in `shared/data/core/.../db/entity/` with a default, both in Kotlin and as `@ColumnInfo(defaultValue = …)`. Backups are the serialized entities, so the Kotlin default is what lets an older backup without the field still import.
2. Bump `version` in `IvyRoomDatabase` and add `AutoMigration(from = N, to = N + 1)`.
3. Build once. Room writes `shared/data/core/schemas/com.ivy.data.db.IvyRoomDatabase/<N + 1>.json`. Commit it. Never edit a schema file that's already committed.
4. Carry the field through the domain model, the mapper and its test, and any legacy model that uses it.
5. Install your build over a build of `main` and check that the app opens with existing data.

The [codebase guide](docs/Codebase-Guide.md#database) lists what each fork version added.

## 4. Run the checks

The CI workflows run these on every pull request. Run the ones that cover your change before you push:

| Check | Command |
|---|---|
| Unit tests | `./gradlew testDebugUnitTest` |
| Detekt | `./gradlew detekt` |
| Android Lint | `./gradlew lintRelease` |
| Paparazzi screenshot tests | `./gradlew verifyPaparazziDebug` |

To run one module's tests, prefix the task with the module, for example `./gradlew :temp:legacy-code:testDebugUnitTest`.

> [!IMPORTANT]
> Detekt already fails on `main`, mostly over fork code that isn't in the baseline. Don't let that hide new findings: search the report for the files you changed, and fix what you added. `scripts/detekt.sh` and `scripts/unitTests.sh` end in `|| exit 0`, so they always report success. Use the Gradle commands above instead.

CI also builds the demo APK, checks Compose stability, tests the `ci-actions` modules and runs integration tests on an emulator. [CI troubleshooting](docs/CI-Troubleshooting.md) explains each check and how to fix a failure.

## 5. Keep personal data out

This repository is public, and so are its commits, pull requests and their edit history. Don't put personal data anywhere in it: code, tests, commit messages, pull request descriptions, screenshots or screen recordings.

- **No real SMS or banking data.** Don't use real SMS text, bank, wallet or contact names, account numbers, amounts, balances or currencies. Use made-up examples instead: "Example Bank", "Corner Shop", USD, an account like `1****89`, and phone numbers from the reserved `+1 555 0100` range.
- **No device details.** Write "tested on a physical device in the debug build". Leave out the phone model, Android version, installed apps, free storage and logs from your phone.
- **Screenshots and recordings** should use test data, or have names and numbers blurred.

If something personal slips into a pull request, edit it out and also delete the old revision from the description's edit history on GitHub.

## 6. Open a pull request

Push your branch to your fork and open a pull request against `main` on [eyosias-k-negash/ivy-wallet-plus](https://github.com/eyosias-k-negash/ivy-wallet-plus/pulls). Because this repository is itself a fork, GitHub may preselect `Ivy-Apps/ivy-wallet` as the base repository. Change it to `eyosias-k-negash/ivy-wallet-plus`.

**Title:** say what changes, in the imperative and sentence case, without a full stop. For example: "Show planned payments as a lighter fill on budget bars" or "Stop the audio note crashing on phones without a recorder app". Commit messages follow the same style.

**Description:** the [pull request template](.github/PULL_REQUEST_TEMPLATE.md) fills in these sections:

| Section | What goes in it |
|---|---|
| **Summary** | What changes for the person using the app, and why. Name buttons and screens in bold. |
| **Changes** | How it works, one bullet per area, led by the class or topic in bold. Mention database versions, new permissions and doc updates. |
| **Testing** | A checklist: ticked for what you ran or tried, unticked for what's still open. |
| **Screenshots** | Optional. For UI changes, a screenshot or short screen recording. |
| **Not in this PR** | Optional. Known limitations and follow-ups. |

Merged pull requests in this repository, such as [#25](https://github.com/eyosias-k-negash/ivy-wallet-plus/pull/25), show the format.

## Code of conduct

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md) when taking part in this project.
