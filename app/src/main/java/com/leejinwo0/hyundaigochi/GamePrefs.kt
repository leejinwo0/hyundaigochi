package com.leejinwo0.hyundaigochi

import android.content.Context

// 설정과 차 정보는 기기 안(앱 전용 저장소)에만 저장 — 서버로 보내지 않음
class GamePrefs(context: Context) {

    private val sp = context.getSharedPreferences("hyundaigochi", Context.MODE_PRIVATE)

    fun loadPlayer(): Player? {
        val name = sp.getString("name", null) ?: return null
        val rawUsages = sp.getString("usages", null) ?: sp.getString("usage", "COMMUTE") ?: "COMMUTE"
        val parsedUsages = rawUsages.split(",")
            .mapNotNull { runCatching { Usage.valueOf(it) }.getOrNull() }
            .toSet()
        val usages = if (parsedUsages.isEmpty()) setOf(Usage.COMMUTE) else parsedUsages
        val fuel = runCatching { FuelType.valueOf(sp.getString("fuel", "") ?: "") }.getOrNull()
        val birthYm = sp.getInt("birthYm", 0).takeIf { it > 0 }
        val focus = (sp.getString("focus", "") ?: "")
            .split(",")
            .mapNotNull { runCatching { StatType.valueOf(it) }.getOrNull() }
            .toSet()
        val goals = mutableMapOf<Long, SeasonGoal>()
        (sp.getString("goals", "") ?: "").split(",").forEach { item ->
            val parts = item.split(":")
            if (parts.size == 2) {
                val key = parts[0].toLongOrNull()
                val goal = runCatching { SeasonGoal.valueOf(parts[1]) }.getOrNull()
                if (key != null && goal != null) goals[key] = goal
            }
        }
        return Player(
            modelId = sp.getString("modelId", "avante") ?: "avante",
            colorIndex = sp.getInt("colorIndex", 0),
            name = name,
            plate = sp.getString("plate", "") ?: "",
            usages = usages,
            focus = focus,
            demoMode = sp.getBoolean("demoMode", false),
            createdAt = sp.getLong("createdAt", System.currentTimeMillis()),
            goals = goals,
            fuel = fuel,
            birthYm = birthYm
        )
    }

    fun savePlayer(p: Player) {
        sp.edit()
            .putString("name", p.name)
            .putString("modelId", p.modelId)
            .putInt("colorIndex", p.colorIndex)
            .putString("plate", p.plate)
            .putString("usages", p.usages.joinToString(",") { it.name })
            .putString("fuel", p.fuel?.name ?: "")
            .putInt("birthYm", p.birthYm ?: 0)
            .putString("focus", p.focus.joinToString(",") { it.name })
            .putBoolean("demoMode", p.demoMode)
            .putLong("createdAt", p.createdAt)
            .putString("goals", p.goals.entries.joinToString(",") { "${it.key}:${it.value.name}" })
            .apply()
    }

    var settledUntil: Long
        get() = sp.getLong("settledUntil", 0L)
        set(v) {
            sp.edit().putLong("settledUntil", v).apply()
        }

    var lastReportedSeason: Long
        get() = sp.getLong("lastReportedSeason", 0L)
        set(v) {
            sp.edit().putLong("lastReportedSeason", v).apply()
        }

    // 마지막으로 확인한 누적 주행거리 (앱이 꺼져 있던 동안의 거리를 되찾는 기준)
    var odoBaseline: Float?
        get() = if (sp.contains("odoBaseline")) sp.getFloat("odoBaseline", 0f) else null
        set(v) {
            val e = sp.edit()
            if (v == null) e.remove("odoBaseline") else e.putFloat("odoBaseline", v)
            e.apply()
        }

    fun clearAll() {
        sp.edit().clear().apply()
    }
}
