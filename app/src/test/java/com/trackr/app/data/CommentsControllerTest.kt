package com.trackr.app.data

import com.trackr.app.data.repository.SocialException
import com.trackr.app.data.repository.SocialRepository
import com.trackr.app.domain.model.Comment
import com.trackr.app.domain.model.Load
import com.trackr.app.ui.screens.social.CommentsController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsControllerTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val social = mockk<SocialRepository>()
    private val counts = mutableListOf<Pair<String, Int>>()
    private val ctl = CommentsController(scope, social) { id, delta -> counts += id to delta }

    private fun comment(id: String) = Comment(id, "u", "ann", null, "hi $id", 0, canDelete = true)

    @Test fun `opening loads the entry's comments`() {
        coEvery { social.comments("e1", "owner") } returns listOf(comment("c1"))
        ctl.open("e1", "Heat", "owner")
        assertEquals(Load.Success(listOf(comment("c1"))), ctl.state.value?.comments)
        assertEquals("Heat", ctl.state.value?.title)
    }

    @Test fun `posting adds a comment, reports +1, reloads and bumps posted so the draft clears`() {
        coEvery { social.comments("e1", null) } returnsMany listOf(emptyList(), listOf(comment("c1")))
        coEvery { social.addComment("e1", "nice") } returns Unit
        ctl.open("e1", "Heat", null)
        ctl.post("nice")
        coVerify { social.addComment("e1", "nice") }
        assertEquals(listOf("e1" to 1), counts)
        assertEquals(1, ctl.state.value?.posted)
        assertEquals(Load.Success(listOf(comment("c1"))), ctl.state.value?.comments)
    }

    @Test fun `a failed post keeps the sheet open with the reason`() {
        coEvery { social.comments(any(), any()) } returns emptyList()
        coEvery { social.addComment(any(), any()) } throws SocialException("Comments can be up to 500 characters.")
        ctl.open("e1", "Heat", null)
        ctl.post("x".repeat(600))
        assertEquals("Comments can be up to 500 characters.", ctl.state.value?.error)
        assertEquals(0, ctl.state.value?.posted)
        assertEquals(emptyList<Pair<String, Int>>(), counts)
    }

    @Test fun `deleting removes the comment and reports -1`() {
        coEvery { social.comments("e1", null) } returns listOf(comment("c1"), comment("c2"))
        coEvery { social.deleteComment("c1") } returns Unit
        ctl.open("e1", "Heat", null)
        ctl.delete(comment("c1"))
        assertEquals(Load.Success(listOf(comment("c2"))), ctl.state.value?.comments)
        assertEquals(listOf("e1" to -1), counts)
    }

    @Test fun `a late result for a previous entry leaves the current sheet alone`() {
        val slow = CompletableDeferred<List<Comment>>()
        coEvery { social.comments("e1", null) } coAnswers { slow.await() }
        coEvery { social.comments("e2", null) } returns listOf(comment("c9"))
        ctl.open("e1", "Heat", null)
        ctl.open("e2", "Alien", null)
        slow.complete(listOf(comment("c1")))
        assertEquals("e2", ctl.state.value?.entryId)
        assertEquals(Load.Success(listOf(comment("c9"))), ctl.state.value?.comments)
        ctl.close()
        assertNull(ctl.state.value)
    }
}
