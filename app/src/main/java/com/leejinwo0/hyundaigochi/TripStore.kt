package com.leejinwo0.hyundaigochi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// 주행 기록은 기기 안(앱 전용 폴더)에만 저장 — 서버로 보내지 않음
class TripStore(context: Context) {

    private val file = File(context.filesDir, "trips.json")

    fun loadAll(): List<TripRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length())
                .map { fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.startTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(trip: TripRecord): List<TripRecord> {
        val list = (listOf(trip) + loadAll()).sortedByDescending { it.startTime }
        saveAll(list)
        return list
    }

    fun delete(id: Long): List<TripRecord> {
        val list = loadAll().filterNot { it.id == id }
        saveAll(list)
        return list
    }

    fun clear(): List<TripRecord> {
        file.delete()
        return emptyList()
    }

    private fun saveAll(list: List<TripRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        file.writeText(arr.toString())
    }

    private fun toJson(t: TripRecord) = JSONObject().apply {
        put("id", t.id)
        put("startTime", t.startTime)
        put("endTime", t.endTime)
        put("startOdometer", t.startOdometer?.toDouble() ?: JSONObject.NULL)
        put("endOdometer", t.endOdometer?.toDouble() ?: JSONObject.NULL)
        put("events", JSONArray(t.events.map { it.name }))
        put("harshAccel", t.risk.harshAccel)
        put("harshBrake", t.risk.harshBrake)
        put("unbuckled", t.risk.unbuckled)
        put("longDrive", t.risk.longDrive)
        put("estimated", t.estimated)
    }

    private fun fromJson(o: JSONObject): TripRecord {
        val arr = o.optJSONArray("events") ?: JSONArray()
        val events = (0 until arr.length())
            .mapNotNull { runCatching { TripEvent.valueOf(arr.getString(it)) }.getOrNull() }
            .toSet()
        return TripRecord(
            id = o.getLong("id"),
            startTime = o.getLong("startTime"),
            endTime = o.getLong("endTime"),
            startOdometer = o.floatOrNull("startOdometer"),
            endOdometer = o.floatOrNull("endOdometer"),
            events = events,
            risk = RiskCounts(
                harshAccel = o.optInt("harshAccel"),
                harshBrake = o.optInt("harshBrake"),
                unbuckled = o.optBoolean("unbuckled"),
                longDrive = o.optBoolean("longDrive")
            ),
            estimated = o.optBoolean("estimated")
        )
    }

    private fun JSONObject.floatOrNull(key: String): Float? =
        if (!has(key) || isNull(key)) null else getDouble(key).toFloat()
}
