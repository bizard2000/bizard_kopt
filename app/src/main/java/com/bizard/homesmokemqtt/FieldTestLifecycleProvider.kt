package com.bizard.homesmokemqtt

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/** Initializes field-test logging early and records process/activity lifecycle events. */
class FieldTestLifecycleProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val c = context ?: return true
        FieldTestRecorder.init(c)
        if (FieldTestRecorder.isActive()) {
            FieldTestRecorder.record("PROCESS_STARTED", "pid process created")
        }
        (c.applicationContext as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (FieldTestRecorder.isActive()) FieldTestRecorder.record("ACTIVITY_CREATED", activity.javaClass.simpleName)
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (FieldTestRecorder.isActive()) FieldTestRecorder.record("ACTIVITY_RESUMED", activity.javaClass.simpleName)
            }
            override fun onActivityPaused(activity: Activity) {
                if (FieldTestRecorder.isActive()) FieldTestRecorder.record("ACTIVITY_PAUSED", activity.javaClass.simpleName)
            }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (FieldTestRecorder.isActive()) FieldTestRecorder.record("ACTIVITY_DESTROYED", activity.javaClass.simpleName)
            }
        })
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
