package com.rendyhd.vicu.location

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An area the platform watches: one circle, entered ([LocationTrigger.ARRIVE]) or left
 * ([LocationTrigger.LEAVE]). The reminders that share a place, radius and trigger share one region,
 * so ten tasks at Home cost one of the few regions a platform allows.
 */
data class LocationRegion(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val trigger: LocationTrigger,
    val taskIds: List<Long>,
) {
    /** For Swift, which watches the region and reports both directions. */
    val notifyOnEntry: Boolean get() = trigger == LocationTrigger.ARRIVE
}

/**
 * Turns location reminders into the regions to watch. Pure: the coordinator feeds it the reminders
 * of open tasks and hands the result to the platform.
 */
object LocationRegionPlanner {
    /** iOS watches at most 20 regions per app. */
    const val IOS_CAPACITY = 20

    /** Play services allows 100 geofences per app. */
    const val ANDROID_CAPACITY = 100

    /**
     * The id of the region [reminder] belongs to. It is built from the position (to about a metre),
     * the radius and the trigger, so it stays the same across plans and changes when any of them does.
     */
    fun regionId(reminder: LocationReminder): String {
        val lat = (reminder.latitude * 1e5).roundToLong()
        val lon = (reminder.longitude * 1e5).roundToLong()
        val kind = if (reminder.trigger == LocationTrigger.ARRIVE) "a" else "l"
        return "vicu-loc-$kind-${lat}_${lon}-${reminder.radiusMeters}"
    }

    /**
     * The regions for [reminders] (already limited to open tasks), at most [capacity]. When there
     * are more, the ones nearest to [near] are kept, or the ones with the most tasks when the
     * position is unknown; the rest come in when a closer one fires and the plan runs again.
     */
    fun plan(reminders: List<LocationReminder>, capacity: Int, near: GeoPoint? = null): List<LocationRegion> {
        val regions = reminders
            .groupBy(::regionId)
            .map { (id, group) ->
                val first = group.first()
                LocationRegion(
                    id = id,
                    latitude = first.latitude,
                    longitude = first.longitude,
                    radiusMeters = first.radiusMeters,
                    trigger = first.trigger,
                    taskIds = group.map { it.taskId }.sorted(),
                )
            }
        val ordered = if (near != null) {
            regions.sortedWith(compareBy({ distanceMeters(near, GeoPoint(it.latitude, it.longitude)) }, { it.id }))
        } else {
            regions.sortedWith(compareByDescending<LocationRegion> { it.taskIds.size }.thenBy { it.id })
        }
        return ordered.take(capacity.coerceAtLeast(0))
    }

    /** Great-circle distance in metres. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        fun rad(deg: Double) = deg * PI / 180.0
        val dLat = rad(b.latitude - a.latitude)
        val dLon = rad(b.longitude - a.longitude)
        val h = sin(dLat / 2).let { it * it } +
            cos(rad(a.latitude)) * cos(rad(b.latitude)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
