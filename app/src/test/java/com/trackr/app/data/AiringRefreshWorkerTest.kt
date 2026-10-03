package com.trackr.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.trackr.app.data.airing.AiringRefreshWorker
import com.trackr.app.data.repository.AiringRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AiringRefreshWorkerTest {
    private val repo = mockk<AiringRepository>(relaxed = true)

    private fun run(attempt: Int = 0): ListenableWorker.Result {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<AiringRefreshWorker>(ctx)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(c: Context, name: String, p: WorkerParameters) = AiringRefreshWorker(c, p, repo)
            }).build()
        return runBlocking { worker.doWork() }
    }

    @Test fun `worker calls refresh and succeeds`() {
        assertEquals(ListenableWorker.Result.success(), run())
        coVerify(exactly = 1) { repo.refresh(any()) }
    }

    @Test fun `worker retries on exception until 5 attempts`() {
        coEvery { repo.refresh(any()) } throws IOException("boom")
        assertEquals(ListenableWorker.Result.retry(), run(attempt = 0))
        assertEquals(ListenableWorker.Result.retry(), run(attempt = 4))
        assertEquals(ListenableWorker.Result.failure(), run(attempt = 5))
    }
}
