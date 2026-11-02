package com.nuvio.tv.domain.deeplink

sealed interface AppDeepLink {
    data class Meta(
        val type: String,
        val id: String
    ) : AppDeepLink

    data class AddonInstall(
        val manifestUrl: String
    ) : AppDeepLink

    data object ScoutAsk : AppDeepLink

    /** Play a media URL directly (http(s), file or content), e.g. from `adb shell am start`. */
    data class Play(
        val url: String,
        val title: String
    ) : AppDeepLink
}
