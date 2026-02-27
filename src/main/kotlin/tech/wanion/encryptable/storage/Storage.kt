package tech.wanion.encryptable.storage

import kotlin.reflect.KClass

@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Storage(
    val storageClass: KClass<out IStorage<*>>
)