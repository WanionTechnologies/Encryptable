package tech.wanion.encryptable.mongo.migration

/**
 * Interface representing a migration that can be applied to the MongoDB database.
 * Implementations of this interface should define the logic for determining whether
 * a migration is needed and the steps to perform the migration.
 */
interface Migration {
    /** Returns the source version that this migration is designed to update from. */
    fun fromVersion(): String

    /** Returns the target version that this migration will update the database to. */
    fun toVersion(): String

    /** Determines whether the migration should be applied based on the current state of the database. */
    fun shouldMigrate(): Boolean = true

    /** Executes the migration logic to update the database schema or data as needed. */
    fun migrateSchema()

    /** Executes any necessary data migration steps after the schema has been updated. */
    fun migrateData()
}