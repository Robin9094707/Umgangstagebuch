package app.umgangstagebuch

import android.content.Context

object BuildConfigCompat {
    fun versionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
