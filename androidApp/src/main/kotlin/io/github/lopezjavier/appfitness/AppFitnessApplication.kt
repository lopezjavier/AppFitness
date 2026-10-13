package io.github.lopezjavier.appfitness

import android.app.Application
import io.github.lopezjavier.appfitness.di.appModule
import io.github.lopezjavier.appfitness.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AppFitnessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AppFitnessApplication)
            modules(appModule, uiModule)
        }
    }
}
