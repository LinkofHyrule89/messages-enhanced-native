package com.ubermicrostudios.messagesenhanced.nativeapp

import com.ubermicrostudios.messagesenhanced.nativeapp.update.ReleaseParser
import com.ubermicrostudios.messagesenhanced.nativeapp.update.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    @Test
    fun semverOrdersMajorMinorPatchAndAcceptsALeadingV() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("V1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("  v1.2.3  "))
        assertNull(SemVer.parse("1.2"))
        assertNull(SemVer.parse("v1.2.3-beta"))
        assertNull(SemVer.parse("v1.2.3.4"))
        assertNull(SemVer.parse(""))

        assertTrue(SemVer(1, 2, 4) > SemVer(1, 2, 3))
        assertTrue(SemVer(1, 3, 0) > SemVer(1, 2, 9))
        assertTrue(SemVer(2, 0, 0) > SemVer(1, 9, 9))
        assertEquals(0, SemVer(0, 6, 0).compareTo(SemVer(0, 6, 0)))
        assertTrue(SemVer.isNewer("v0.6.1", "0.6.0"))
        assertFalse(SemVer.isNewer("v0.6.0", "0.6.0"))
        assertFalse(SemVer.isNewer("v0.5.9", "0.6.0"))
        assertFalse(SemVer.isNewer("not-a-version", "0.6.0"))
    }

    @Test
    fun releaseJsonReadsTagNotesAndTheApkAsset() {
        val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val json = """
            {
              "tag_name": "v1.2.3",
              "name": "ignored title",
              "body": "Fixes\n\n- one",
              "assets": [
                {
                  "name": "notes.txt",
                  "browser_download_url": "https://example.test/notes.txt",
                  "size": 12
                },
                {
                  "name": "messages-enhanced-native-v1.2.3.apk",
                  "browser_download_url": "https://example.test/app.apk",
                  "size": 4096,
                  "digest": "sha256:$sha",
                  "extra": true
                },
                {
                  "name": "other.apk",
                  "browser_download_url": "https://example.test/other.apk",
                  "size": 1
                }
              ]
            }
        """.trimIndent()
        val parsed = ReleaseParser.parse(json)
        assertEquals(SemVer(1, 2, 3), parsed?.version)
        assertEquals("v1.2.3", parsed?.tag)
        assertEquals("Fixes\n\n- one", parsed?.notes)
        val apk = parsed?.apk
        assertEquals("messages-enhanced-native-v1.2.3.apk", apk?.name)
        assertEquals("https://example.test/app.apk", apk?.downloadUrl)
        assertEquals(4096L, apk?.size)
        assertEquals(sha, apk?.sha256)
        assertEquals(false, apk?.digestInvalid)
    }

    @Test
    fun releaseJsonTreatsAMissingOrBadDigestAndAMissingApk() {
        val noDigest = """
            {"tag_name":"v2.0.0","body":"","assets":[
              {"name":"App.APK","browser_download_url":"https://example.test/a.apk","size":9}
            ]}
        """.trimIndent()
        val plain = ReleaseParser.parse(noDigest)
        assertEquals(SemVer(2, 0, 0), plain?.version)
        assertNull(plain?.apk?.sha256)
        assertEquals(false, plain?.apk?.digestInvalid)
        assertEquals("App.APK", plain?.apk?.name)

        val bad = """
            {"tag_name":"v2.0.1","body":"n","assets":[
              {"name":"a.apk","browser_download_url":"https://example.test/a.apk","size":9,"digest":"md5:abcd"}
            ]}
        """.trimIndent()
        val invalid = ReleaseParser.parse(bad)?.apk
        assertNull(invalid?.sha256)
        assertEquals(true, invalid?.digestInvalid)
        assertNull(ReleaseParser.sha256Hex("sha256:abcd"))
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ReleaseParser.sha256Hex("SHA256:0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"),
        )

        val noApk = """{"tag_name":"v3.0.0","body":"notes","assets":[{"name":"readme.txt","size":1}]}"""
        val parsed = ReleaseParser.parse(noApk)
        assertEquals(SemVer(3, 0, 0), parsed?.version)
        assertEquals("notes", parsed?.notes)
        assertNull(parsed?.apk)

        assertNull(ReleaseParser.parse("""{"tag_name":"nightly","assets":[]}"""))
        assertNull(ReleaseParser.parse("not json"))
    }
}
