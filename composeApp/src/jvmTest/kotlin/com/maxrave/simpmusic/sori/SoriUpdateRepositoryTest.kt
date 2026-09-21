package com.maxrave.simpmusic.sori

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoriUpdateRepositoryTest {
    @Test
    fun parsesGithubLatestRelease() {
        val json =
            """{"tag_name":"v2.1.0-sori.2","published_at":"2026-09-21T12:00:00Z","body":"notes","assets":[{"name":"Sori-2.1.2.msi"}]}"""

        val data = parseLatestRelease(json)

        assertEquals("v2.1.0-sori.2", data.tagName)
        assertEquals("2026-09-21T12:00:00Z", data.releaseTime)
        assertEquals("notes", data.body)
    }

    @Test
    fun toleratesNullFields() {
        val data = parseLatestRelease("""{"tag_name":"v1","published_at":null,"body":null}""")

        assertEquals("v1", data.tagName)
        assertNull(data.releaseTime)
        assertEquals("", data.body)
    }
}
