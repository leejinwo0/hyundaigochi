package com.leejinwo0.hyundaigochi

import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 순수 Kotlin (Android 의존 없음)

const val DAY_MS = 24L * 60 * 60 * 1000
const val HOUR_MS = 60L * 60 * 1000
const val DEMO_SEASON_TRIPS = 3 // 시연 모드: 주행 3회마다 시즌 종료

enum class Mood { CALM, HAPPY, WET, WARM, COOL, TIRED, SLEEPY, BORED, HUNGRY, SCARED, DIZZY, PARTY }

data class Stage(val level: Int, val title: String, val minXp: Int, val desc: String, val reward: String)

enum class SeasonGoal(val label: String, val desc: String, val boosts: Map<StatType, Float>) {
    COMMUTE("출퇴근 리그", "성실함·적응력이 1.3배 빨리 올라요", mapOf(StatType.DILIGENCE to 1.3f, StatType.ADAPT to 1.3f)),
    TRIP("주말 여행 시즌", "지구력·모험심이 1.3배 빨리 올라요", mapOf(StatType.ENDURANCE to 1.3f, StatType.ADVENTURE to 1.3f)),
    SAFETY("안전 운전 챌린지", "안전이 1.5배 빨리 올라요", mapOf(StatType.SAFETY to 1.5f))
}

data class Player(
    val modelId: String,
    val colorIndex: Int,
    val name: String,
    val plate: String,
    val usages: Set<Usage>,
    val focus: Set<StatType>,
    val demoMode: Boolean,
    val createdAt: Long,
    val goals: Map<Long, SeasonGoal>,
    val fuel: FuelType? = null,
    val birthYm: Int? = null // 출고 연월 (yyyyMM)
) {
    // 고른 용도에서 1.2배가 되는 능력치 (겹쳐도 한 번만)
    val usageBoosts: Set<StatType> get() = usages.flatMap { it.boosts }.toSet()

    val usageLabel: String get() = usages.sortedBy { it.ordinal }.joinToString(" · ") { it.label }

    // 새 차의 시작 능력치: 안전만 99, 나머지는 0
    fun baseStats(): List<Int> = CareerEngine.START_STATS
}

enum class Tone { GOOD, BAD, LEVEL }

data class LogEntry(val time: Long, val text: String, val tone: Tone)

// 경험치가 어떻게 계산됐는지
data class XpBreakdown(
    val base: Int,
    val distance: Int,
    val experiences: Int,
    val clean: Int,
    val total: Int,    // 컨디션 보정 전 합계
    val halved: Boolean
)

// 주행 한 번으로 능력치가 얼마나, 왜 바뀌었는지
data class StatChange(
    val type: StatType,
    val delta: Float,
    val reasons: List<String>,
    val capped: Boolean // 상한(99)에 막혀 덜 올랐는지
)

data class TripResult(
    val trip: TripRecord,
    val xpGained: Int,
    val conditionBefore: Int,
    val conditionAfter: Int,
    val statsBefore: List<Int>,
    val statsAfter: List<Int>,
    val ovrBefore: Int,
    val ovrAfter: Int,
    val logs: List<LogEntry>,
    val levelUp: Stage?,
    val milestoneKm: Int?,
    val xp: XpBreakdown,
    val changes: List<StatChange>
)

data class CareerProfile(
    val xp: Int,
    val stage: Stage,
    val nextStage: Stage?,
    val condition: Int,
    val stats: List<Int>,
    val growth: List<Int>,
    val ovr: Int,
    val mood: Mood,
    val line: String,
    val results: List<TripResult>,
    val logs: List<LogEntry>, // 최신이 앞
    val totalKm: Float,
    val tripCount: Int,
    val xpByTrip: Map<Long, Int>,
    val avgXp: Int,          // 최근 주행 평균 경험치
    val tripsToNext: Int?    // 다음 단계까지 예상 주행 횟수
) {
    val progress: Float
        get() = nextStage?.let { (xp - stage.minXp).toFloat() / (it.minXp - stage.minXp) } ?: 1f
}

// 앱을 열었을 때 한꺼번에 보여 줄 "그동안의 정산"
data class AwaySummary(
    val tripCount: Int,
    val estimatedCount: Int,
    val km: Float,
    val xpGained: Int,
    val statGains: List<Pair<StatType, Int>>,
    val logs: List<LogEntry>,
    val levelUp: Stage?,
    val conditionBefore: Int,
    val conditionAfter: Int,
    val live: Boolean, // 방금 도착한 주행 1회
    val maxEndTime: Long
)

data class SeasonInfo(val key: Long, val label: String, val trips: List<TripRecord>, val complete: Boolean)

data class Reaction(val who: String, val text: String)

data class SeasonReport(
    val season: SeasonInfo,
    val ovrBefore: Int,
    val ovrAfter: Int,
    val statsBefore: List<Int>,
    val statsAfter: List<Int>,
    val tripCount: Int,
    val km: Float,
    val cleanPercent: Int,
    val xp: Int,
    val bestTripKm: Float,
    val firsts: List<String>,
    val riskTotal: Int,
    val reactions: List<Reaction>
)

object CareerEngine {

    // 새 차: 안전 99, 나머지 0 (지구력, 성실함, 적응력, 사교성, 모험심, 안전 순서)
    val START_STATS = listOf(0, 0, 0, 0, 0, 99)

    // 종합 능력치 = 지구력·성실함·적응력·사교성·모험심 평균에서, 안전이 99에서 깎인 만큼 뺀 값
    // 새 차는 0에서 시작하고, 모두 99가 되면 99
    fun ovrFor(s: List<Int>): Int =
        max(0, ((s.take(5).sum() - (MAX_STAT - s[5])) / 5f).roundToInt())

    fun startOvr(): Int = ovrFor(START_STATS)

    // 15단계. 보상은 도트 차 꾸밈(휠 색, 줄무늬, 지붕 램프, 반짝임, 왕관)으로 실제 화면에 나타남
    val stages = listOf(
        Stage(1, "갓 출고된 새내기", 0, "막 출고된 차예요. 안전만 가득 찬 채로 첫 길을 나서요.", ""),
        Stage(2, "길들이는 중", 100, "운전자와 서로 손발을 맞춰 가는 단계예요.", ""),
        Stage(3, "동네 마실러", 250, "가까운 길에 익숙해지고, 다니는 길이 조금씩 넓어져요.", "휠이 은빛으로 바뀌어요"),
        Stage(4, "단골길 개척자", 450, "자주 가는 길이 생기고 새 길도 하나둘 찾아내요.", ""),
        Stage(5, "출퇴근 베테랑", 700, "같은 길을 오가며 믿음직해지는 단계예요.", "휠이 파랗게 바뀌고 차체에 줄무늬가 생겨요"),
        Stage(6, "사계절 드라이버", 1000, "비·밤·추위·더위를 두루 겪은 단계예요.", ""),
        Stage(7, "장거리 도전자", 1400, "처음으로 먼 길에 도전해 보는 단계예요.", "휠이 초록빛으로 바뀌어요"),
        Stage(8, "고속도로 러너", 1900, "쭉 뻗은 길에서도 흔들림 없이 달려요.", "지붕에 작은 램프가 달려요"),
        Stage(9, "주말 여행가", 2500, "주말마다 새로운 곳으로 떠나는 단계예요.", "휠이 보랏빛이 되고 줄무늬가 금색으로 바뀌어요"),
        Stage(10, "장거리 여행가", 3200, "먼 길이 오히려 반가운 단계예요.", ""),
        Stage(11, "도로 위의 베테랑", 4000, "수많은 길을 달려 본 노련한 차예요.", "휠이 주황빛으로 바뀌어요"),
        Stage(12, "노련한 길잡이", 5000, "어떤 날씨, 어떤 길에서도 침착해요.", "차 주변에 반짝임이 생겨요"),
        Stage(13, "도로의 명장", 6200, "달린 길만큼 이야기가 쌓인 명장이에요.", "휠이 금색으로 바뀌어요"),
        Stage(14, "전설의 드라이버", 7600, "다른 차들이 이름을 기억하는 전설이에요.", ""),
        Stage(15, "명예의 전당", 9200, "최고 단계예요. 함께 달린 모든 길이 기록으로 남아 있어요.", "머리 위에 왕관이 얹히고 휠이 눈부시게 빛나요")
    )

    private const val LOW_CONDITION = 40
    private const val MAX_STAT = 99

    fun stageFor(xp: Int): Stage = stages.last { xp >= it.minXp }

    // ─── 시즌 ───
    fun seasonKeys(sorted: List<TripRecord>, demo: Boolean): List<Long> {
        if (!demo) return sorted.map { startOfMonth(it.startTime) }
        var counted = 0
        return sorted.map { t ->
            if (t.estimated) {
                (max(counted - 1, 0) / DEMO_SEASON_TRIPS + 1).toLong()
            } else {
                val key = (counted / DEMO_SEASON_TRIPS + 1).toLong()
                counted++
                key
            }
        }
    }

    fun seasonLabel(key: Long, demo: Boolean): String =
        if (demo) "시즌 $key" else {
            val cal = Calendar.getInstance().apply { timeInMillis = key }
            "${cal.get(Calendar.YEAR)}년 ${cal.get(Calendar.MONTH) + 1}월 시즌"
        }

    fun nextSeasonKey(key: Long, demo: Boolean): Long =
        if (demo) key + 1 else Calendar.getInstance().apply {
            timeInMillis = key
            add(Calendar.MONTH, 1)
        }.timeInMillis

    // 최신 시즌이 앞
    fun seasons(tripsAny: List<TripRecord>, demo: Boolean, now: Long): List<SeasonInfo> {
        val trips = tripsAny.sortedBy { it.startTime }
        val keys = seasonKeys(trips, demo)
        val currentMonth = startOfMonth(now)
        return trips.indices
            .groupBy { keys[it] }
            .map { (key, idx) ->
                val list = idx.map { trips[it] }
                val complete = if (demo) list.count { !it.estimated } >= DEMO_SEASON_TRIPS else key < currentMonth
                SeasonInfo(key, seasonLabel(key, demo), list, complete)
            }
            .sortedByDescending { it.key }
    }

    // ─── 커리어 계산 ───
    fun build(tripsAny: List<TripRecord>, now: Long, battery: Int?, player: Player): CareerProfile {
        val trips = tripsAny.sortedBy { it.startTime }
        val keys = seasonKeys(trips, player.demoMode)
        val base = player.baseStats()
        val cur = FloatArray(6) { base[it].toFloat() } // 소수점으로 쌓고 보여 줄 때만 반올림

        fun statsNow(): List<Int> = List(6) { min(MAX_STAT, max(0, cur[it].roundToInt())) }
        fun ovrOf(s: List<Int>): Int = ovrFor(s)

        val results = mutableListOf<TripResult>()
        val allLogs = mutableListOf<LogEntry>()
        val seen = mutableSetOf<TripEvent>()
        val xpByTrip = mutableMapOf<Long, Int>()
        val days = mutableSetOf<Long>()
        var xp = 0
        var condition = 100
        var totalKm = 0f
        var prevEnd: Long? = null

        for ((i, t) in trips.withIndex()) {
            prevEnd?.let { condition = min(100, condition + restBonus(t.startTime - it)) }
            val conditionBefore = condition
            val stageBefore = stageFor(xp)
            val statsBefore = statsNow()
            val ovrBefore = ovrOf(statsBefore)
            val logs = mutableListOf<LogEntry>()
            val km = t.distanceKm ?: 0f
            val low = condition < LOW_CONDITION

            // 경험치
            val parts = xpParts(t, km)
            val gained = if (low) parts.total / 2 else parts.total
            xp += gained
            xpByTrip[t.id] = gained

            // 컨디션 (위험 운전은 깎고, 차분한 주행은 회복)
            if (!t.estimated) {
                val r = t.risk
                val penalty = (r.harshAccel + r.harshBrake) * 10 +
                    (if (r.unbuckled) 30 else 0) + (if (r.longDrive) 15 else 0)
                if (penalty > 0) {
                    condition = max(0, condition - penalty)
                    logs += LogEntry(t.endTime, "${riskText(r)}, 컨디션 -$penalty", Tone.BAD)
                } else if (km >= 1f) {
                    condition = min(100, condition + 5)
                }
            }
            if (low) logs += LogEntry(t.endTime, "컨디션이 나빠 경험치와 성장이 절반이었다", Tone.BAD)
            if (t.estimated) logs += LogEntry(t.endTime, "앱이 꺼져 있던 동안 ${"%.1f".format(km)}km를 달렸다", Tone.GOOD)

            // 처음 겪은 일
            t.events.sortedBy { it.ordinal }.forEach { e ->
                if (seen.add(e)) logs += LogEntry(t.endTime, firstText(e), Tone.GOOD)
            }

            // 누적 1,000km 단위 돌파
            val milestone = milestoneKm(t.startOdometer, t.endOdometer)
            milestone?.let { logs += LogEntry(t.endTime, "누적 ${"%,d".format(it)}km 달성", Tone.GOOD) }

            // 능력치 변화: 오르는 값에는 보너스(집중 육성·용도·시즌 목표)와 컨디션 보정, 깎이는 값에는 보정 없음
            val firstOfDay = days.add(startOfDay(t.startTime))
            val raw = deltas(t, firstOfDay, km)
            val goal = player.goals[keys[i]]
            val changes = mutableListOf<StatChange>()
            StatType.values().forEachIndexed { idx, type ->
                val (d, why) = raw[idx]
                if (d == 0f) return@forEachIndexed
                var applied = d
                val reasons = mutableListOf(why)
                if (d > 0f) {
                    var m = 1f
                    if (type in player.focus) { m *= 1.5f; reasons += "집중 육성 ×1.5" }
                    if (type in player.usageBoosts) { m *= 1.2f; reasons += "용도 보너스 ×1.2" }
                    goal?.boosts?.get(type)?.let { gm -> m *= gm; reasons += "시즌 목표 ×$gm" }
                    if (low) { m *= 0.5f; reasons += "컨디션이 낮아 ×0.5" }
                    applied = d * m
                }
                val before = cur[idx]
                cur[idx] = (cur[idx] + applied).coerceIn(0f, MAX_STAT.toFloat())
                val real = cur[idx] - before
                changes += StatChange(type, real, reasons, capped = applied > 0f && real < applied - 0.001f)
            }
            totalKm += km

            val statsAfter = statsNow()
            val ovrAfter = ovrOf(statsAfter)
            val stageAfter = stageFor(xp)
            val levelUp = if (stageAfter.level > stageBefore.level) stageAfter else null
            levelUp?.let { logs += LogEntry(t.endTime, "Lv.${it.level} 승급, ${it.title}" + (if (it.reward.isNotBlank()) " · ${it.reward}" else ""), Tone.LEVEL) }

            allLogs += logs
            results += TripResult(
                t, gained, conditionBefore, condition, statsBefore, statsAfter,
                ovrBefore, ovrAfter, logs, levelUp, milestone, parts.copy(halved = low), changes
            )
            prevEnd = t.endTime
        }

        val currentCondition = prevEnd?.let { min(100, condition + restBonus(now - it)) } ?: condition
        val stats = statsNow()
        val stage = stageFor(xp)
        val next = stages.firstOrNull { it.level == stage.level + 1 }
        var mood = moodOf(trips.lastOrNull(), results.lastOrNull(), currentCondition, now, battery)
        var line = lineFor(mood, now, player.fuel)
        val nowYm = ymOf(now)
        player.birthYm?.let { bym ->
            if (CarAge.isBirthdayMonth(bym, nowYm) && mood != Mood.DIZZY && mood != Mood.SCARED) {
                mood = Mood.PARTY
                line = "이번 달은 내 ${CarAge.nth(bym, nowYm)}번째 생일이 있는 달이야! 같이 달려 줘서 고마워."
            }
        }

        // 다음 단계까지 예상 주행 횟수 (최근 주행 평균 경험치 기준)
        val recent = results.filter { !it.trip.estimated }.takeLast(5)
        val avgXp = if (recent.isEmpty()) 30 else recent.map { it.xpGained }.average().roundToInt().coerceAtLeast(5)
        val tripsToNext = next?.let { n ->
            val remain = (n.minXp - xp).coerceAtLeast(0)
            (remain + avgXp - 1) / avgXp
        }

        return CareerProfile(
            xp = xp,
            stage = stage,
            nextStage = next,
            condition = currentCondition,
            stats = stats,
            growth = stats.mapIndexed { i, v -> v - base[i] },
            ovr = ovrOf(stats),
            mood = mood,
            line = line,
            results = results,
            logs = allLogs.reversed(),
            totalKm = totalKm,
            tripCount = trips.size,
            xpByTrip = xpByTrip,
            avgXp = avgXp,
            tripsToNext = tripsToNext
        )
    }

    private fun xpParts(t: TripRecord, km: Float): XpBreakdown =
        if (t.estimated) {
            XpBreakdown(0, km.toInt(), 0, 0, km.toInt(), false)
        } else {
            val exp = t.events.size * 5
            val clean = if (t.risk.isClean) 5 else 0
            XpBreakdown(10, km.toInt(), exp, clean, 10 + km.toInt() + exp + clean, false)
        }

    // 주행 1회로 바뀌는 능력치와 그 이유 (지구력, 성실함, 적응력, 사교성, 모험심, 안전 순서)
    // [가정] 기준값 — 시연하며 조정
    private fun deltas(t: TripRecord, firstOfDay: Boolean, km: Float): List<Pair<Float, String>> {
        val none = 0f to ""
        val out = MutableList(6) { none }
        if (km > 0f) out[0] = km / 10f to "달린 거리 ${"%.1f".format(km)}km ÷ 10"
        if (t.estimated) {
            if (firstOfDay) out[1] = 1f to "오늘 첫 주행 +1"
            return out
        }
        out[1] = (0.5f + (if (firstOfDay) 1f else 0f)) to
            (if (firstOfDay) "주행 +0.5, 오늘 첫 주행 +1" else "주행 +0.5")

        var adapt = 0f
        val adaptParts = mutableListOf<String>()
        if (TripEvent.RAIN in t.events) { adapt += 1.5f; adaptParts += "비 +1.5" }
        if (TripEvent.NIGHT_LIGHTS in t.events || isNight(hourOf(t.startTime)) || isNight(hourOf(t.endTime))) {
            adapt += 1f; adaptParts += "밤길 +1"
        }
        if (TripEvent.HEATER in t.events) { adapt += 1f; adaptParts += "히터 +1" }
        if (TripEvent.AIR_CON in t.events) { adapt += 1f; adaptParts += "에어컨 +1" }
        if (adapt > 0f) {
            out[2] = min(adapt, 3.5f) to (adaptParts.joinToString(", ") + (if (adapt > 3.5f) " (한 번에 최대 3.5)" else ""))
        }

        if (TripEvent.PASSENGER in t.events) out[3] = 2f to "동승자 +2"

        var adv = 0f
        val advParts = mutableListOf<String>()
        if (km >= 100f) { adv += 3f; advParts += "100km 이상 +3" }
        else if (km >= 50f) { adv += 2f; advParts += "50km 이상 +2" }
        if (TripEvent.WINDOW_OPEN in t.events) { adv += 0.5f; advParts += "창문 열고 달리기 +0.5" }
        if (adv > 0f) out[4] = adv to advParts.joinToString(", ")

        val r = t.risk
        if (r.isClean) {
            if (km >= 1f) out[5] = 1.5f to "깨끗한 주행 +1.5"
        } else {
            var loss = 0f
            val lossParts = mutableListOf<String>()
            if (r.harshAccel > 0) { loss += 3f * r.harshAccel; lossParts += "급가속 ${r.harshAccel}회 −${3 * r.harshAccel}" }
            if (r.harshBrake > 0) { loss += 3f * r.harshBrake; lossParts += "급감속 ${r.harshBrake}회 −${3 * r.harshBrake}" }
            if (r.unbuckled) { loss += 8f; lossParts += "안전벨트 미착용 −8" }
            if (r.longDrive) { loss += 4f; lossParts += "2시간 넘는 연속 운전 −4" }
            out[5] = -loss to lossParts.joinToString(", ")
        }
        return out
    }

    private fun restBonus(gapMs: Long): Int = ((gapMs / HOUR_MS) * 2).toInt().coerceIn(0, 100) // 1시간 쉴 때마다 +2

    private fun moodOf(last: TripRecord?, lastResult: TripResult?, condition: Int, now: Long, battery: Int?): Mood {
        if (last == null) return if (battery != null && battery < 20) Mood.HUNGRY else Mood.CALM
        val sinceLast = now - last.endTime
        val recent = sinceLast < 6 * HOUR_MS
        return when {
            condition < LOW_CONDITION -> Mood.DIZZY
            recent && last.risk.unbuckled -> Mood.SCARED
            recent && (lastResult?.levelUp != null || lastResult?.milestoneKm != null) -> Mood.PARTY
            sinceLast >= 3 * DAY_MS -> Mood.BORED
            battery != null && battery < 20 -> Mood.HUNGRY
            recent && TripEvent.RAIN in last.events -> Mood.WET
            recent && TripEvent.HEATER in last.events -> Mood.WARM
            recent && TripEvent.AIR_CON in last.events -> Mood.COOL
            recent && TripEvent.PASSENGER in last.events -> Mood.HAPPY
            recent && (last.risk.longDrive || (last.distanceKm ?: 0f) >= 100f) -> Mood.TIRED
            isNight(hourOf(now)) -> Mood.SLEEPY
            else -> Mood.CALM
        }
    }

    private fun lineFor(mood: Mood, now: Long, fuel: FuelType?): String {
        val options = when (mood) {
            Mood.CALM -> listOf("오늘도 준비 완료. 어디든 같이 가자.", "가만히 서 있는 것도 나쁘지 않네.")
            Mood.HAPPY -> listOf("오늘은 혼자가 아니었지! 기분 최고야.", "누가 같이 타니까 괜히 신났어.")
            Mood.WET -> listOf("비 맞아서 좀 젖었어. 그래도 빗길 경험은 꽤 쌓였다!", "와이퍼가 아직도 얼얼해.")
            Mood.WARM -> listOf("히터 덕분에 따끈따끈해.", "추운 날엔 역시 따뜻한 게 최고야.")
            Mood.COOL -> listOf("에어컨 바람 시원했다.", "더운 날 잘 버텼어, 우리.")
            Mood.TIRED -> listOf("오늘 정말 멀리 갔다… 바퀴가 아직 뜨거워.", "조금만 쉬었다 가자. 나 좀 지쳤어.")
            Mood.SLEEPY -> listOf("밤이 깊었네. 나도 이제 좀 잘게.", "졸려… 내일 또 만나.")
            Mood.BORED -> listOf("며칠째 그대로야… 나 심심해.", "나 잊은 거 아니지?")
            Mood.HUNGRY -> when (fuel) {
                FuelType.ELECTRIC -> listOf("배터리가 얼마 안 남았어. 밥 좀 줘!", "충전하러 가면 안 될까?")
                FuelType.HYDROGEN -> listOf("수소가 얼마 안 남았어. 충전소 가면 안 될까?", "배가 고파… 수소 좀 줘!")
                else -> listOf("연료가 얼마 안 남았어. 기름 좀 줘!", "주유소 들르면 안 될까?")
            }
            Mood.SCARED -> listOf("벨트 안 하면 나 무섭단 말이야. 다음엔 꼭 매 줘.", "안전벨트 없이 달리는 건 싫어.")
            Mood.DIZZY -> listOf("아까 급하게 움직여서 어질어질해. 살살 가자.", "컨디션이 바닥이야. 조금 쉬면 나아질 거야.")
            Mood.PARTY -> listOf("나 한 단계 컸어! 같이 달려 줘서 고마워.", "오늘은 내 생일 같은 날이야!")
        }
        return options[((now / HOUR_MS) % options.size).toInt()]
    }

    private fun riskText(r: RiskCounts): String = listOfNotNull(
        if (r.harshAccel > 0) "급가속 ${r.harshAccel}회" else null,
        if (r.harshBrake > 0) "급감속 ${r.harshBrake}회" else null,
        if (r.unbuckled) "안전벨트 미착용" else null,
        if (r.longDrive) "2시간 넘게 쉬지 않고 운전" else null
    ).joinToString(", ")

    private fun firstText(e: TripEvent): String = when (e) {
        TripEvent.RAIN -> "처음으로 빗길을 달렸다, 적응력 상승"
        TripEvent.NIGHT_LIGHTS -> "처음으로 전조등을 켜고 밤길을 달렸다, 적응력 상승"
        TripEvent.HEATER -> "처음으로 히터를 켰다, 적응력 상승"
        TripEvent.AIR_CON -> "처음으로 에어컨을 켰다, 적응력 상승"
        TripEvent.PASSENGER -> "처음으로 누군가 함께 탔다, 사교성 상승"
        TripEvent.WINDOW_OPEN -> "처음으로 창문을 열고 달렸다, 모험심 상승"
    }

    private fun milestoneKm(start: Float?, end: Float?): Int? {
        if (start == null || end == null) return null
        val before = (start / 1000).toInt()
        val after = (end / 1000).toInt()
        return if (after > before) after * 1000 else null
    }

    // ─── 앱을 열었을 때의 정산 ───
    fun pending(profile: CareerProfile, settledUntil: Long): AwaySummary? {
        val list = profile.results.filter { it.trip.endTime > settledUntil }
        if (list.isEmpty()) return null
        val first = list.first()
        val last = list.last()
        val gains = StatType.values().mapIndexedNotNull { i, type ->
            val g = last.statsAfter[i] - first.statsBefore[i]
            if (g != 0) type to g else null // 깎인 능력치(안전)도 함께 보여 줌
        }
        return AwaySummary(
            tripCount = list.size,
            estimatedCount = list.count { it.trip.estimated },
            km = list.sumOf { (it.trip.distanceKm ?: 0f).toDouble() }.toFloat(),
            xpGained = list.sumOf { it.xpGained },
            statGains = gains,
            logs = list.flatMap { it.logs },
            levelUp = list.mapNotNull { it.levelUp }.maxByOrNull { it.level },
            conditionBefore = first.conditionBefore,
            conditionAfter = last.conditionAfter,
            live = list.size == 1 && !list[0].trip.estimated,
            maxEndTime = list.maxOf { it.trip.endTime }
        )
    }

    // ─── 시즌 성적표 ───
    fun report(profile: CareerProfile, season: SeasonInfo): SeasonReport {
        val ids = season.trips.map { it.id }.toSet()
        val rs = profile.results.filter { it.trip.id in ids }
        val first = rs.firstOrNull()
        val last = rs.lastOrNull()
        val real = season.trips.filter { !it.estimated }
        val clean = real.count { it.risk.isClean }
        val km = season.trips.sumOf { (it.distanceKm ?: 0f).toDouble() }.toFloat()
        val riskTotal = real.sumOf { it.risk.total }
        val cleanPercent = if (real.isEmpty()) 100 else clean * 100 / real.size
        val ovrNow = profile.ovr
        return SeasonReport(
            season = season,
            ovrBefore = first?.ovrBefore ?: ovrNow,
            ovrAfter = last?.ovrAfter ?: ovrNow,
            statsBefore = first?.statsBefore ?: profile.stats,
            statsAfter = last?.statsAfter ?: profile.stats,
            tripCount = season.trips.size,
            km = km,
            cleanPercent = cleanPercent,
            xp = rs.sumOf { it.xpGained },
            bestTripKm = season.trips.maxOfOrNull { it.distanceKm ?: 0f } ?: 0f,
            firsts = rs.flatMap { it.logs }.filter { it.tone != Tone.BAD && !it.text.startsWith("앱이") }.map { it.text },
            riskTotal = riskTotal,
            reactions = reactions(real, cleanPercent, riskTotal, km)
        )
    }

    // 게임 속 가상 반응 (실제 사람·매체가 아님)
    private fun reactions(real: List<TripRecord>, cleanPercent: Int, riskTotal: Int, km: Float): List<Reaction> {
        val list = mutableListOf<Reaction>()
        if (riskTotal >= 3) list += Reaction("옆 주차칸 경차", "요즘 이 차 좀 거칠게 달리던데… 살살 부탁해요.")
        if (cleanPercent >= 90 && real.isNotEmpty()) list += Reaction("동네 정비소 사장님", "이 차, 운전 습관이 곱네요. 관리가 잘 돼 있어요.")
        if (real.any { TripEvent.RAIN in it.events }) list += Reaction("주차장 경비 아저씨", "비 오는 날에도 부지런히 다니네요. 와이퍼가 고생이 많겠어요.")
        if (real.any { TripEvent.PASSENGER in it.events }) list += Reaction("같은 차종 동호회 게시판", "요즘 옆자리가 자주 차던데요? 좋은 일 있나 봐요.")
        if (km >= 100f) list += Reaction("휴게소 직원", "먼 길 오셨네요. 따뜻한 거 한잔하고 가세요.")
        if (list.size < 2) list += Reaction("지나가던 신형 차", "앞으로가 기대되는 차네요.")
        return list.take(3)
    }

    // ─── 시간 도구 ───
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfMonth(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(millis)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    fun ymOf(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
    }

    fun hourOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

    fun isNight(hour: Int): Boolean = hour >= 21 || hour < 5
}
