package com.trackr.app.domain

import com.trackr.app.domain.util.AiredProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiredProgressTest {
    @Test fun `the lower of the episode total and the episodes out caps progress`() {
        assertEquals(40, AiredProgress.ceiling(total = 50, aired = 40)) // announced episodes aren't watchable yet
        assertEquals(12, AiredProgress.ceiling(total = 12, aired = 20))
        assertEquals(40, AiredProgress.ceiling(total = null, aired = 40))
        assertEquals(12, AiredProgress.ceiling(total = 12, aired = null))
        assertNull(AiredProgress.ceiling(total = null, aired = null))
        assertEquals(0, AiredProgress.ceiling(total = 12, aired = 0)) // not released yet: nothing to mark
    }
}
