package com.takaisaisei.pixelims.telephony

import android.annotation.SuppressLint
import android.content.res.Resources

/** Access to the framework's Wi-Fi Calling operator-name label templates. */
object WfcLabels {

    @SuppressLint("DiscouragedApi")
    fun templates(): List<String> = runCatching {
        val res = Resources.getSystem()
        val id = res.getIdentifier("wfcSpnFormats", "array", "android")
        if (id == 0) emptyList() else res.getStringArray(id).toList()
    }.getOrDefault(emptyList())
}
