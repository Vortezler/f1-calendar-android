package com.praval.f1calendar.di

import javax.inject.Qualifier

/** The app talks to two unrelated services, so each needs its own Retrofit instance. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ErgastRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenF1Retrofit

/**
 * A process-lifetime scope for work that must outlive any one screen — specifically the shared
 * live-session poll, which both the navigation bar and the Live screen subscribe to.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
