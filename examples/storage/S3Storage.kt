package com.example

import tech.wanion.encryptable.storage.Storage

@Storage(storageClass = S3StorageImpl::class)
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class S3Storage