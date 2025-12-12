# 🚫 Coroutines Incompatibility with Encryptable

## 🏯 Executive Summary

Coroutines are intentionally **incompatible** with Encryptable.\
Encryptable relies on thread-local / inheritable thread-local state to carry per-request secrets and to implement deterministic change detection. Kotlin coroutines do not reliably preserve thread-local state across suspensions and context switches. As a result, using coroutines with Encryptable can silently break change detection, thread-local secrets, resource cleanup, and other core features.

> Encryptable depends on thread-local propagation for security and correctness. Coroutines break this assumption.

---

## ❌ Why Coroutines Break Encryptable

- **Thread-local secrets:** Encryptable stores per-request secrets and tracking data in thread-local storage (`InheritableThreadLocal`).
- **Coroutine context switches:** Kotlin coroutines may run continuations on different threads and do not automatically propagate thread-local or inheritable thread-local values across suspension points. ([Kotlin docs](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#thread-local-data))
- **Result:** The framework can lose access to the per-request secret (or see stale values) when work resumes on a different thread, breaking:
  - Change detection (hash comparison relies on consistent thread-local secrets and context)
  - Per-request zero-knowledge encryption (secret may be missing or wrong)
  - Unsaved-entity tracking and cleanup

> **Bottom line:** Coroutines invalidate Encryptable's core architectural assumptions.

---

## 🛑 Our Choice: Deliberate Incompatibility

We evaluated ways to make coroutines compatible (propagating thread-locals, bridging code, per-coroutine context carriers). The effort required is large, invasive, and fragile. After weighing benefits and costs, we intentionally chose **not** to support coroutines.

> **Fail-fast behavior:** Encryptable checks for the presence of Kotlin coroutines (e.g., `kotlinx.coroutines.CoroutineScope`) on the classpath at application startup. If detected, the framework throws an exception and prevents startup. This avoids silent failures, subtle bugs, and security risks.

- **Early detection:** Any incompatibility is detected early, providing clear feedback and preventing production issues.
- **Strictness:** Silent failures could compromise data integrity and security, which are core to Encryptable's guarantees.

**Reasons:**
- Propagating `InheritableThreadLocal` reliably across coroutine suspensions is complex and error-prone.
- Change-detection and lifecycle systems depend on thread-local inheritance semantics.
- Supporting coroutines would require touching many internals and risk subtle, hard-to-diagnose bugs.

> **Design decision:** We prefer a clear, explicit incompatibility over an unreliable compatibility layer.

---

## 🧭 Recommended Alternatives

1. **Use traditional Java concurrency primitives** (`ExecutorService`, `CompletableFuture`, `ForkJoinPool`) for concurrency in code that touches Encryptable internals.
2. **Prefer the provided `Limited` helper functions** (see below) instead of `stream().parallel()` or unbounded thread pools.
3. **Use `Limited` extension functions** for arrays, iterables, and maps. They are compatible with Encryptable's thread-local design and change detection.

---

## 🛠️ Limited Utilities ([See Implementation](../src/main/kotlin/tech/wanion/encryptable/util/Limited.kt))

The project provides `Limited.kt` utilities to run parallel work while preserving Encryptable invariants.

- **Concurrency limit:** `Limited` uses a configurable percentage of available processors (`thread.limit.percentage` in `application.properties`, default: `0.34`).
- **Extensions:** Offers `parallelForEach` for `Array<T>`, `Iterable<T>`, and `Map<K,V>`.
- **Thread pool:** Uses a bounded thread pool for CPU-bound work, or virtual threads for I/O-bound work.
- **Safe parallelism:** Preserves thread-local semantics per task.
- **Tunable occupation:** Adjust via `thread.limit.percentage`.

---

## 🌀 `Stream.limitedParallel()`

The public API provides a stream-returning extension that behaves like a parallel stream while using the `Limited` executor.

**Signature:**
```kotlin
fun <T> Stream<T>.limitedParallel(limited: Boolean = true): Stream<T>
```

**Behavior:**
- Returns a `Stream<T>` wrapper (`LimitedStream`) that captures intermediate operations.
- Delays execution until a terminal operation is invoked.
- At terminal time:
  1. Collects the source into a concrete `List` and applies intermediate stages.
  2. Creates a bounded thread pool or virtual-thread executor based on `limited` flag.
  3. For `forEach`, submits one task per item to the executor and waits for completion.
  4. Other terminal operations run sequentially on the collected result.

**Usage Example:**
```kotlin
// CPU-bound: limited, like stream().parallel().forEach{...}
myList.stream()
    .limitedParallel(limited = true)
    .map { expensiveTransform(it) }
    .filter { keep(it) }
    .forEach { item -> process(item) }

// I/O-bound: virtual threads
myList.stream()
    .limitedParallel(limited = false)
    .forEach { item -> blockingIo(item) }
```

---

## ⚠️ Important Caveats & Differences from `stream().parallel()`

- **Eager collection:** The wrapper collects the entire source at terminal time. Not suitable for infinite streams; may increase peak memory usage.
- **Short-circuiting:** Terminal ops like `findFirst`, `anyMatch` run on the collected result, not in parallel across workers.
- **Parallel terminals:** Only `forEach` dispatches per-item parallel tasks. Others run sequentially.
- **Ordering:** `forEach` is unordered; `forEachOrdered` preserves order by running sequentially after collection.
- **Limited behavior:** `.sequential()`/`.parallel()` on the wrapper keep the limited behavior.

> **Trade-off:** Robust, easy to reason about, and safe for Encryptable internals while enabling common parallel patterns.

---

## 📊 How the Concurrency Limit Is Applied

- `Limited.threadPool(size)` creates the executor for per-item parallel work.
- `getLimit(size)` returns `minOf(threadLimit, size)` where `threadLimit` is computed from `thread.limit.percentage` and available processors.
- At terminal time (`forEach`), the executor enforces concurrency via its fixed thread pool size.

---

## 📝 Recommendation & Best Practices

> **Use `stream().limitedParallel()`** in place of `stream().parallel()` when interacting with Encryptable internals or when you want to cap per-request concurrency.

- Prefer `limited = true` for CPU-bound work. Use `limited = false` (virtual threads) for high-concurrency blocking I/O tasks.
- **Avoid coroutines** in any code path that touches Encryptable internals. If you need coroutines, isolate Encryptable work behind a traditional `ExecutorService` boundary.

---

## 🏁 Conclusion

- **Coroutines are intentionally incompatible** with Encryptable because they break thread-local propagation required by the framework.
- **`Stream.limitedParallel()`** returns a stream wrapper that behaves like `stream().parallel()` for common usage patterns while enforcing a concurrency cap and preserving Encryptable invariants.
- **Use `Limited` helpers and `limitedParallel()`** for parallelism with Encryptable. Tune `thread.limit.percentage` in `application.properties` to adjust per-request occupation.

> For implementation details, see [`Limited.kt`](../src/main/kotlin/tech/wanion/encryptable/util/Limited.kt).