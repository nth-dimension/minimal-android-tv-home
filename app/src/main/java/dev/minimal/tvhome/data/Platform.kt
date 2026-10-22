package dev.minimal.tvhome.data

import android.content.Context
import android.media.tv.TvContract

/** Which TV platform this is running on. The home screen is the same; a few edges differ. */
object Platform {
    /** Feature Amazon declares on every Fire TV running Fire OS. */
    private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"

    fun isFireTv(context: Context): Boolean = context.packageManager.hasSystemFeature(FIRE_TV_FEATURE)

    /** "Fire TV" or "Google TV", for the few labels that name the platform. */
    fun name(context: Context): String = if (isFireTv(context)) "Fire TV" else "Google TV"

    /**
     * Whether the system has the TV provider that Watch Next rows live in. Fire OS has none, so
     * Continue watching there comes only from the playbacks Minimal Home records itself.
     */
    fun hasTvProvider(context: Context): Boolean =
        context.packageManager.resolveContentProvider(TvContract.AUTHORITY, 0) != null
}
