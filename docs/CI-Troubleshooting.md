# Troubleshooting CI failures

Every pull request and every push to `main` runs the checks below on GitHub Actions. If one fails (❌), click **Details** next to it, or find the run under [Actions](https://github.com/eyosias-k-negash/ivy-wallet-plus/actions), and read the log to see why.

| Check | Workflow | Run it locally |
|---|---|---|
| [Detekt](#detekt) | `detekt.yml` | `./gradlew detekt` |
| [Lint](#lint) | `lint.yml` | `./gradlew lintRelease` |
| [Unit tests](#unit-tests) | `test.yml` | `./gradlew testDebugUnitTest` |
| [Composables stability](#compose-stability) | `compose_stability.yml` | See below |
| [Paparazzi screenshot tests](#paparazzi-tests) | `paparazzi_screenshot_test.yml` | `./gradlew verifyPaparazziDebug` |
| [Integration tests](#integration-tests) | `integration_test.yml` | `./scripts/integrationTests.sh`, with a device or emulator connected |
| [APK](#apk) | `apk.yml` | `./gradlew assembleDemo` |
| [CI Actions tests](#ci-actions-tests) | `ci_actions_test.yml` | `./gradlew :ci-actions:base:test :ci-actions:compose-stability:test` |

## Detekt

[Detekt](https://detekt.dev/) is a static code analyzer for Kotlin that enforces readable code and good practices.

```bash
./gradlew detekt
```

> [!IMPORTANT]
> Detekt already fails on `main`, mostly over fork code that isn't in the baseline, so this check fails on every pull request for now. Look in the report for the files you changed, and fix the findings you added. `scripts/detekt.sh` ends in `|| exit 0`, so it reports success even when Detekt fails.

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

The report is written to `build/reports/lint/`. `scripts/lint.sh` runs the same task and opens the report with macOS's `open`.

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

## Compose stability

This check builds the demo APK with Compose compiler reports, then checks that your `@Composable` functions are stable, meaning restartable and skippable. Unstable composables recompose more than they need to, which can drop frames and make animations and scrolling lag.

To fix a failure, open the failing run and read the report. It lists which `@Composable` functions are unstable and which parameters cause it. Then see Android's guides on [stability](https://developer.android.com/jetpack/compose/performance/stability) and [fixing stability issues](https://developer.android.com/jetpack/compose/performance/stability/fix).

To run it locally:

```bash
./gradlew assembleDemo -PcomposeCompilerReports=true
./gradlew :ci-actions:compose-stability:run
```

**Compose stability baseline** (not recommended). Use it only if the failure is in legacy code. The script commits the baseline for you. If it fails, open it and run its commands by hand.

```bash
./scripts/composeStabilityBaseline.sh
```

## Paparazzi tests

[Paparazzi](https://github.com/cashapp/paparazzi) renders `@Composable` previews to images and compares them with the recorded ones, to catch visual changes.

1. Run `./gradlew verifyPaparazziDebug` to find the modules and screens that fail.
2. Open the generated report to see what changed since the images were recorded.
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

## APK

Builds the demo APK and uploads it as the `Ivy-Wallet-Demo.apk` artifact of the run. If it fails, `./gradlew assembleDemo` shows the same compile or resource error locally.

## CI Actions tests

The `ci-actions` modules are small Kotlin programs used by CI, such as the Compose stability check. This runs their unit tests, so it only fails if you changed `ci-actions/`.
