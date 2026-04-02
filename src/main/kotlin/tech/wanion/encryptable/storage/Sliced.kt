package tech.wanion.encryptable.storage


/**
 * Marks a [ByteArray] field for sliced storage.
 *
 * When a field is annotated with [@Sliced], Encryptable will automatically split the byte array
 * into independent slices and store them as separate entries in the storage backend.
 *
 * If the field is also annotated with [@Encrypt], each slice will be independently encrypted
 * with its own IV and authentication tag (AES-256-GCM). Without [@Encrypt], slices are stored
 * as raw bytes — no encryption is applied.
 *
 * The field-as-live-mirror semantics are fully preserved: assign, update, null, and read all
 * behave identically to a non-sliced field. Slicing is entirely transparent to the developer.
 *
 * **Why slicing?**
 * Loading a large [ByteArray] as a single encrypted blob requires holding the full ciphertext
 * and plaintext in memory simultaneously — physically impossible in memory-constrained environments
 * (edge runtimes, serverless functions, low-memory JVM instances). With [@Sliced], consumers
 * process one slice at a time (~8MB peak memory per 4MB slice), regardless of total file size.
 * How slices are delivered to end users is the responsibility of the application, not the framework.
 *
 * **Reference layout:**
 * The concatenated reference [ByteArray] stored in the entity document has the following layout:
 * ```
 * [0..7]                         → 8 bytes — original plaintext data length (Long, big-endian)
 * [8 .. 8+refLen-1]              → slice 0 reference
 * [8+refLen .. 8+2*refLen-1]     → slice 1 reference
 * ...
 * [8+(N-1)*refLen .. 8+N*refLen-1] → slice N-1 reference
 * ```
 * The 8-byte length header allows pre-allocating a [ByteArray] of the exact output size before
 * fetching a single slice — enabling fully parallel fetch + decrypt with direct index placement,
 * and no intermediate buffers or reassembly step.
 *
 * Slice count: `(referenceBytes.size - 8) / storage.referenceLength`
 *
 * **Recommended slice sizes:**
 * - Large media (video, audio): 4 MB — ~4ms decrypt time with AES-NI
 * - Audio files: 1 MB — smaller slices, lower per-slice latency
 * - Large documents / CAD: 2 MB
 * - General large files: 2 MB
 *
 * @param sizeMB The size of each slice in megabytes. Must be between 1 and 32. Defaults to 4 (4MB).
 *
 * @throws IllegalArgumentException if `sizeMB` is less than 1 or greater than 32 (validated at runtime during metadata processing).
 *
 * @see tech.wanion.encryptable.mongo.Encrypt
 * @see IStorage
 * @see Storage
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sliced(val sizeMB: Int = 4)
