package io.github.lopezjavier.appfitness.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Tracks the resumed [Activity] so platform adapters that need an Activity context (Credential Manager) can get
 * it without a ViewModel ever holding one. Holds a weak reference only.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity>? = null

    /** The foreground activity, or null between activities. */
    fun get(): Activity? = current?.get()

    /** Registers this holder for the whole process lifetime. */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
