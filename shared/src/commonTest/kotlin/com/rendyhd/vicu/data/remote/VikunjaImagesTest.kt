package com.rendyhd.vicu.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VikunjaImagesTest {

    @Test
    fun `a placeholder URL moves to the server and keeps its path and query`() {
        assertEquals(
            "https://tasks.example.com/api/v2/avatar/sam?size=64",
            VikunjaImages.rewrite("http://localhost/api/v2/avatar/sam?size=64", "https://tasks.example.com"),
        )
    }

    @Test
    fun `a server in a sub-path keeps its path in front`() {
        assertEquals(
            "http://192.168.1.5:3456/vikunja/api/v2/tasks/1/attachments/2",
            VikunjaImages.rewrite("http://localhost/api/v2/tasks/1/attachments/2", "http://192.168.1.5:3456/vikunja/"),
        )
    }

    @Test
    fun `other hosts are left alone`() {
        assertNull(VikunjaImages.rewrite("https://example.com/image.png", "https://tasks.example.com"))
        assertNull(VikunjaImages.rewrite("https://localhost.evil.com/x.png", "https://tasks.example.com"))
    }

    @Test
    fun `no server configured means no rewrite`() {
        assertNull(VikunjaImages.rewrite("http://localhost/api/v2/avatar/sam", ""))
    }
}
