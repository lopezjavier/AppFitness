package io.github.lopezjavier.appfitness

import android.app.Application
import io.github.lopezjavier.appfitness.di.appModule
import io.github.lopezjavier.appfitness.di.sharedModule
import io.github.lopezjavier.appfitness.di.uiModule
import io.github.lopezjavier.appfitness.platform.CurrentActivityHolder
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AppFitnessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin =
            startKoin {
                androidLogger(Level.ERROR)
                androidContext(this@AppFitnessApplication)
                modules(appModule, sharedModule, uiModule)
            }.koin
        // Credential Manager needs an Activity context; the holder hands the foreground one to the adapter.
        koin.get<CurrentActivityHolder>().register(this)
    }
}
