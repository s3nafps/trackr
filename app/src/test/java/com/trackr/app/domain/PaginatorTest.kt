package com.trackr.app.domain

import com.trackr.app.domain.model.MediaItem
import com.trackr.app.domain.model.MediaPage
import com.trackr.app.domain.model.MediaSource
import com.trackr.app.domain.model.MediaType
import com.trackr.app.domain.util.PageState
import com.trackr.app.domain.util.Paginator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PaginatorTest {
    private fun item(id: Int) = MediaItem(MediaSource.TMDB, "$id", MediaType.MOVIE, "Title $id", null)
    private fun page(vararg ids: Int, hasMore: Boolean = true) = MediaPage(ids.map(::item), hasMore)
    private fun ids(s: PageState) = s.items.map { it.externalId.toInt() }

    private fun TestScope.paginator(fetch: suspend (Int, Boolean) -> MediaPage) =
        Paginator(TestScope(UnconfinedTestDispatcher(testScheduler)), { it.message ?: "error" }, fetch)

    @Test fun `appends pages in order and stops at the last one`() = runTest {
        val requested = mutableListOf<Int>()
        val p = paginator { n, _ -> requested += n; if (n < 3) page(n * 10, n * 10 + 1) else page(30, hasMore = false) }
        assertTrue(p.state.value.firstLoad)
        repeat(5) { p.loadMore() }
        assertEquals(listOf(1, 2, 3), requested)
        assertEquals(listOf(10, 11, 20, 21, 30), ids(p.state.value))
        assertTrue(p.state.value.endReached)
        assertEquals(3, p.state.value.pages)
    }

    @Test fun `titles already shown are skipped when lists shift between pages`() = runTest {
        val p = paginator { n, _ -> if (n == 1) page(1, 2, 3) else page(3, 4, 2, 5) }
        p.loadMore(); p.loadMore()
        assertEquals(listOf(1, 2, 3, 4, 5), ids(p.state.value))
    }

    @Test fun `only one page loads at a time`() = runTest {
        val gate = CompletableDeferred<MediaPage>()
        var calls = 0
        val p = paginator { _, _ -> calls++; gate.await() }
        p.loadMore(); p.loadMore(); p.loadMore()
        assertEquals(1, calls)
        assertTrue(p.state.value.loading)
        gate.complete(page(1))
        runCurrent()
        assertFalse(p.state.value.loading)
        assertEquals(listOf(1), ids(p.state.value))
    }

    @Test fun `a failed page stops loading until retried, keeping what was shown`() = runTest {
        var fail = false
        val p = paginator { n, _ -> if (fail) throw IOException("offline") else page(n) }
        p.loadMore()
        fail = true
        p.loadMore()
        assertEquals("offline", p.state.value.error)
        assertEquals(listOf(1), ids(p.state.value))
        p.loadMore() // ignored while the error is showing
        assertEquals("offline", p.state.value.error)
        fail = false
        p.retry()
        assertNull(p.state.value.error)
        assertEquals(listOf(1, 2), ids(p.state.value))
    }

    @Test fun `reset starts over with a new source and ignores the old request`() = runTest {
        val slow = CompletableDeferred<MediaPage>()
        val p = paginator { _, _ -> slow.await() }
        p.loadMore()
        p.reset { n, _ -> page(100 + n) }
        slow.complete(page(1, 2))
        runCurrent()
        assertEquals(listOf(101), ids(p.state.value))
        assertEquals(1, p.state.value.pages)
    }

    @Test fun `refresh keeps titles on screen until page 1 replaces them, and forces every page`() = runTest {
        val gate = CompletableDeferred<MediaPage>()
        val forced = mutableListOf<Boolean>()
        var refreshed = false
        val p = paginator { n, force -> forced += force; if (refreshed && n == 1) gate.await() else page(n) }
        p.loadMore(); p.loadMore()
        refreshed = true
        p.reset(force = true, keepItems = true)
        assertTrue(p.state.value.refreshing)
        assertEquals(listOf(1, 2), ids(p.state.value))
        gate.complete(page(7, 1))
        runCurrent()
        assertEquals(listOf(7, 1), ids(p.state.value))
        assertFalse(p.state.value.refreshing)
        p.loadMore()
        assertEquals(listOf(false, false, true, true), forced)
    }

    @Test fun `clear empties the list without loading`() = runTest {
        var calls = 0
        val p = paginator { n, _ -> calls++; page(n) }
        p.loadMore()
        p.clear()
        assertEquals(PageState.of(emptyList()), p.state.value)
        p.loadMore()
        assertEquals(1, calls)
    }
}
