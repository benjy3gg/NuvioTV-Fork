package com.nuvio.tv.core.deeplink

import com.nuvio.tv.domain.deeplink.AppDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    @Test
    fun parsesMetaQueryDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://meta?type=series&id=tt0944947")
        )
    }

    @Test
    fun parsesAddonInstallDeepLink() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            DeepLinkParser.parse("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json")
        )
    }

    @Test
    fun parsesStremioAddonInstallDeepLink() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            DeepLinkParser.parse("stremio://free.nebulapro.xyz/sports/i/free/manifest.json")
        )
    }

    @Test
    fun parsesDirectImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://series/tt0944947")
        )
    }

    @Test
    fun parsesProviderImdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            DeepLinkParser.parse("nuvio://imdb/series/tt0944947")
        )
    }

    @Test
    fun parsesProviderTmdbDetailDeepLink() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tmdb:1399"),
            DeepLinkParser.parse("nuvio://tmdb/tv/1399")
        )
    }

    @Test
    fun doesNotTreatAuthLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("nuvio://auth/trakt?code=abc"))
    }

    @Test
    fun doesNotTreatNonHostStremioLinkAsAddonInstall() {
        assertNull(DeepLinkParser.parse("stremio://detail/series/tt0944947"))
    }

    @Test
    fun parsesNuvioPlayLinkWithEncodedUrlAndTitle() {
        assertEquals(
            AppDeepLink.Play(url = "https://host/a b.mkv?token=1&x=2", title = "FEL test"),
            DeepLinkParser.parse("nuvio://play?url=https%3A%2F%2Fhost%2Fa%20b.mkv%3Ftoken%3D1%26x%3D2&title=FEL%20test")
        )
    }

    @Test
    fun nuvioPlayLinkTurnsBarePathIntoFileUrl() {
        assertEquals(
            AppDeepLink.Play(url = "file:///sdcard/Android/data/app/cache/fel.mkv", title = "fel.mkv"),
            DeepLinkParser.parse("nuvio://play?url=/sdcard/Android/data/app/cache/fel.mkv")
        )
    }

    @Test
    fun parsesDirectViewUrlsWithFileNameAsTitle() {
        assertEquals(
            AppDeepLink.Play(url = "https://host/dir/clip.mkv?sig=abc", title = "clip.mkv"),
            DeepLinkParser.parse("https://host/dir/clip.mkv?sig=abc")
        )
        assertEquals(
            AppDeepLink.Play(url = "file:///sdcard/My Clip.mp4", title = "My Clip.mp4"),
            DeepLinkParser.parse("file:///sdcard/My Clip.mp4")
        )
        assertEquals(
            AppDeepLink.Play(url = "content://com.nuvio.tv.test.fileprovider/cache/fel.mp4", title = "fel.mp4"),
            DeepLinkParser.parse("content://com.nuvio.tv.test.fileprovider/cache/fel.mp4")
        )
    }

    @Test
    fun rejectsPlayLinkWithoutUsableUrl() {
        assertNull(DeepLinkParser.parse("nuvio://play"))
        assertNull(DeepLinkParser.parse("nuvio://play?url=javascript%3Aalert(1)"))
    }
}
