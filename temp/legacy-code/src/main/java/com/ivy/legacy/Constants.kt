package com.ivy.legacy

object Constants {
    const val URL_TC =
        "https://github.com/ILIYANGERMANOV/privacy-policies/blob/master/ivy-wallet-tc.md"
    const val URL_PRIVACY_POLICY =
        "https://github.com/ILIYANGERMANOV/privacy-policies/blob/master/ivy-wallet-privacy-policy.md"

    const val URL_IVY_WALLET_REPO = "https://github.com/eyosias-k-negash/ivy-wallet-plus"

    const val URL_LATEST_RELEASE = "$URL_IVY_WALLET_REPO/releases/latest"

    const val URL_USER_GUIDE = "$URL_IVY_WALLET_REPO/blob/main/docs/User-Guide.md"

    // GitHub Issues are turned off on the repository, so bug reports and ideas go here.
    const val URL_CONTACT = "https://eyosias-k-negash.github.io"

    // The original Ivy Wallet community.
    const val URL_IVY_TELEGRAM_INVITE = "https://t.me/+ETavgioAvWg4NThk"

    const val CATEGORY_UNSPECIFIED_NAME = "Unspecified"

    const val URL_IVY_CONTRIBUTORS =
        "https://github.com/Ivy-Apps/ivy-wallet#contributors-wall"

    const val URL_GITHUB_SPONSORS =
        "https://github.com/sponsors/Ivy-Apps"

    const val DEFAULT_SMS_REGEX = "(?i)(?:amount|debited|spent|paid|rs\\.?|inr|usd|€|£)\\s*[:\\-\\s]*([0-9,.]+).*?(?:on|at|date)\\s*[:\\-\\s]*([0-9/.\\-]+)\\s*([0-9:]+)"
}
