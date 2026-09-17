package com.infinitezerone.minibgm.sync.work.workers

import android.content.ContextWrapper
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.google.common.util.concurrent.ListenableFuture
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.util.SyncCompletionObserver
import com.infinitezerone.minibgm.core.testing.repository.FakeCollectionRepository
import com.infinitezerone.minibgm.core.testing.repository.FakeScheduleRepository
import com.infinitezerone.minibgm.core.testing.util.FakeTokenProvider
import com.infinitezerone.minibgm.core.testing.util.testBgmDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Constructor
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class BgmSyncWorkerTest {
    private lateinit var scheduleRepository: FakeScheduleRepository
    private lateinit var collectionRepository: FakeCollectionRepository
    private lateinit var tokenProvider: FakeTokenProvider
    private lateinit var syncCompletionObserver: FakeSyncCompletionObserver
    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = testBgmDispatchers(testDispatcher)

    @Before
    fun setUp() {
        scheduleRepository = FakeScheduleRepository()
        collectionRepository = FakeCollectionRepository()
        tokenProvider = FakeTokenProvider(42L)
        syncCompletionObserver = FakeSyncCompletionObserver()
    }

    private class FakeSyncCompletionObserver : SyncCompletionObserver {
        var callCount = 0

        override suspend fun onSyncSucceeded() {
            callCount++
        }
    }

    private fun createWorker(runAttemptCount: Int = 0): BgmSyncWorker {
        val constructor: Constructor<*> = WorkerParameters::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        val types = constructor.parameterTypes
        val args = arrayOfNulls<Any>(types.size)
        for (i in types.indices) {
            args[i] =
                when (types[i]) {
                    UUID::class.java -> UUID.randomUUID()
                    Data::class.java -> Data.EMPTY
                    Collection::class.java, Set::class.java, List::class.java -> emptySet<String>()
                    WorkerParameters.RuntimeExtras::class.java -> WorkerParameters.RuntimeExtras()
                    Int::class.javaPrimitiveType -> if (i == 4) runAttemptCount else 0
                    Executor::class.java -> Executors.newSingleThreadExecutor()
                    CoroutineContext::class.java -> Dispatchers.Unconfined
                    TaskExecutor::class.java -> createTaskExecutor()
                    WorkerFactory::class.java -> createWorkerFactory()
                    ProgressUpdater::class.java -> ProgressUpdater { _, _, _ -> createImmediateFuture() }
                    ForegroundUpdater::class.java -> ForegroundUpdater { _, _, _ -> createImmediateFuture() }
                    else -> null
                }
        }
        val params = constructor.newInstance(*args) as WorkerParameters

        return BgmSyncWorker(
            appContext = ContextWrapper(null),
            workerParams = params,
            scheduleRepository = scheduleRepository,
            collectionRepository = collectionRepository,
            tokenProvider = tokenProvider,
            dispatchers = testDispatchers,
            syncCompletionObserver = syncCompletionObserver,
        )
    }

    private fun createWorkerFactory(): WorkerFactory =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: android.content.Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = null
        }

    private fun createTaskExecutor(): TaskExecutor =
        object : TaskExecutor {
            private val directExecutor = Executor { it.run() }

            override fun getMainThreadExecutor(): Executor = directExecutor

            override fun getSerialTaskExecutor(): SerialExecutor =
                object : SerialExecutor {
                    override fun execute(command: Runnable) = command.run()

                    override fun hasPendingTasks(): Boolean = false
                }
        }

    private fun <T> createImmediateFuture(): ListenableFuture<T> =
        object : ListenableFuture<T> {
            override fun addListener(
                listener: Runnable,
                executor: Executor,
            ) = executor.execute(listener)

            override fun cancel(mayInterruptIfRunning: Boolean) = false

            override fun isCancelled() = false

            override fun isDone() = true

            override fun get(): T? = null

            override fun get(
                timeout: Long,
                unit: TimeUnit,
            ): T? = null
        }

    @Test
    fun doWork_returnsSuccess_whenRepositorySucceeds() =
        runTest(testDispatcher) {
            scheduleRepository.syncBangumiDataResult = AppResult.Success(Unit)
            val worker = createWorker()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, scheduleRepository.syncBangumiDataCallCount)
            // 同步成功后必须通知下游（小组件等）自行刷新
            assertEquals(1, syncCompletionObserver.callCount)
        }

    @Test
    fun doWork_returnsRetry_whenRepositoryFails() =
        runTest(testDispatcher) {
            scheduleRepository.syncBangumiDataResult = AppResult.Error(IllegalStateException("Network failure"))
            val worker = createWorker()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            assertEquals(1, scheduleRepository.syncBangumiDataCallCount)
            // 失败路径不产生新数据，不应通知下游刷新
            assertEquals(0, syncCompletionObserver.callCount)
        }

    @Test
    fun doWork_syncsWatchingCollections_whenUserLoggedIn() =
        runTest(testDispatcher) {
            scheduleRepository.syncBangumiDataResult = AppResult.Success(Unit)
            tokenProvider.uidFlow.value = 42L
            val worker = createWorker()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, collectionRepository.syncWatchingCallCount)
        }
}
