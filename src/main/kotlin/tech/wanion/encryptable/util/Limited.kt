package tech.wanion.encryptable.util

import tech.wanion.encryptable.config.EncryptableConfig
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.*
import java.util.function.Function
import java.util.stream.*

/**
# Limited

Provides utilities for creating thread pools and dispatchers with a controlled number of concurrent threads.

- The thread limit is set to a configurable percentage (default: 42%) of available processors, with a minimum of 1 thread.
- This helps prevent resource exhaustion while enabling efficient concurrent execution.

## Usage Guidance
- For CPU-bound parallel tasks, use `parallelForEach` with the `limited` option set to `true`. This restricts concurrency and avoids thread starvation.
- For I/O-bound parallel tasks, use `parallelForEach` with `limited` set to `false`. This leverages virtual threads, which are well-suited for handling many blocking operations concurrently.

> **Note:** The `dispatcher` method is not recommended for use with `InheritableThreadLocal`, as coroutine contexts do not propagate thread-local values reliably.
 */
object Limited {
    /**
     * The maximum number of threads allowed for parallel execution per request.
     *
     * This limit is set to a configurable percentage (default: 34%) of available processors (logical cores), with a minimum of 1 thread.
     *
     * Purpose:
     * - Prevents a single request from exhausting all server resources by capping the number of threads it can use.
     * - Ensures fair resource allocation and system stability, especially in multi-tenant or high-concurrency environments.
     * - Allows efficient parallelism for CPU-bound tasks without risking thread starvation or degraded performance for other requests.
     *
     * Why 34%?
     * - The default value is set to 0.34 (34%) to ensure that, when multiplied by the number of logical cores and rounded down to an integer,
     *   the thread limit does not scale down too aggressively due to floating-point rounding. This provides a more consistent and predictable allocation of threads per request, especially on systems with SMT (Simultaneous Multithreading).
     * - On processors with SMT (e.g., AMD or Intel CPUs with 2 threads per physical core), 34% of logical cores corresponds to approximately 2/3 of the physical cores. For example, on a CPU with 16 physical cores and SMT (32 logical cores), 34% of 32 is about 10, which is roughly 2/3 of 16 physical cores. This ensures a safe and efficient thread cap for CPU-bound workloads.
     *
     * The value can be configured via the 'thread.limit.percentage' property in the environment.
     */
    private val threadLimit: Int = EncryptableConfig.threadLimit

    /**
     * Returns the effective thread limit based on the configured maximum and the requested size.
     *
     * @param size The desired number of threads.
     * @return The lesser of the configured thread limit and the requested size.
     */
    private fun getLimit(size: Int) = minOf(threadLimit, size)

    /**
     * Creates a fixed thread pool with a limited number of threads based on the configured thread limit.
     *
     * If the effective thread limit is 1, consider executing tasks sequentially for optimal performance.
     *
     * @param size The desired number of threads (typically the number of tasks to run in parallel).
     * @return An ExecutorService with a fixed thread pool limited to the configured maximum.
     */
    fun threadPool(size: Int): ExecutorService = Executors.newFixedThreadPool(getLimit(size), Thread.ofVirtual().factory())

    /**
     * Extension function to process elements of an Array in parallel, using a thread pool.
     *
     * If the effective thread limit is 1, executes sequentially for optimal performance.
     *
     * **Usage:**
     *
     * - If `limited` is `true`, uses `Limited.threadPool` to restrict the number of concurrent threads.
     *   - This mode is intended for **CPU-bound tasks** and is **not recommended** for parallel tasks that block I/O, as excessive blocking can lead to thread starvation or degraded performance.
     * - If `limited` is `false`, uses a virtual thread pool with parallelism equal to the number of items.
     *   - In this mode, it can be used for **I/O-bound tasks**, as virtual threads are well-suited for handling many blocking operations concurrently.
     *
     * The thread pool is always properly shut down after execution, even if exceptions occur.
     *
     * **Parameters:**
     * - `limited`: Whether to limit the number of threads (default: `true`). If `false`, suitable for I/O-bound tasks.
     * - `action`: The action to perform on each element.
     */
    fun <T> Array<T>.parallelForEach(limited: Boolean = true, action: (T) -> Unit) {
        if (this.isEmpty()) return
        val limit = if (limited) getLimit(this.size) else this.size
        if (limit == 1) {
            this.forEach(action)
            return
        }
        val pool = if (limited) threadPool(this.size) else Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = this.map { item ->
                pool.submit { action(item) }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Extension function to process elements of an Iterable in parallel, using a thread pool.
     *
     * If the effective thread limit is 1, executes sequentially for optimal performance.
     *
     * **Usage:**
     *
     * - If `limited` is `true`, uses `Limited.threadPool` to restrict the number of concurrent threads.
     *   - This mode is intended for **CPU-bound tasks** and is **not recommended** for parallel tasks that block I/O, as excessive blocking can lead to thread starvation or degraded performance.
     * - If `limited` is `false`, uses a virtual thread pool with parallelism equal to the number of items.
     *   - In this mode, it can be used for **I/O-bound tasks**, as virtual threads are well-suited for handling many blocking operations concurrently.
     *
     * The thread pool is always properly shut down after execution, even if exceptions occur.
     *
     * **Parameters:**
     * - `limited`: Whether to limit the number of threads (default: `true`). If `false`, suitable for I/O-bound tasks.
     * - `action`: The action to perform on each element.
     */
    fun <T> Iterable<T>.parallelForEach(limited: Boolean = true, action: (T) -> Unit) {
        val count = this.count()
        if (count == 0) return
        val limit = if (limited) getLimit(count) else count
        if (limit == 1) {
            this.forEach(action)
            return
        }
        val pool = if (limited) threadPool(count) else Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = this.map { item ->
                pool.submit { action(item) }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Extension function to process entries of a Map in parallel, using a thread pool.
     *
     * If the effective thread limit is 1, executes sequentially for optimal performance.
     *
     * Usage:
     * - If `limited` is `true`, uses Limited.threadPool to restrict the number of concurrent threads.
     *   This mode is intended for CPU-bound tasks and is not recommended for parallel tasks that block I/O,
     *   as excessive blocking can lead to thread starvation or degraded performance.
     * - If `limited` is `false`, uses a virtual thread pool with parallelism equal to the number of entries.
     *   In this mode, it can be used for I/O-bound tasks, as virtual threads are well-suited for handling many blocking operations concurrently.
     *
     * The thread pool is always properly shut down after execution, even if exceptions occur.
     *
     * Parameters:
     * - limited: Whether to limit the number of threads (default: true). If false, suitable for I/O-bound tasks.
     * - action: The action to perform on each Map.Entry.
     */
    fun <K, V> Map<out K, V>.parallelForEach(limited: Boolean = true, action: (Map.Entry<K, V>) -> Unit) {
        if (this.isEmpty()) return
        val limit = if (limited) getLimit(this.size) else this.size
        if (limit == 1) {
            this.entries.forEach(action)
            return
        }
        val pool = if (limited) threadPool(this.size) else Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = this.entries.map { entry ->
                pool.submit { action(entry) }
            }
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Extension function to map elements of a List in parallel, using a thread pool.
     *
     * If the effective thread limit is 1, executes sequentially for optimal performance.
     *
     * **Usage:**
     *
     * - If `limited` is `true`, uses `Limited.threadPool` to restrict the number of concurrent threads.
     *   - This mode is intended for **CPU-bound tasks** and is **not recommended** for parallel tasks that block I/O, as excessive blocking can lead to thread starvation or degraded performance.
     * - If `limited` is `false`, uses a virtual thread pool with parallelism equal to the number of items.
     *   - In this mode, it can be used for **I/O-bound tasks**, as virtual threads are well-suited for handling many blocking operations concurrently.
     *
     * The thread pool is always properly shut down after execution, even if exceptions occur.
     *
     * **Parameters:**
     * - `limited`: Whether to limit the number of threads (default: `true`). If `false`, suitable for I/O-bound tasks.
     * - `transform`: The transformation function to apply to each element.
     *
     * @return A new list containing the transformed elements in the same order.
     */
    fun <T, R> List<T>.parallelMap(limited: Boolean = true, transform: (T) -> R): List<R> {
        if (this.isEmpty()) return emptyList()
        val limit = if (limited) getLimit(this.size) else this.size
        if (limit == 1) {
            return this.map(transform)
        }
        val pool = if (limited) threadPool(this.size) else Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = this.mapIndexed { index, item ->
                pool.submit<Pair<Int, R>> { index to transform(item) }
            }
            return futures.map { it.get() }
                .sortedBy { it.first }
                .map { it.second }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Extension function to replace all elements of a MutableList in parallel, using a thread pool.
     *
     * If the effective thread limit is 1, executes sequentially for optimal performance.
     *
     * This is a parallel version of `MutableList.replaceAll()` that processes transformations concurrently.
     *
     * **Usage:**
     *
     * - If `limited` is `true`, uses `Limited.threadPool` to restrict the number of concurrent threads.
     *   - This mode is intended for **CPU-bound tasks** and is **not recommended** for parallel tasks that block I/O, as excessive blocking can lead to thread starvation or degraded performance.
     * - If `limited` is `false`, uses a virtual thread pool with parallelism equal to the number of items.
     *   - In this mode, it can be used for **I/O-bound tasks**, as virtual threads are well-suited for handling many blocking operations concurrently.
     *
     * The thread pool is always properly shut down after execution, even if exceptions occur.
     *
     * **Parameters:**
     * - `limited`: Whether to limit the number of threads (default: `true`). If `false`, suitable for I/O-bound tasks.
     * - `transform`: The transformation function to apply to each element.
     */
    fun <T> MutableList<T>.parallelReplaceAll(limited: Boolean = true, transform: (T) -> T) {
        if (this.isEmpty()) return
        val limit = if (limited) getLimit(this.size) else this.size
        if (limit == 1) {
            this.replaceAll(transform)
            return
        }
        val pool = if (limited) threadPool(this.size) else Executors.newVirtualThreadPerTaskExecutor()
        try {
            val futures = this.mapIndexed { index, item ->
                pool.submit<Pair<Int, T>> { index to transform(item) }
            }
            val results = futures.map { it.get() }
            results.forEach { (index, value) ->
                this[index] = value
            }
        } finally {
            pool.shutdown()
        }
    }

    /**
     * Extension function to replace all elements of a MutableList, automatically choosing between
     * sequential and parallel processing based on list size to avoid overhead for small lists.
     *
     * If the effective thread limit is 1, executes sequentially for optimal performance.
     *
     * For small lists (< threshold), uses sequential `replaceAll()`. For larger lists, uses
     * parallel processing to improve performance with CPU-bound operations like encryption.
     *
     * **Usage:**
     *
     * - If `limited` is `true`, uses `Limited.threadPool` to restrict the number of concurrent threads.
     * - If `limited` is `false`, uses a virtual thread pool with parallelism equal to the number of items.
     * - `threshold`: The minimum list size for parallel processing (default: 1000). Lists smaller than this
     *   will use sequential processing to avoid parallelization overhead.
     *
     * **Parameters:**
     * - `limited`: Whether to limit the number of threads when using parallel processing (default: `true`).
     * - `threshold`: Minimum size for parallel processing (default: `1000`). Adjust based on operation cost.
     * - `transform`: The transformation function to apply to each element.
     */
    fun <T> MutableList<T>.smartReplaceAll(limited: Boolean = true, threshold: Int = 1000, transform: (T) -> T) {
        val limit = if (limited) getLimit(this.size) else this.size
        if (this.size < threshold || limit == 1) {
            this.replaceAll(transform)
        } else {
            this.parallelReplaceAll(limited, transform)
        }
    }

    /**
     * New API: return a Stream that behaves similarly to `stream().parallel()` but uses the
     * limited executor provided by this utility. The returned stream captures intermediate
     * operations and will execute the whole pipeline under the limited executor when a
     * terminal operation is invoked.
     *
     * Note: This implementation eagerly collects the source stream when a terminal operation
     * runs. It supports the common intermediate operations (map, filter, flatMap, peek,
     * distinct, sorted, limit, skip). Infinite streams and some rare short-circuiting
     * optimizations are not supported by this wrapper.
     */
    fun <T> Stream<T>.limitedParallel(limited: Boolean = true): Stream<T> = LimitedStream(this, limited)

    // Internal stream wrapper that captures pipeline stages and executes them at terminal time.
    private class LimitedStream<T>(
        private val source: Stream<T>,
        private val limited: Boolean,
        private val stages: List<(Stream<Any?>) -> Stream<Any?>> = emptyList()
    ) : Stream<T> {

        // Helper to execute the captured pipeline and return a concrete Stream<T>
        private fun executePipeline(): Stream<T> {
            val baseList = source.collect(Collectors.toList())
            @Suppress("UNCHECKED_CAST")
            var s: Stream<Any?> = baseList.stream() as Stream<Any?>
            for (stage in stages) {
                s = stage(s)
            }
            @Suppress("UNCHECKED_CAST")
            return s as Stream<T>
        }

        // Utility to create a new LimitedStream with an appended stage
        private fun withStage(stage: (Stream<Any?>) -> Stream<Any?>): LimitedStream<T> =
            LimitedStream(source, limited, stages + stage)

        // --- Intermediate operations that we capture ---
        @Suppress("UNCHECKED_CAST")
        override fun filter(predicate: Predicate<in T>): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.filter(predicate as Predicate<Any?>) }
            return withStage(stage)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <R> map(mapper: Function<in T, out R>): Stream<R> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.map(mapper as Function<Any?, Any?>) }
            @Suppress("UNCHECKED_CAST")
            return LimitedStream(source as Stream<R>, limited, stages + stage)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <R> flatMap(mapper: Function<in T, out Stream<out R>>): Stream<R> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s ->
                s.flatMap(mapper as Function<Any?, out Stream<Any?>>)
            }
            @Suppress("UNCHECKED_CAST")
            return LimitedStream(source as Stream<R>, limited, stages + stage)
        }

        @Suppress("UNCHECKED_CAST")
        override fun peek(action: Consumer<in T>): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.peek(action as Consumer<Any?>) }
            return withStage(stage)
        }

        override fun distinct(): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.distinct() }
            return withStage(stage)
        }

        override fun sorted(): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.sorted() }
            return withStage(stage)
        }

        @Suppress("UNCHECKED_CAST")
        override fun sorted(comparator: Comparator<in T>): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.sorted(comparator as Comparator<Any?>) }
            return withStage(stage)
        }

        override fun limit(maxSize: Long): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.limit(maxSize) }
            return withStage(stage)
        }

        override fun skip(n: Long): Stream<T> {
            val stage: (Stream<Any?>) -> Stream<Any?> = { s -> s.skip(n) }
            return withStage(stage)
        }

        // --- Terminal operations executed under the configured executor ---
        override fun forEach(action: Consumer<in T>) {
            val result = executePipeline().collect(Collectors.toList())
            if (result.isEmpty()) return
            val limit = if (limited) getLimit(result.size) else result.size
            if (limit == 1) {
                result.forEach { action.accept(it) }
                return
            }
            val pool = if (limited) threadPool(result.size) else Executors.newVirtualThreadPerTaskExecutor()
            try {
                val futures = result.map { item -> pool.submit { action.accept(item) } }
                futures.forEach { it.get() }
            } finally {
                pool.shutdown()
            }
        }

        override fun forEachOrdered(action: Consumer<in T>) {
            // Preserve encounter order: execute same pipeline, collect results, then apply action in order.
            val result = executePipeline().collect(Collectors.toList())
            result.forEach { action.accept(it) }
        }

        override fun <R, A> collect(collector: Collector<in T, A, R>): R {
            // Execute pipeline and collect sequentially using the downstream collector.
            return executePipeline().collect(collector)
        }

        override fun <R> collect(supplier: Supplier<R>, accumulator: BiConsumer<R, in T>, combiner: BiConsumer<R, R>): R {
            @Suppress("UNCHECKED_CAST")
            return executePipeline().collect(supplier, accumulator as BiConsumer<R, T>, combiner)
        }

        override fun toArray(): Array<Any?> = executePipeline().toArray()

        override fun <A> toArray(generator: IntFunction<Array<A>>): Array<A> = executePipeline().toArray(generator)

        override fun reduce(identity: T, accumulator: BinaryOperator<T>): T = executePipeline().reduce(identity, accumulator)

        override fun reduce(accumulator: BinaryOperator<T>): Optional<T> = executePipeline().reduce(accumulator)

        override fun <U> reduce(identity: U, accumulator: BiFunction<U, in T, U>, combiner: BinaryOperator<U>): U =
            executePipeline().reduce(identity, accumulator, combiner)

        // --- Additional terminal operations delegated to the executed pipeline ---
        override fun min(comparator: Comparator<in T>): Optional<T> = executePipeline().min(comparator)

        override fun max(comparator: Comparator<in T>): Optional<T> = executePipeline().max(comparator)

        override fun count(): Long = executePipeline().count()

        override fun anyMatch(predicate: Predicate<in T>): Boolean = executePipeline().anyMatch(predicate)

        override fun allMatch(predicate: Predicate<in T>): Boolean = executePipeline().allMatch(predicate)

        override fun noneMatch(predicate: Predicate<in T>): Boolean = executePipeline().noneMatch(predicate)

        override fun findFirst(): Optional<T> = executePipeline().findFirst()

        override fun findAny(): Optional<T> = executePipeline().findAny()

        override fun mapToInt(mapper: ToIntFunction<in T>): IntStream = executePipeline().mapToInt(mapper)

        override fun mapToLong(mapper: ToLongFunction<in T>): LongStream = executePipeline().mapToLong(mapper)

        override fun mapToDouble(mapper: ToDoubleFunction<in T>): DoubleStream = executePipeline().mapToDouble(mapper)

        override fun flatMapToInt(mapper: Function<in T, out IntStream>): IntStream = executePipeline().flatMapToInt(mapper)

        override fun flatMapToLong(mapper: Function<in T, out LongStream>): LongStream = executePipeline().flatMapToLong(mapper)

        override fun flatMapToDouble(mapper: Function<in T, out DoubleStream>): DoubleStream = executePipeline().flatMapToDouble(mapper)

        // --- Other Stream methods delegate to the executed pipeline (this triggers evaluation) ---
        override fun iterator(): MutableIterator<T> = executePipeline().iterator()

        override fun spliterator(): Spliterator<T> = executePipeline().spliterator()

        override fun isParallel(): Boolean = true

        override fun sequential(): Stream<T> = this // keep wrapper; sequential() won't disable the limited behavior

        override fun parallel(): Stream<T> = this

        override fun unordered(): Stream<T> = withStage { s -> s.unordered() }

        override fun onClose(closeHandler: Runnable): Stream<T> {
            // Forward close handler to underlying source
            source.onClose(closeHandler)
            return this
        }

        override fun close() {
            source.close()
        }
    }
}