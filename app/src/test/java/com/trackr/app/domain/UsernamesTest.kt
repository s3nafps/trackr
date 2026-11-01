package com.trackr.app.domain

import com.trackr.app.domain.util.Usernames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernamesTest {
    @Test fun `the box keeps only what the server accepts`() {
        assertEquals("hani_atto", Usernames.clean("hani atto"))
        assertEquals("hani_", Usernames.clean("hani هاني"))
        assertEquals("Jos._1", Usernames.clean("Jos.é_1!"))
        assertEquals(Usernames.MAX, Usernames.clean("a".repeat(40)).length)
    }

    @Test fun `whatever the box keeps is valid once long enough`() {
        listOf("hani atto", "Zoë_99", "a.b.c", "x".repeat(30)).forEach { assertTrue(it, Usernames.isValid(Usernames.clean(it))) }
        assertFalse(Usernames.isValid(Usernames.clean("هاني")))
        assertFalse(Usernames.isValid("ab"))
        assertFalse(Usernames.isValid("héllo"))
    }
}
