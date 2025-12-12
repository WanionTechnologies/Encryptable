package wanion.encryptable

import wanion.encryptable.mongo.CID
import wanion.encryptable.mongo.Encryptable
import wanion.encryptable.mongo.EncryptableMongoRepository
import wanion.encryptable.mongo.Encrypt
import wanion.encryptable.mongo.HKDFId
import wanion.encryptable.mongo.annotation.PartOf
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Repository

/**
 * # Example 4: Lists and Collections
 *
 * This example demonstrates:
 * - Working with EncryptableList
 * - Automatic synchronization between memory and database
 * - Adding and removing items from lists
 * - Lazy loading of list elements
 * - Lists with @PartOf for cascade behavior
 */

// ===== Entity Definitions =====

@Document(collection = "tasks")
class Task : Encryptable<Task>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var title: String? = null

    @Encrypt
    var description: String? = null

    var priority: Int = 0 // Not encrypted

    @Encrypt
    var assignedTo: String? = null
}

@Document(collection = "projects")
class Project : Encryptable<Project>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var projectName: String? = null

    @Encrypt
    var description: String? = null

    // List of tasks with @PartOf - tasks deleted when project is deleted
    @Encrypt
    @PartOf
    var tasks: MutableList<Task> = mutableListOf()

    var createdAt: String? = null
}

@Document(collection = "tags")
class Tag : Encryptable<Tag>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var name: String? = null

    @Encrypt
    var color: String? = null
}

@Document(collection = "articles")
class Article : Encryptable<Article>() {
    @HKDFId
    override var id: CID? = null

    @Encrypt
    var title: String? = null

    @Encrypt
    var content: String? = null

    // List WITHOUT @PartOf - tags are shared and won't be deleted
    @Encrypt
    var tags: MutableList<Tag> = mutableListOf()
}

// ===== Repository Definitions =====

@Repository
interface ProjectRepository : EncryptableMongoRepository<Project>

@Repository
interface TaskRepository : EncryptableMongoRepository<Task>

@Repository
interface ArticleRepository : EncryptableMongoRepository<Article>

@Repository
interface TagRepository : EncryptableMongoRepository<Tag>

// ===== Example Usage =====

class ListsExample(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val articleRepository: ArticleRepository,
    private val tagRepository: TagRepository
) {
    /**
     * Generates a cryptographically secure secret with proper entropy
     * Best practice: Use at least 256 bits (32 bytes) of entropy
     */
    private fun generateSecret(): String = String.randomSecret()

    fun example1_CreateListWithItems() {
        println("=== Example 1: Create List with Items ===")

        // Create tasks first
        val task1Secret = generateSecret()
        val task1 = Task().withSecret(task1Secret).apply {
            title = "Design database schema"
            // ...rest of example code...
        }
        // ...rest of example code...
    }
    // ...rest of example code...
}

