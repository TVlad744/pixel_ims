package com.takaisaisei.pixelims.domain

import android.content.SharedPreferences
import android.os.PersistableBundle

/**
 * A boxed, typed CarrierConfig value that owns its I/O: writing itself into the [PersistableBundle]
 * the broker applies, persisting itself to [SharedPreferences], and re-reading a stored value.
 */
sealed interface CfgValue {
    fun putInto(bundle: PersistableBundle, key: String)
    fun saveInto(editor: SharedPreferences.Editor, key: String)

    /** Re-reads a persisted value of this same type, defaulting to the current value when absent. */
    fun reload(prefs: SharedPreferences, key: String): CfgValue

    data class Bool(val v: Boolean) : CfgValue {
        override fun putInto(bundle: PersistableBundle, key: String) = bundle.putBoolean(key, v)
        override fun saveInto(editor: SharedPreferences.Editor, key: String) {
            editor.putBoolean(key, v)
        }

        override fun reload(prefs: SharedPreferences, key: String) = Bool(prefs.getBoolean(key, v))
    }

    data class IntVal(val v: Int) : CfgValue {
        override fun putInto(bundle: PersistableBundle, key: String) = bundle.putInt(key, v)
        override fun saveInto(editor: SharedPreferences.Editor, key: String) {
            editor.putInt(key, v)
        }

        override fun reload(prefs: SharedPreferences, key: String) = IntVal(prefs.getInt(key, v))
    }
}
