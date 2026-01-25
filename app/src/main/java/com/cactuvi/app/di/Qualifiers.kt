package com.cactuvi.app.di

import javax.inject.Qualifier

/**
 * Qualifier to distinguish the active source's base URL.
 * Used for injecting the current API service configuration.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveSourceUrl

/**
 * Qualifier to distinguish the active source's credentials.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveSourceCredentials
