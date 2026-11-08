# Contributing to Ivy Wallet Plus

Ivy Wallet Plus is a fork of [Ivy-Apps/ivy-wallet](https://github.com/Ivy-Apps/ivy-wallet). The original project is no longer maintained, so contributions go to this repository.

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
git checkout -b fix-transaction-date-picker
```

## 3. Make your change

Read the [Developer Guidelines](docs/Guidelines.md) before you start. In short: keep it simple, and don't break existing features.

Build the app with `./gradlew assembleDemo`. The APK is written to `app/build/outputs/apk/demo/app-demo.apk`. Try your change on a device or emulator before you open a pull request.

## 4. Run the checks

CI runs these on every pull request. Run them locally before you push:

| Check | Command |
|---|---|
| Unit tests | `./gradlew testDebugUnitTest` |
| Detekt | `./gradlew detekt` |
| Android Lint | `./gradlew lintRelease` |
| Paparazzi screenshot tests | `./gradlew verifyPaparazziDebug` |

CI also builds the demo APK, checks Compose stability, tests the `ci-actions` modules and runs integration tests on an emulator. [CI troubleshooting](docs/CI-Troubleshooting.md) explains each check and how to fix a failure.

## 5. Open a pull request

Push your branch to your fork and open a pull request against `main` on [eyosias-k-negash/ivy-wallet-plus](https://github.com/eyosias-k-negash/ivy-wallet-plus/pulls). Because this repository is itself a fork, GitHub may preselect `Ivy-Apps/ivy-wallet` as the base repository. Change it to `eyosias-k-negash/ivy-wallet-plus`.

In the description, say what changed and how you tested it. For UI changes, a screenshot or short screen recording helps.

## Code of conduct

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md) when taking part in this project.
