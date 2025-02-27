package me.champeau.jmh

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Threads(1)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
open class StateSynchronizationBenchmark {

    @Benchmark
    fun synchronizedTest(bh: Blackhole) = runBlocking(Dispatchers.Default) {
        var i = 0L
        val lock = Any()
        massiveRun {
            synchronized(lock) {
                i++
            }
        }
        require(i == 1000 * 10_000L)
        bh.consume(i)
    }

    @Benchmark
    fun mutexTest(bh: Blackhole, m: MutexWrapper) = runBlocking(Dispatchers.Default) {
        var i = 0L
        massiveRun {
            m.mutex.withLock {
                i++
            }
        }
        require(i == 1000 * 10_000L)
        bh.consume(i)
    }

    @Benchmark
    fun limitedDispatcherTest(bh: Blackhole, d: SingleThreadDispatcher) = runBlocking(d.dispather) {
        var i = 0L
        massiveRun {
            i++
        }
        require(i == 1000 * 10_000L)
        bh.consume(i)
    }

    @Benchmark
    fun limitedDispatcherSwitchingTest(bh: Blackhole, d: SingleThreadDispatcher) = runBlocking(Dispatchers.Default) {
        var i = 0L
        massiveRun {
            withContext(d.dispather) {
                i++
            }
        }
        require(i == 1000 * 10_000L)
        bh.consume(i)
    }


    @Benchmark
    fun atomicTest(bh: Blackhole, m: MutexWrapper) = runBlocking(Dispatchers.Default) {
        val i = AtomicLong()
        massiveRun {
            i.incrementAndGet()
        }
        require(i.get() == 1000 * 10_000L)
        bh.consume(i.get())
    }

    @Benchmark
    fun mutableListSynchronizedTest(bh: Blackhole) = runBlocking(Dispatchers.Default) {
        val list = mutableListOf<Int>()
        massiveRun {
            synchronized(this@StateSynchronizationBenchmark) {
                list.add(it)
            }
        }
        require(list.size == 1000 * 10_000)
        bh.consume(list.toList())
    }

    @Benchmark
    fun mutableListLimitedDispatcherTest(bh: Blackhole, d: SingleThreadDispatcher) = runBlocking(d.dispather) {
        val list = mutableListOf<Int>()
        massiveRun {
            list.add(it)
        }
        require(list.size == 1000 * 10_000)
        bh.consume(list.toList())
    }

    @Benchmark
    fun mutableListLimitedDispatcherSwitchingTest(bh: Blackhole, d: SingleThreadDispatcher) =
        runBlocking(Dispatchers.Default) {
            val list = mutableListOf<Int>()
            massiveRun {
                withContext(d.dispather) {
                    list.add(it)
                }
            }
            require(list.size == 1000 * 10_000)
            bh.consume(list.toList())
        }

    @Benchmark
    fun mutableListMutexTest(bh: Blackhole, m: MutexWrapper) = runBlocking(Dispatchers.Default) {
        val list = mutableListOf<Int>()
        massiveRun {
            m.mutex.withLock {
                list.add(it)
            }
        }
        require(list.size == 1000 * 10_000)
        bh.consume(list.toList())
    }

    @Benchmark
    fun mutableConcurrentListTest(bh: Blackhole, m: MutexWrapper) = runBlocking(Dispatchers.Default) {
        val list = ConcurrentHashMap.newKeySet<Int>()
        massiveRun {
            list.add(it)
        }
        require(list.size == 1000 * 10_000)
        bh.consume(list.toList())
    }

    @State(Scope.Thread)
    open class SingleThreadDispatcher {
        lateinit var dispather: CoroutineDispatcher

        @Setup(Level.Trial)
        fun doSetup() {
            dispather = Dispatchers.Default.limitedParallelism(1)
        }
    }

    @State(Scope.Thread)
    open class MutexWrapper {
        lateinit var mutex: Mutex

        @Setup(Level.Trial)
        fun doSetup() {
            mutex = Mutex()
        }
    }
}

private suspend fun massiveRun(repeats: Int = 10_000, action: suspend (Int) -> Unit) =
    coroutineScope {
        repeat(1000) { i ->
            launch {
                repeat(repeats) { j ->
                    action(i * 10_000 + j)
                }
            }
        }
    }
