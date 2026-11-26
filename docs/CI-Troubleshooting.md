# Troubleshooting CI failures

Every pull request and every push to `main` runs the checks below on GitHub Actions. They all pass on `main`, so a failure on a pull request comes from its changes. If one fails (❌), click **Details** next to it, or find the run under [Actions](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions), and read the log to see why. Most checks also attach a report to the run, under **Artifacts** on the run's summary page.

| Check | Workflow | Run it locally |
|---|---|---|
| [Build](#build) | `ci.yml` | `./gradlew :app:assembleDebug` |
| [Unit tests](#unit-tests) | `ci.yml` | `./gradlew testDebugUnitTest` |
| [Lint](#lint) | `ci.yml` | `./gradlew lintRelease` |
| [Detekt](#detekt) | `ci.yml` | `./gradlew detekt` |
| [Screenshot tests](#screenshot-tests) | `ci.yml` | `./gradlew verifyPaparazziDebug` |
| [Compose stability](#compose-stability) | `ci.yml` | See below |
| [Integration tests](#integration-tests) | `integration_test.yml` | `./scripts/integrationTests.sh`, with a device or emulator connected |

Pushing a version tag runs a separate workflow, `release.yml`, which builds the signed release. See [Releasing](Codebase-Guide.md#releasing).

## Build

Builds the debug APK and uploads it as the `ivy-wallet-plus-debug-apk` artifact, so you can install a pull request's build without compiling it. It installs as **Ivy Wallet+ Debug**, next to a regular install. If the build fails, `./gradlew :app:assembleDebug` shows the same compile or resource error locally.

## Detekt

[Detekt](https://detekt.dev/) is a static code analyzer for Kotlin that enforces readable code and good practices.

```bash
./gradlew detekt
```

The report is uploaded as the `detekt-report` artifact.

**Suppress a finding** only if you're sure Detekt is wrong. Add `@Suppress("ID_OF_THE_CHECK")` to ignore it in one place:

```kotlin
@Suppress("FunctionMaxLength")
fun veryVeryLongFunction() {
    // ...
}
```

**Detekt baseline** (not recommended). If the findings are in legacy code, you can add them to the baseline:

```bash
./scripts/detektBaseline.sh
```

## Lint

The check runs the [standard Android Lint](https://developer.android.com/studio/write/lint) plus [Slack's compose-lints](https://slackhq.github.io/compose-lints/), which enforce proper Compose usage.

```bash
./gradlew lintRelease
```

Lint itself never fails the build, so the check then fails if the report has any issue of **Error** severity. The report is written to `build/reports/lint/` and uploaded as the `lint-report` artifact. `scripts/lint.sh` runs the same task and opens the report with macOS's `open`.

**Suppress a finding** the same way as for Detekt, with `@Suppress("ID_OF_THE_CHECK")`, and only if you're sure Lint is wrong.

**Lint baseline** (not recommended), for findings in legacy code:

```bash
./scripts/lintBaseline.sh
```

## Unit tests

A failure means your change breaks an existing unit test. Find the failing test and fix your code, or the test if the behavior change is intended.

```bash
./gradlew testDebugUnitTest
```

To run one module or one class, for example the SMS parser tests:

```bash
./gradlew :temp:legacy-code:testDebugUnitTest --tests com.ivy.legacy.sms.SmsParserTest
```

The check also runs the tests of the `ci-actions` modules, the small Kotlin programs CI uses, such as the Compose stability check. They only fail if you changed `ci-actions/`:

```bash
./gradlew :ci-actions:base:test :ci-actions:compose-stability:test
```

The `ci-actions` modules build with a JDK 17 toolchain, which Gradle downloads the first time if you don't have one.

## Compose stability

This check builds the debug APK, which writes Compose compiler reports, then checks that your `@Composable` functions are stable, meaning restartable and skippable. Unstable composables recompose more than they need to, which can drop frames and make animations and scrolling lag.

To fix a failure, open the failing run and read the report. It lists which `@Composable` functions are unstable and which parameters cause it. Then see Android's guides on [stability](https://developer.android.com/jetpack/compose/performance/stability) and [fixing stability issues](https://developer.android.com/jetpack/compose/performance/stability/fix).

To run it locally:

```bash
./gradlew assembleDebug
./gradlew :ci-actions:compose-stability:run
```

**Compose stability baseline** (not recommended). Use it only if the failure is in legacy code. The script commits the baseline for you. If it fails, open it and run its commands by hand.

```bash
./scripts/composeStabilityBaseline.sh
```

## Screenshot tests

[Paparazzi](https://github.com/cashapp/paparazzi) renders `@Composable` previews to images and compares them with the recorded ones, to catch visual changes.

1. Run `./gradlew verifyPaparazziDebug` to find the modules and screens that fail.
2. Open the generated report to see what changed since the images were recorded. On CI, the differences are uploaded as the `paparazzi-failures` artifact.
3. If the change is intended, record new images for that module with `./gradlew :<module>:recordPaparazziDebug`.
4. Run `./gradlew :<module>:verifyPaparazziDebug` again to check that it passes.
5. Repeat for every module that failed.

> [!NOTE]
> Use static data only. Dynamic data, such as the current date or time, makes the images differ on every run.

## Integration tests

These run the `androidTest` tests of `shared:data:core` and `shared:domain` on an API 34 emulator. Locally, connect a device or start an emulator, then:

```bash
./gradlew :shared:data:core:connectedDebugAndroidTest
./gradlew :shared:domain:connectedDebugAndroidTest
```
