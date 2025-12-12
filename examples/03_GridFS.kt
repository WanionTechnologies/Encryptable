package wanion.encryptable

import wanion.encryptable.mongo.CID
import wanion.encryptable.mongo.Encryptable
import wanion.encryptable.mongo.EncryptableMongoRepository
import wanion.encryptable.mongo.Encrypt
import wanion.encryptable.mongo.HKDFId
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository

/**
 * # Example 3: GridFS and Large Binary Fields
 *
 * This example demonstrates:
 * - Storing large binary files (images, documents, etc.)
 * - Automatic GridFS storage for ByteArray fields >1KB
 * - Lazy loading of large files
 * - Encrypting files before GridFS storage
 * - Efficient handling of large data
 */

// ===== Entity Definitions =====

@Document(collection = "documents")
class DocumentEntity : Encryptable<DocumentEntity>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var title: String? = null

    @Encrypt
    var description: String? = null

    // Small file - stored directly in document
    @Encrypt
    var thumbnail: ByteArray? = null // <1KB - stored in document

    // Large file - automatically stored in GridFS
    @Encrypt
    var pdfContent: ByteArray? = null // >1KB - stored in GridFS

    @Encrypt
    var imageContent: ByteArray? = null // >1KB - stored in GridFS

    var contentType: String? = null // Not encrypted
    var fileSize: Long? = null // Not encrypted
}

@Document(collection = "profiles")
class UserProfile : Encryptable<UserProfile>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var username: String? = null

    // Profile picture - encrypted and stored in GridFS if large
    @Encrypt
    var profilePicture: ByteArray? = null

    // Banner image
    @Encrypt
    var bannerImage: ByteArray? = null

    // Small icon - likely stored in document
    @Encrypt
    var icon: ByteArray? = null
}

// ===== Repository Definitions =====

@Repository
interface DocumentEntityRepository : EncryptableMongoRepository<DocumentEntity>

@Repository
interface UserProfileRepository : EncryptableMongoRepository<UserProfile>

// ===== Example Usage =====

class GridFSExample(
    private val documentRepository: DocumentEntityRepository,
    private val userProfileRepository: UserProfileRepository
) {
    /**
     * Generates a cryptographically secure secret with proper entropy
     * Best practice: Use at least 256 bits (32 bytes) of entropy
     */
    private fun generateSecret(): String = String.randomSecret()

    /**
     * Creates a sample ByteArray to simulate file content
     */
    private fun createSampleFile(sizeInKB: Int): ByteArray {
        return ByteArray(sizeInKB * 1024) { (it % 256).toByte() }
    }

    fun example1_SmallVsLargeFiles() {
        println("=== Example 1: Small vs Large Files ===")

        val secret = generateSecret()
        val document = DocumentEntity().withSecret(secret).apply {
            title = "Sample Document"
            description = "Demonstrating small and large file handling"

            // Small thumbnail - 512 bytes - stored in MongoDB document
            thumbnail = ByteArray(512) { it.toByte() }

            // Large PDF - 500KB - automatically stored in GridFS
            pdfContent = createSampleFile(500)

            // Large image - 2MB - automatically stored in GridFS
            imageContent = createSampleFile(2048)

            contentType = "application/pdf"
            fileSize = pdfContent?.size?.toLong()
        }

        documentRepository.save(document, secret)
        println("Document saved with ID: ${document.id}")
        println("Thumbnail size: ${document.thumbnail?.size} bytes (stored in document)")
        println("PDF size: ${document.pdfContent?.size} bytes (stored in GridFS)")
        println("Image size: ${document.imageContent?.size} bytes (stored in GridFS)")
        println("\nNote: Files >1KB are automatically stored in GridFS")
        println("Secret for later: ${secret.take(20)}...")
    }

    fun example2_LazyLoadingLargeFiles(secret: String) {
        println("\n=== Example 2: Lazy Loading of Large Files ===")

        // Retrieve the document - large files are NOT loaded yet
        val document = documentRepository.findBySecretOrNull(secret)

        if (document != null) {
            println("Document retrieved: ${document.title}")
            println("Description: ${document.description}")

            // Small thumbnail is already loaded (stored in document)
            println("\nThumbnail loaded: ${document.thumbnail != null}")
            println("Thumbnail size: ${document.thumbnail?.size} bytes")

            // Large files are lazy-loaded when accessed
            println("\nAccessing PDF content (triggers lazy load from GridFS)...")
            val pdfSize = document.pdfContent?.size
            println("PDF loaded on-demand, size: $pdfSize bytes")

            println("\nAccessing image content (triggers lazy load from GridFS)...")
            val imageSize = document.imageContent?.size
            println("Image loaded on-demand, size: $imageSize bytes")

            println("\nBenefit: Large files only loaded when actually needed!")
        }
    }

    fun example3_WorkingWithRealFiles() {
        println("\n=== Example 3: Working with Real Files ===")

        val secret = generateSecret()

        // Simulate reading a real file
        // In production: val imageBytes = File("path/to/image.jpg").readBytes()
        val imageBytes = createSampleFile(1500) // 1.5MB sample image

        val profile = UserProfile().withSecret(secret).apply {
            username = "john_photographer"
            profilePicture = imageBytes
            bannerImage = createSampleFile(3000) // 3MB banner
            icon = ByteArray(256) { it.toByte() } // 256 bytes - stored in document
        }

        userProfileRepository.save(profile, secret)
        println("Profile saved with ID: ${profile.id}")
        println("Profile picture: ${profile.profilePicture?.size} bytes (GridFS)")
        println("Banner image: ${profile.bannerImage?.size} bytes (GridFS)")
        println("Icon: ${profile.icon?.size} bytes (document)")

        // Later, retrieve and access specific files
        val retrieved = userProfileRepository.findBySecretOrNull(secret)
        if (retrieved != null) {
            // Only load the profile picture, not the banner
            val pictureData = retrieved.profilePicture
            println("\nRetrieved profile picture: ${pictureData?.size} bytes")

            // Banner is NOT loaded yet (lazy loading)
            // It will only load if/when accessed
            println("Banner image will load on first access")
        }
    }

    fun example4_UpdatingLargeFiles(secret: String) {
        println("\n=== Example 4: Updating Large Files ===")

        val document = documentRepository.findBySecretOrNull(secret)

        if (document != null) {
            println("Original PDF size: ${document.pdfContent?.size} bytes")

            // Replace the PDF with a new version
            val newPdfContent = createSampleFile(750) // 750KB
            document.pdfContent = newPdfContent

            println("Updated PDF size: ${newPdfContent.size} bytes")
            println("\nThe framework will:")
            println("1. Detect the field change (via checksum)")
            println("2. Delete the old GridFS file")
            println("3. Store the new encrypted file in GridFS")
            println("4. Update only the changed field in MongoDB")

            // Changes persist automatically when request completes
        }
    }

    fun example5_EncryptedVsUnencryptedFiles() {
        println("\n=== Example 5: Encrypted vs Unencrypted Files ===")

        @Document(collection = "public_documents")
        class PublicDocument : Encryptable<PublicDocument>() {
            @HKDFId
            override var id: UUID? = null

            var title: String? = null // Not encrypted

            // Large file WITHOUT @Encrypt - stored in GridFS but NOT encrypted
            var publicPdfContent: ByteArray? = null

            // Large file WITH @Encrypt - encrypted before GridFS storage
            @Encrypt
            var privatePdfContent: ByteArray? = null
        }

        println("Field annotations determine encryption:")
        println("- publicPdfContent: Stored in GridFS, NOT encrypted")
        println("- privatePdfContent: Encrypted with AES-256, then stored in GridFS")
        println("\nAll ByteArray fields >1KB go to GridFS automatically")
        println("@Encrypt annotation controls whether they're encrypted first")
    }

    fun example6_GridFSCleanup(secret: String) {
        println("\n=== Example 6: GridFS Cleanup on Delete ===")

        val document = documentRepository.findBySecretOrNull(secret)

        if (document != null) {
            println("Document has ${document.pdfContent?.size} bytes in GridFS")
            println("Deleting document...")

            // Delete the document
            documentRepository.deleteBySecret(secret)

            println("Document deleted")
            println("Associated GridFS files automatically cleaned up!")
            println("\nNote: If fields are removed from the class definition,")
            println("orphaned GridFS files may remain - implement periodic cleanup")
        }
    }

    fun example7_PerformanceConsiderations() {
        println("\n=== Example 7: Performance Considerations ===")

        val secret = generateSecret()

        // Create document with multiple large files
        val document = DocumentEntity().withSecret(secret).apply {
            title = "Performance Test"
            pdfContent = createSampleFile(5000) // 5MB
            imageContent = createSampleFile(10000) // 10MB
        }

        println("Saving document with 15MB of files...")
        val startTime = System.currentTimeMillis()
        documentRepository.save(document, secret)
        val saveTime = System.currentTimeMillis() - startTime
        println("Saved in ${saveTime}ms")

        println("\nRetrieving document metadata (without loading files)...")
        val retrieveStart = System.currentTimeMillis()
        val retrieved = documentRepository.findBySecretOrNull(secret)
        val retrieveTime = System.currentTimeMillis() - retrieveStart
        println("Retrieved in ${retrieveTime}ms")
        println("Title: ${retrieved?.title}")

        println("\nLazy loading PDF (only when needed)...")
        val loadStart = System.currentTimeMillis()
        val pdfSize = retrieved?.pdfContent?.size
        val loadTime = System.currentTimeMillis() - loadStart
        println("Loaded ${pdfSize} bytes in ${loadTime}ms")

        println("\nKey insights:")
        println("- Initial retrieval is fast (metadata only)")
        println("- Large files loaded on-demand")
        println("- Only load what you need to use")

        // Cleanup
        documentRepository.deleteBySecret(secret)
    }

    fun runAllExamples() {
        val secret = example1_SmallVsLargeFiles().let { generateSecret() }

        // Note: For examples 2, 4, 6 you'd need the actual secret from example 1
        // example2_LazyLoadingLargeFiles(secret)

        example3_WorkingWithRealFiles()
        example5_EncryptedVsUnencryptedFiles()
        example7_PerformanceConsiderations()

        println("\n=== GridFS Examples Completed ===")
    }
}
