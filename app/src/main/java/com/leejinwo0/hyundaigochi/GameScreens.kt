package com.leejinwo0.hyundaigochi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppTab(val label: String) { GARAGE("차고"), SEASON("시즌"), MYCAR("내 차"), RECORDS("기록") }

// 앱의 맨 위 화면: 처음이면 시작 과정, 주행 중이면 "기록 중"만, 정차 중이면 게임 화면
@Composable
fun GameApp(onRequestSpeed: () -> Unit) {
    val player = GameRuntime.player
    val driving = GameRuntime.isDriving || GameRuntime.uxRestricted
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ln.Canvas)
    ) {
        if (player == null) {
            if (driving) {
                DrivingScreen(CarModels.byId("avante"), Color(CarColors.all[0].argb), 1, Mood.CALM)
            } else {
                Onboarding(onFinish = { GameRuntime.createPlayer(it) })
            }
        } else {
            MainGame(player, driving, onRequestSpeed)
        }
    }
}

@Composable
private fun MainGame(player: Player, driving: Boolean, onRequestSpeed: () -> Unit) {
    val tick = GameRuntime.tick
    val allTrips = GameRuntime.trips
    val energy = GameRuntime.energyFor(player)
    val now = remember(tick, allTrips) { System.currentTimeMillis() }
    val trips = remember(allTrips, player.createdAt) { allTrips.filter { it.endTime >= player.createdAt } }
    val profile = remember(trips, player, energy, now) { CareerEngine.build(trips, now, energy, player) }
    val seasons = remember(trips, player.demoMode, now) { CareerEngine.seasons(trips, player.demoMode, now) }
    val settled = GameRuntime.settledUntil
    val lastReported = GameRuntime.lastReportedSeason
    val pending = remember(profile, settled) { CareerEngine.pending(profile, settled) }
    val due = remember(seasons, lastReported) {
        seasons.filter { it.complete && it.key > lastReported }.maxByOrNull { it.key }
    }

    var tab by remember { mutableStateOf(AppTab.GARAGE) }
    var focusDay by remember { mutableStateOf<Long?>(null) }
    var viewSeasonKey by remember { mutableStateOf<Long?>(null) }
    var detailTripId by remember { mutableStateOf<Long?>(null) }

    val model = CarModels.byId(player.modelId)
    val color = Color(CarColors.all[player.colorIndex.coerceIn(0, CarColors.all.lastIndex)].argb)
    val level = profile.stage.level

    Box(modifier = Modifier.fillMaxSize()) {
        if (driving) {
            DrivingScreen(model, color, level, profile.mood)
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                NavRail(tab) { tab = it }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Ln.Hairline)
                )
                when (tab) {
                    AppTab.GARAGE -> GarageTab(
                        player, profile, model, color, seasons, trips.isEmpty(), onRequestSpeed,
                        Modifier.weight(1f)
                    )
                    AppTab.SEASON -> SeasonTab(
                        player, profile, seasons,
                        onOpen = { viewSeasonKey = it },
                        modifier = Modifier.weight(1f)
                    )
                    AppTab.MYCAR -> MyCarTab(player, profile, model, color, Modifier.weight(1f))
                    AppTab.RECORDS -> RecordsTab(profile, trips, focusDay, onOpen = { detailTripId = it.id }, modifier = Modifier.weight(1f))
                }
            }

            // 정산(그동안 쌓인 주행) → 시즌 성적표 순서로 띄움
            val viewing = viewSeasonKey?.let { k -> seasons.firstOrNull { it.key == k } }
            val detail = detailTripId?.let { id -> trips.firstOrNull { it.id == id } }
            if (pending != null) {
                AwayOverlay(pending, model, color, level, profile.mood) { GameRuntime.settle(pending.maxEndTime) }
            } else if (due != null) {
                SeasonReportOverlay(
                    report = CareerEngine.report(profile, due),
                    nextKey = CareerEngine.nextSeasonKey(due.key, player.demoMode),
                    readOnly = false,
                    onClose = { GameRuntime.setLastReported(due.key) },
                    onPickGoal = { GameRuntime.chooseGoal(CareerEngine.nextSeasonKey(due.key, player.demoMode), it) }
                )
            } else if (viewing != null) {
                SeasonReportOverlay(
                    report = CareerEngine.report(profile, viewing),
                    nextKey = 0L,
                    readOnly = true,
                    onClose = { viewSeasonKey = null },
                    onPickGoal = {}
                )
            } else if (detail != null) {
                TripDetailOverlay(
                    t = detail,
                    r = profile.results.firstOrNull { it.trip.id == detail.id },
                    onClose = { detailTripId = null },
                    onDelete = {
                        detailTripId = null
                        GameRuntime.deleteTrip(detail.id)
                    }
                )
            }
        }
    }
}

// ─── 주행 중: 멈춰 있는 도트 차 + "기록 중"만 (긴 글·목록·움직임 없음) ───
@Composable
private fun DrivingScreen(model: CarModel, color: Color, level: Int, mood: Mood) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PixelCar(
            model, color, level,
            mood = if (mood == Mood.SLEEPY) Mood.CALM else mood,
            animate = false,
            modifier = Modifier.size(width = 420.dp, height = 280.dp)
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Ln.Primary)
            )
            Spacer(Modifier.width(16.dp))
            Text("기록 중", color = Ln.Ink, fontSize = 64.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.6).sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("정차하면 오늘 얼마나 컸는지 보여 드릴게요", color = Ln.InkSubtle, fontSize = 28.sp)
    }
}

// ─── 왼쪽 세로 탭 ───
@Composable
private fun NavRail(tab: AppTab, onTab: (AppTab) -> Unit) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 40.dp)
    ) {
        Text(
            "기름만 줘도\n잘 커요",
            color = Ln.Ink,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(40.dp))
        AppTab.values().forEach { t ->
            RailItem(t.label, t == tab) { onTab(t) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(ItemShape)
            .background(if (selected) Ln.Surface2 else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, Ln.Hairline, ItemShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            color = if (selected) Ln.Ink else Ln.InkSubtle,
            fontSize = 26.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun conditionColor(c: Int): Color = when {
    c >= 70 -> Ln.Success
    c >= 40 -> Ln.Warn
    else -> Ln.Danger
}

// ─── 차고: 캐릭터 + 레벨 + 컨디션 + 확인할 일 ───
@Composable
private fun GarageTab(
    player: Player,
    profile: CareerProfile,
    model: CarModel,
    color: Color,
    seasons: List<SeasonInfo>,
    noTrips: Boolean,
    onRequestSpeed: () -> Unit,
    modifier: Modifier
) {
    // 이번 시즌이 시작할 때보다 종합 능력치가 얼마나 바뀌었는지
    val current = seasons.firstOrNull()?.takeIf { !it.complete }
    val seasonFirst = current?.let { se ->
        val ids = se.trips.map { it.id }.toSet()
        profile.results.firstOrNull { it.trip.id in ids }
    }
    val delta = profile.ovr - (seasonFirst?.ovrBefore ?: profile.ovr)
    val seasonChanges = StatType.values().mapIndexedNotNull { i, type ->
        val g = profile.stats[i] - (seasonFirst?.statsBefore?.get(i) ?: profile.stats[i])
        if (g != 0) type to g else null
    }
    var showLadder by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 왼쪽: 캐릭터
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(CardShape)
                    .background(Ln.Surface1)
                    .border(1.dp, Ln.Hairline, CardShape)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PixelCar(model, color, profile.stage.level, profile.mood, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(12.dp))
                PlateBadge(player.plate)
            }
            Text(
                profile.line,
                color = Ln.InkMuted,
                fontSize = 28.sp,
                lineHeight = 40.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Ln.Surface3)
                    .border(1.dp, Ln.HairlineStrong, CardShape)
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            )
        }

        // 오른쪽: 수치와 설명
        Column(
            modifier = Modifier
                .width(620.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column {
                val nowYm = CareerEngine.ymOf(System.currentTimeMillis())
                val ageText = player.birthYm?.let { CarAge.text(it, nowYm) }
                Text(
                    "${player.name} · ${model.label}" + (ageText?.let { " · $it" } ?: ""),
                    color = Ln.InkSubtle,
                    fontSize = 22.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Lv.${profile.stage.level}",
                        color = Ln.OnPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(PillShape)
                            .background(Ln.Primary)
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        profile.stage.title,
                        color = Ln.Ink,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-1).sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(profile.stage.desc, color = Ln.InkSubtle, fontSize = 20.sp, lineHeight = 28.sp)
            }

            // 종합 능력치: 어떻게 계산되고, 얼마나 변했고, 무엇이 차지하는지
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("종합 능력치", color = Ln.InkSubtle, fontSize = 20.sp)
                        Text("${profile.ovr}", color = Ln.Ink, fontSize = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.6).sp)
                    }
                    Text(
                        when {
                            delta > 0 -> "이번 시즌 +$delta"
                            delta < 0 -> "이번 시즌 $delta"
                            else -> "이번 시즌 변화 없음"
                        },
                        color = when {
                            delta > 0 -> Ln.Success
                            delta < 0 -> Ln.Danger
                            else -> Ln.InkSubtle
                        },
                        fontSize = 22.sp
                    )
                }
                Text(
                    "종합 능력치는 지구력·성실함·적응력·사교성·모험심 다섯 개의 평균이에요. 안전은 99에서 깎인 만큼 종합에서 빠져요. 능력치가 1점 오르면 종합은 0.2점 올라요.",
                    color = Ln.InkSubtle,
                    fontSize = 19.sp,
                    lineHeight = 27.sp
                )
                if (seasonChanges.isNotEmpty()) {
                    Text("이번 시즌에 바뀐 능력치", color = Ln.InkMuted, fontSize = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        seasonChanges.forEach { (type, g) ->
                            Badge("${type.label} ${if (g > 0) "+$g" else "$g"}", danger = g < 0)
                        }
                    }
                }
                Text("종합 능력치에서 차지하는 비율", color = Ln.InkMuted, fontSize = 20.sp)
                val growSum = profile.stats.take(5).sum()
                StatType.values().filter { it != StatType.SAFETY }.forEach { type ->
                    val v = profile.stats[type.ordinal]
                    Gauge(
                        label = type.label,
                        valueText = "${v}점 · ${if (growSum == 0) 0 else v * 100 / growSum}%",
                        fraction = v / 100f,
                        color = if (type in player.focus) Ln.Primary else Ln.InkSubtle,
                        barHeight = 8
                    )
                }
                val lost = 99 - profile.stats[5]
                Text(
                    "안전 ${profile.stats[5]}점 · " +
                        if (lost > 0) "깎인 ${lost}점만큼 종합에서 빠졌어요" else "깎이지 않아서 종합에서 빠진 건 없어요",
                    color = if (lost > 0) Ln.Danger else Ln.InkSubtle,
                    fontSize = 19.sp
                )
            }

            // 다음 단계: 무엇이 필요하고 얼마나 남았는지
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                val next = profile.nextStage
                if (next != null) {
                    Text("다음 단계 · Lv.${next.level}", color = Ln.InkSubtle, fontSize = 20.sp)
                    Text(next.title, color = Ln.Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(next.desc, color = Ln.InkMuted, fontSize = 20.sp, lineHeight = 28.sp)
                    Text(
                        "승급하면 · " + next.reward.ifBlank { "칭호가 올라가요" },
                        color = Ln.PrimaryHover,
                        fontSize = 20.sp,
                        lineHeight = 28.sp
                    )
                    Gauge(
                        label = "경험치",
                        valueText = "${profile.xp} / ${next.minXp} XP",
                        fraction = profile.progress,
                        color = Ln.Primary,
                        barHeight = 16
                    )
                    Text(
                        "남은 경험치 ${next.minXp - profile.xp} XP · 최근 주행 평균 ${profile.avgXp} XP → 약 ${profile.tripsToNext ?: 0}번 더 달리면 승급해요",
                        color = Ln.InkMuted,
                        fontSize = 20.sp,
                        lineHeight = 28.sp
                    )
                } else {
                    Text("최고 단계예요. 함께 달린 모든 길이 기록으로 남아 있어요.", color = Ln.InkMuted, fontSize = 22.sp)
                }
                Text("경험치(XP)는 이렇게 쌓여요", color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                xpRules.forEach { Text("· $it", color = Ln.InkSubtle, fontSize = 19.sp, lineHeight = 26.sp) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLadder = !showLadder },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("전체 단계 보기 (${CareerEngine.stages.size}단계)", color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (showLadder) "접기" else "펼치기", color = Ln.PrimaryHover, fontSize = 20.sp)
                }
                if (showLadder) {
                    CareerEngine.stages.forEach { st ->
                        StageRow(st, current = st.level == profile.stage.level, reached = st.level <= profile.stage.level)
                    }
                }
            }

            // 컨디션
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Gauge(
                    label = "컨디션",
                    valueText = "${profile.condition}",
                    fraction = profile.condition / 100f,
                    color = conditionColor(profile.condition),
                    barHeight = 16
                )
                Text(
                    "위험 운전을 하면 떨어지고, 차분하게 달리면 +5, 쉬면 1시간에 +2씩 회복돼요. 40 아래면 경험치와 성장이 절반이에요.",
                    color = Ln.InkSubtle,
                    fontSize = 19.sp,
                    lineHeight = 27.sp
                )
            }

            Text("확인할 일", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (noTrips) TodoRow("첫 주행을 해 볼까요", "기어를 D로 바꾸면 기록이 시작돼요", true, null)
                if (!GameRuntime.speedGranted) {
                    TodoRow("급가속·급감속 감지 켜기", "차속 권한을 허용하면 위험 운전이 안전 능력치에 반영돼요", true, onRequestSpeed)
                }
                val energyNow = GameRuntime.energyFor(player)
                val fuelType = player.fuel
                if (energyNow != null && energyNow < 20) {
                    val word = fuelType?.energyWord ?: "배터리"
                    val refill = fuelType?.refill ?: "충전"
                    TodoRow("${word}가 ${energyNow}%예요", "${refill}하러 가면 차가 기뻐해요", true, null)
                }
                player.birthYm?.let { bym ->
                    val nym = CareerEngine.ymOf(System.currentTimeMillis())
                    if (CarAge.isBirthdayMonth(bym, nym)) {
                        TodoRow("이번 달은 우리 차 ${CarAge.nth(bym, nym)}번째 생일이에요", "출고 ${bym / 100}년 ${bym % 100}월", true, null)
                    }
                }
                if (profile.condition < 40) {
                    TodoRow("컨디션이 낮아요", "차분히 달리고 쉬면 회복돼요. 지금은 성장이 절반이에요", true, null)
                }
                TodoRow("집중 육성 중", player.focus.joinToString(", ") { it.label }, false, null)
                if (player.demoMode) {
                    val done = current?.trips?.count { !it.estimated } ?: 0
                    TodoRow("시연 모드", "이번 시즌 $done / $DEMO_SEASON_TRIPS 회 주행", false, null)
                } else {
                    TodoRow("이번 시즌", current?.label ?: "새 시즌이 곧 시작돼요", false, null)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MiniStat("함께 달린 거리", "${"%.1f".format(profile.totalKm)} km", Modifier.weight(1f))
                MiniStat("주행 횟수", "${profile.tripCount}회", Modifier.weight(1f))
            }
        }
    }
}

private fun signed(n: Int): String = if (n > 0) "+$n" else "$n"

// 성장 규칙 설명 (차고, 내 차, 주행 기록에서 같이 씀)
private val xpRules = listOf(
    "주행 1회 기본 10 XP",
    "달린 거리 1km당 1 XP",
    "겪은 일(비·밤길·히터·에어컨·동승자·창문) 한 가지당 5 XP",
    "위험 운전 없이 달리면 5 XP 보너스",
    "컨디션이 40 아래면 XP와 성장이 절반"
)

private val overallRules = listOf(
    "지구력·성실함·적응력·사교성·모험심 다섯 개의 평균이에요",
    "안전은 99에서 깎인 만큼 종합에서 빠져요",
    "능력치가 1점 오르면 종합은 0.2점 올라요. 새 차는 0에서 시작해요"
)

private val statRules = listOf(
    "지구력" to "10km 달릴 때마다 +1",
    "성실함" to "주행 1회 +0.5 · 그날 첫 주행이면 +1 더",
    "적응력" to "비 +1.5 · 밤길 +1 · 히터 +1 · 에어컨 +1 (한 번에 최대 3.5)",
    "사교성" to "동승자가 타면 +2",
    "모험심" to "50km 이상 +2 · 100km 이상 +3 · 창문 열고 달리면 +0.5",
    "안전" to "깨끗한 주행 +1.5 (최대 99) · 급가속·급감속 1회 −3 · 벨트 미착용 −8 · 2시간 넘는 연속 운전 −4"
)

private val bonusRules = listOf(
    "집중 육성으로 고른 능력치는 ×1.5",
    "용도에 맞는 능력치 두 개는 ×1.2",
    "시즌 목표로 고른 능력치는 ×1.3 (안전은 ×1.5)",
    "컨디션이 40 아래면 오르는 값이 ×0.5 (위험 운전으로 깎이는 값에는 적용되지 않아요)"
)

@Composable
private fun StageRow(st: Stage, current: Boolean, reached: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(if (current) Ln.PrimarySoft else Color.Transparent)
            .then(if (current) Modifier.border(1.dp, Ln.Primary, ItemShape) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Lv.${st.level}",
                color = if (reached) Ln.Primary else Ln.InkTertiary,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(70.dp)
            )
            Text(
                st.title,
                color = if (reached) Ln.Ink else Ln.InkSubtle,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text("${st.minXp} XP", color = Ln.InkTertiary, fontSize = 18.sp)
        }
        Text(
            if (st.reward.isBlank()) "칭호가 올라가요" else "보상 · ${st.reward}",
            color = Ln.InkSubtle,
            fontSize = 18.sp,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun GrowthRules() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("종합 능력치", color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        overallRules.forEach { Text("· $it", color = Ln.InkSubtle, fontSize = 19.sp, lineHeight = 26.sp) }
        Spacer(Modifier.height(4.dp))
        Text("능력치는 이렇게 변해요", color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        statRules.forEach { (name, rule) ->
            Row(verticalAlignment = Alignment.Top) {
                Text(name, color = Ln.InkSubtle, fontSize = 19.sp, modifier = Modifier.width(100.dp))
                Text(rule, color = Ln.InkMuted, fontSize = 19.sp, lineHeight = 26.sp, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("보너스와 보정", color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        bonusRules.forEach { Text("· $it", color = Ln.InkSubtle, fontSize = 19.sp, lineHeight = 26.sp) }
        Spacer(Modifier.height(4.dp))
        Text("경험치(XP)", color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        xpRules.forEach { Text("· $it", color = Ln.InkSubtle, fontSize = 19.sp, lineHeight = 26.sp) }
    }
}

@Composable
private fun TodoRow(title: String, sub: String, accent: Boolean, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(Ln.Surface1)
            .border(1.dp, if (accent) Ln.Primary else Ln.Hairline, ItemShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (accent) Ln.Primary else Ln.InkTertiary)
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Ln.InkSubtle, fontSize = 19.sp)
        }
        if (onClick != null) Text("›", color = Ln.InkSubtle, fontSize = 32.sp)
    }
}

// ─── 시즌: 이번 시즌 진행 + 지난 시즌 성적표 ───
@Composable
private fun SeasonTab(
    player: Player,
    profile: CareerProfile,
    seasons: List<SeasonInfo>,
    onOpen: (Long) -> Unit,
    modifier: Modifier
) {
    val current = seasons.firstOrNull()?.takeIf { !it.complete }
    val finished = seasons.filter { it.complete }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("시즌", color = Ln.Ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)

        SectionCard(modifier = Modifier.fillMaxWidth()) {
            val label = current?.label ?: "새 시즌이 곧 시작돼요"
            Text(label, color = Ln.InkSubtle, fontSize = 22.sp)
            if (current != null) {
                val real = current.trips.filter { !it.estimated }
                val clean = if (real.isEmpty()) 100 else real.count { it.risk.isClean } * 100 / real.size
                val ids = current.trips.map { it.id }.toSet()
                val xp = profile.results.filter { it.trip.id in ids }.sumOf { it.xpGained }
                val km = current.trips.sumOf { (it.distanceKm ?: 0f).toDouble() }.toFloat()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat("주행", "${current.trips.size}회", Modifier.weight(1f))
                    MiniStat("달린 거리", "${"%.1f".format(km)} km", Modifier.weight(1f))
                    MiniStat("안전 주행", "$clean%", Modifier.weight(1f))
                    MiniStat("얻은 경험치", "$xp XP", Modifier.weight(1f))
                }
                if (player.demoMode) {
                    val done = real.size
                    Gauge("시즌 진행", "$done / $DEMO_SEASON_TRIPS 회", done / DEMO_SEASON_TRIPS.toFloat(), Ln.Primary, 12)
                }
                val goal = player.goals[current.key]
                Text(
                    if (goal != null) "시즌 목표: ${goal.label} (${goal.desc})" else "시즌 목표: 아직 없어요. 시즌이 끝나면 다음 시즌 목표를 고를 수 있어요.",
                    color = Ln.InkMuted,
                    fontSize = 22.sp
                )
            } else {
                Text(
                    "기어를 D로 바꿔 달리면 새 시즌이 시작돼요.",
                    color = Ln.InkMuted,
                    fontSize = 24.sp
                )
            }
        }

        Text("지난 시즌", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        if (finished.isEmpty()) {
            Text("끝난 시즌이 아직 없어요.", color = Ln.InkSubtle, fontSize = 24.sp)
        } else {
            finished.forEach { s ->
                val km = s.trips.sumOf { (it.distanceKm ?: 0f).toDouble() }.toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .clip(ItemShape)
                        .background(Ln.Surface1)
                        .border(1.dp, Ln.Hairline, ItemShape)
                        .clickable { onOpen(s.key) }
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(s.label, color = Ln.Ink, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        Text("${s.trips.size}회 · ${"%.1f".format(km)}km", color = Ln.InkSubtle, fontSize = 20.sp)
                    }
                    Text("성적표 보기 ›", color = Ln.PrimaryHover, fontSize = 22.sp)
                }
            }
        }
    }
}

// ─── 내 차: 능력치 + 설정 ───
@Composable
private fun MyCarTab(player: Player, profile: CareerProfile, model: CarModel, color: Color, modifier: Modifier) {
    var confirmReset by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var plateEdit by remember(player.plate) { mutableStateOf(player.plate) }

    Row(
        modifier = modifier
            .fillMaxHeight()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 왼쪽: 차 정보 + 능력치
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Ln.Surface1)
                    .border(1.dp, Ln.Hairline, CardShape)
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelCar(model, color, profile.stage.level, Mood.CALM, animate = false, modifier = Modifier.size(width = 240.dp, height = 160.dp))
                Spacer(Modifier.width(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(player.name, color = Ln.Ink, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                    Text(
                        "${model.label} · ${CarColors.all[player.colorIndex.coerceIn(0, CarColors.all.lastIndex)].name}",
                        color = Ln.InkSubtle, fontSize = 22.sp
                    )
                    Text(
                        "${player.usageLabel} · ${player.usageBoosts.joinToString(", ") { it.label }} ×1.2",
                        color = Ln.InkSubtle, fontSize = 22.sp, lineHeight = 30.sp
                    )
                    player.birthYm?.let { bym ->
                        Text(
                            "출고 ${bym / 100}년 ${bym % 100}월 · ${CarAge.text(bym, CareerEngine.ymOf(System.currentTimeMillis()))}",
                            color = Ln.InkSubtle, fontSize = 22.sp
                        )
                    }
                    player.fuel?.let { Text("연료 ${it.label} · ${it.refill}이 먹이예요", color = Ln.InkSubtle, fontSize = 22.sp) }
                    PlateBadge(player.plate)
                }
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("능력치", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                StatType.values().forEachIndexed { i, type ->
                    val focus = type in player.focus
                    val g = profile.growth[i]
                    Gauge(
                        label = type.label + if (focus) " ★" else "",
                        sub = type.hint,
                        valueText = "${profile.stats[i]}",
                        fraction = profile.stats[i] / 100f,
                        color = if (focus) Ln.Primary else Ln.InkSubtle,
                        barHeight = 12,
                        extra = if (g > 0) "+$g" else if (g < 0) "$g" else null,
                        extraColor = if (g < 0) Ln.Danger else Ln.Success
                    )
                }
                Text("★ 집중 육성 (1.5배 빨리 자라요) · 파란색 막대 · 오른쪽 숫자는 시작할 때보다 바뀐 값이에요", color = Ln.InkTertiary, fontSize = 18.sp)
            }

            val realTrips = profile.results.map { it.trip }.filter { !it.estimated }.sortedBy { it.startTime }
            if (realTrips.isNotEmpty()) {
                val days = realTrips.map { CareerEngine.startOfDay(it.startTime) }.distinct().size
                val totalMin = realTrips.sumOf { it.minutes }
                val longest = realTrips.maxOf { it.distanceKm ?: 0f }
                var best = 0
                var streak = 0
                realTrips.forEach {
                    if (it.risk.isClean) {
                        streak++
                        if (streak > best) best = streak
                    } else {
                        streak = 0
                    }
                }
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("내 차 기록", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    InfoRow("처음 달린 날", fmtDate("yyyy년 M월 d일", realTrips.first().startTime))
                    InfoRow("함께한 날", "${days}일")
                    InfoRow("총 주행 시간", fmtMinutes(totalMin))
                    InfoRow("가장 멀리 간 주행", "${"%.1f".format(longest)} km")
                    InfoRow("연속 안전 주행", "지금 ${streak}회 · 최고 ${best}회")
                }
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRules = !showRules },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("성장 규칙", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (showRules) "접기" else "펼치기", color = Ln.PrimaryHover, fontSize = 20.sp)
                }
                if (showRules) GrowthRules()
            }
        }

        // 오른쪽: 설정
        Column(
            modifier = Modifier
                .width(620.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("설정", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("시연 모드", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                        Text("주행 3회마다 시즌이 끝나요 (평소엔 한 달)", color = Ln.InkSubtle, fontSize = 19.sp)
                    }
                    Switch(checked = player.demoMode, onCheckedChange = { GameRuntime.setDemoMode(it) })
                }
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("번호판", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text("이 기기 안에만 저장돼요. 지우면 번호판 없이 지내요.", color = Ln.InkSubtle, fontSize = 19.sp)
                PlateField(plateEdit) { plateEdit = it }
                BigButton(
                    "번호판 저장",
                    enabled = Plate.isValid(plateEdit) && plateEdit != player.plate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GameRuntime.updatePlayer(player.copy(plate = plateEdit))
                }
            }

            // 출고 연월과 연료 종류 (나중에 고칠 수 있어요)
            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("차 정보", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                var year by remember(player.birthYm) { mutableStateOf(player.birthYm?.let { it / 100 }) }
                var month by remember(player.birthYm) { mutableStateOf(player.birthYm?.let { it % 100 }) }
                var fuelSel by remember(player.fuel) { mutableStateOf(player.fuel) }
                val y = year
                val m = month
                val ym: Int? = if (y != null && m != null) CarAge.ym(y, m) else null
                val ok = (y == null && m == null) ||
                    (ym != null && CarAge.isValid(ym, CareerEngine.ymOf(System.currentTimeMillis())))
                CarInfoFields(y, m, fuelSel, ok, { year = it }, { month = it }, { fuelSel = it })
                BigButton(
                    "차 정보 저장",
                    enabled = ok && (ym != player.birthYm || fuelSel != player.fuel),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GameRuntime.updatePlayer(player.copy(birthYm = ym, fuel = fuelSel))
                }
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Text("내 데이터", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text(
                    "주행 기록과 번호판은 이 기기 안에만 저장돼요. 아래 버튼으로 모두 지우고 처음부터 시작할 수 있어요.",
                    color = Ln.InkSubtle, fontSize = 19.sp
                )
                BigButton("내 데이터 모두 지우기", primary = false, modifier = Modifier.fillMaxWidth()) { confirmReset = true }
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStatus = !showStatus },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("차량 연결 상태", color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (showStatus) "접기" else "펼치기", color = Ln.PrimaryHover, fontSize = 20.sp)
                }
                if (showStatus) {
                    StatusLine("기어", GameRuntime.gear)
                    StatusLine("누적 주행거리", GameRuntime.odometer?.let { "$it km" } ?: "-")
                    StatusLine("차속 권한", if (GameRuntime.speedGranted) "허용" else "없음")
                    GameRuntime.status.entries.sortedBy { it.key }.forEach { StatusLine(it.key, it.value) }
                }
            }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "내 데이터를 모두 지울까요?",
            text = "주행 기록, 레벨, 능력치, 번호판이 모두 사라지고 처음 시작 화면으로 돌아가요. 되돌릴 수 없어요.",
            confirmLabel = "모두 지우기",
            onConfirm = {
                confirmReset = false
                GameRuntime.resetAll()
            },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun StatusLine(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(name, color = Ln.InkSubtle, fontSize = 18.sp, modifier = Modifier.width(150.dp))
        Text(
            value,
            color = if (value.startsWith("✕")) Ln.Danger else Ln.InkMuted,
            fontSize = 18.sp
        )
    }
}

// ─── 기록: 주행 목록 ───
@Composable
private fun RecordsTab(
    profile: CareerProfile,
    trips: List<TripRecord>,
    focusDay: Long?,
    onOpen: (TripRecord) -> Unit,
    modifier: Modifier
) {
    var confirmAll by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val resultById = remember(profile) { profile.results.associateBy { it.trip.id } }

    // 날짜 머리줄(Long)과 주행(TripRecord)을 한 줄로 펼친 목록
    val rows: List<Any> = remember(trips) {
        buildList {
            trips.groupBy { CareerEngine.startOfDay(it.startTime) }
                .toSortedMap(reverseOrder())
                .forEach { (day, list) ->
                    add(day)
                    addAll(list.sortedByDescending { it.startTime })
                }
        }
    }
    LaunchedEffect(focusDay, rows) {
        val index = rows.indexOfFirst { it is Long && it == focusDay }
        if (index >= 0) listState.scrollToItem(index)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 40.dp, end = 40.dp, top = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("주행 기록", color = Ln.Ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showRules = !showRules }, modifier = Modifier.heightIn(min = 76.dp)) {
                Text(if (showRules) "계산 방법 접기" else "계산 방법 보기", color = Ln.PrimaryHover, fontSize = 22.sp)
            }
            if (trips.isNotEmpty()) {
                TextButton(onClick = { confirmAll = true }, modifier = Modifier.heightIn(min = 76.dp)) {
                    Text("기록 모두 지우기", color = Ln.InkSubtle, fontSize = 22.sp)
                }
            }
        }
        if (showRules) {
            SectionCard(modifier = Modifier.fillMaxWidth()) { GrowthRules() }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "줄을 누르면 그 주행에서 경험치와 능력치가 얼마나, 왜 바뀌었는지 볼 수 있어요.",
            color = Ln.InkSubtle,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        if (trips.isEmpty()) {
            Text(
                "아직 주행 기록이 없어요. 기어를 D로 바꾸면 기록이 시작되고, P로 돌아오면 여기에 한 줄씩 쌓여요.",
                color = Ln.InkSubtle, fontSize = 26.sp, lineHeight = 40.sp
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                items(rows, key = { row ->
                    if (row is Long) "day-$row" else "trip-${(row as TripRecord).id}"
                }) { row ->
                    when (row) {
                        is Long -> Text(
                            fmtDate("M월 d일 EEEE", row),
                            color = Ln.InkSubtle, fontSize = 22.sp,
                            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
                        )
                        is TripRecord -> TripRow(row, resultById[row.id]) { onOpen(row) }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            title = "주행 기록을 모두 지울까요?",
            text = "레벨과 능력치도 처음부터 다시 계산돼요. 되돌릴 수 없어요.",
            confirmLabel = "모두 지우기",
            onConfirm = {
                confirmAll = false
                GameRuntime.clearTrips()
            },
            onDismiss = { confirmAll = false }
        )
    }
}

@Composable
private fun TripRow(t: TripRecord, r: TripResult?, onOpen: () -> Unit) {
    val ovrDelta = r?.let { it.ovrAfter - it.ovrBefore } ?: 0
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clickable(onClick = onOpen)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(290.dp)) {
                if (t.estimated) {
                    Text("기록 없는 주행", color = Ln.Ink, fontSize = 24.sp)
                    Text("앱이 꺼져 있던 동안", color = Ln.InkSubtle, fontSize = 18.sp)
                } else {
                    Text(fmtDate("M월 d일 E", t.startTime), color = Ln.InkSubtle, fontSize = 18.sp)
                    Text(
                        "${fmtDate("a h:mm", t.startTime)} → ${fmtDate("a h:mm", t.endTime)}",
                        color = Ln.Ink, fontSize = 24.sp
                    )
                }
            }
            Text(
                t.distanceKm?.let { "${"%.1f".format(it)} km" } ?: "-",
                color = Ln.InkMuted, fontSize = 24.sp, modifier = Modifier.width(130.dp)
            )
            Text("+${r?.xpGained ?: 0} XP", color = Ln.PrimaryHover, fontSize = 24.sp, modifier = Modifier.width(120.dp))
            Text(
                when {
                    ovrDelta > 0 -> "종합 +$ovrDelta"
                    ovrDelta < 0 -> "종합 $ovrDelta"
                    else -> "종합 ±0"
                },
                color = when {
                    ovrDelta > 0 -> Ln.Success
                    ovrDelta < 0 -> Ln.Danger
                    else -> Ln.InkSubtle
                },
                fontSize = 22.sp,
                modifier = Modifier.width(120.dp)
            )
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.events.sortedBy { it.ordinal }.forEach { Badge(it.label) }
                riskBadges(t.risk).forEach { Badge(it, danger = true) }
            }
            Text("자세히 ›", color = Ln.InkSubtle, fontSize = 20.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Ln.Hairline)
        )
    }
}

// 주행 한 번의 계산 내역: 경험치 합계, 능력치별 변화와 이유
@Composable
private fun TripDetailOverlay(t: TripRecord, r: TripResult?, onClose: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember(t.id) { mutableStateOf(false) }
    Scrim {
        Column(
            modifier = Modifier
                .widthIn(max = 1000.dp)
                .fillMaxHeight(0.92f)
                .clip(CardShape)
                .background(Ln.Surface1)
                .border(1.dp, Ln.HairlineStrong, CardShape)
                .verticalScroll(rememberScrollState())
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                if (t.estimated) "기록 없는 주행" else "${fmtDate("M월 d일 EEEE a h:mm", t.startTime)} 출발",
                color = Ln.InkSubtle, fontSize = 24.sp
            )
            Text(
                "${t.distanceKm?.let { "%.1f".format(it) } ?: "-"} km · ${fmtMinutes(t.minutes)}",
                color = Ln.Ink, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.events.sortedBy { it.ordinal }.forEach { Badge(it.label) }
                riskBadges(t.risk).forEach { Badge(it, danger = true) }
            }

            if (r != null) {
                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("경험치", color = Ln.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    val x = r.xp
                    if (t.estimated) {
                        Text("앱이 꺼져 있던 동안의 주행이라 달린 거리만 경험치로 반영돼요.", color = Ln.InkSubtle, fontSize = 20.sp)
                        InfoRow("달린 거리 (1km당 1)", "+${x.distance}")
                    } else {
                        InfoRow("기본", "+${x.base}")
                        InfoRow("달린 거리 (1km당 1)", "+${x.distance}")
                        InfoRow("겪은 일 ${t.events.size}가지 (한 가지당 5)", "+${x.experiences}")
                        InfoRow("깨끗한 주행 보너스", if (x.clean > 0) "+${x.clean}" else "없음")
                    }
                    InfoRow("합계", "${x.total} XP")
                    if (x.halved) {
                        Text(
                            "컨디션이 40 아래라 절반만 받았어요 → ${r.xpGained} XP",
                            color = Ln.Danger, fontSize = 20.sp
                        )
                    }
                }

                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("능력치 변화", color = Ln.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    if (r.changes.isEmpty()) {
                        Text("이번 주행으로 바뀐 능력치는 없어요.", color = Ln.InkSubtle, fontSize = 20.sp)
                    }
                    r.changes.forEach { c ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.type.label, color = Ln.Ink, fontSize = 22.sp, modifier = Modifier.weight(1f))
                                Text(
                                    "%+.1f".format(c.delta),
                                    color = if (c.delta < 0f) Ln.Danger else if (c.capped) Ln.InkSubtle else Ln.Success,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                c.reasons.joinToString("  ·  ") + if (c.capped) "  ·  상한 99에 닿아 더 오르지 않아요" else "",
                                color = Ln.InkSubtle,
                                fontSize = 18.sp,
                                lineHeight = 25.sp
                            )
                        }
                    }
                    Text(
                        "능력치는 소수점으로 쌓이다가 화면에는 반올림해서 보여요.",
                        color = Ln.InkTertiary,
                        fontSize = 17.sp
                    )
                }

                SectionCard(modifier = Modifier.fillMaxWidth()) {
                    InfoRow("종합 능력치", "${r.ovrBefore} → ${r.ovrAfter}")
                    InfoRow("컨디션", "${r.conditionBefore} → ${r.conditionAfter}")
                    r.logs.forEach { log ->
                        Text(
                            log.text,
                            color = if (log.tone == Tone.BAD) Ln.Danger else Ln.InkMuted,
                            fontSize = 20.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("닫기", modifier = Modifier.weight(1f), onClick = onClose)
                BigButton("이 기록 지우기", primary = false, modifier = Modifier.weight(1f)) { confirm = true }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "이 주행 기록을 지울까요?",
            text = "이 기록으로 얻은 경험치와 능력치도 빠져요.",
            confirmLabel = "지우기",
            onConfirm = {
                confirm = false
                onDelete()
            },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ln.InkSubtle, fontSize = 21.sp, modifier = Modifier.weight(1f))
        Text(value, color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

private fun riskBadges(r: RiskCounts): List<String> = listOfNotNull(
    if (r.harshAccel > 0) "급가속 ${r.harshAccel}" else null,
    if (r.harshBrake > 0) "급감속 ${r.harshBrake}" else null,
    if (r.unbuckled) "벨트 미착용" else null,
    if (r.longDrive) "장시간 운전" else null
)

// ─── 앱을 열었을 때 한꺼번에 보여 주는 정산 (정차 중에만) ───
@Composable
private fun AwayOverlay(
    s: AwaySummary,
    model: CarModel,
    color: Color,
    level: Int,
    mood: Mood,
    onConfirm: () -> Unit
) {
    Scrim {
        Row(
            modifier = Modifier
                .widthIn(max = 1300.dp)
                .clip(CardShape)
                .background(Ln.Surface1)
                .border(1.dp, Ln.HairlineStrong, CardShape)
                .padding(40.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PixelCar(model, color, level, mood, modifier = Modifier.size(width = 380.dp, height = 253.dp))
            Column(modifier = Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    s.levelUp?.let { "Lv.${it.level} 승급! ${it.title}" }
                        ?: if (s.live) "도착! 오늘도 한 뼘 컸어요" else "그동안 ${s.tripCount}번 달렸어요",
                    color = Ln.Ink, fontSize = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp
                )
                s.levelUp?.reward?.takeIf { it.isNotBlank() }?.let {
                    Text("승급 보상 · $it", color = Ln.PrimaryHover, fontSize = 22.sp, lineHeight = 30.sp)
                }
                if (!s.live && s.estimatedCount > 0) {
                    Text(
                        "앱이 꺼져 있던 동안의 주행거리도 함께 반영했어요.",
                        color = Ln.InkSubtle, fontSize = 20.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MiniStat("달린 거리", "${"%.1f".format(s.km)} km", Modifier.weight(1f))
                    MiniStat("얻은 경험치", "+${s.xpGained} XP", Modifier.weight(1f))
                    MiniStat("컨디션", "${s.conditionBefore} → ${s.conditionAfter}", Modifier.weight(1f))
                }
                if (s.statGains.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        s.statGains.forEach { (type, g) -> Badge("${type.label} ${if (g > 0) "+$g" else "$g"}", danger = g < 0) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.logs.take(7).forEach { log ->
                        Text(
                            log.text,
                            color = if (log.tone == Tone.BAD) Ln.Danger else Ln.InkMuted,
                            fontSize = 21.sp
                        )
                    }
                    if (s.logs.size > 7) Text("…외 ${s.logs.size - 7}개", color = Ln.InkSubtle, fontSize = 19.sp)
                }
                BigButton("확인", modifier = Modifier.fillMaxWidth(), onClick = onConfirm)
            }
        }
    }
}

// ─── 시즌 성적표 ───
@Composable
private fun SeasonReportOverlay(
    report: SeasonReport,
    nextKey: Long,
    readOnly: Boolean,
    onClose: () -> Unit,
    onPickGoal: (SeasonGoal) -> Unit
) {
    var goal by remember(report.season.key) { mutableStateOf<SeasonGoal?>(null) }
    Scrim {
        Column(
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxHeight(0.92f)
                .clip(CardShape)
                .background(Ln.Surface1)
                .border(1.dp, Ln.HairlineStrong, CardShape)
                .verticalScroll(rememberScrollState())
                .padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("${report.season.label}이 끝났어요", color = Ln.InkSubtle, fontSize = 24.sp)
            Text(
                "종합 능력치가 ${report.ovrAfter}이 됐어요",
                color = Ln.Ink, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MiniStat("주행", "${report.tripCount}회", Modifier.weight(1f))
                MiniStat("달린 거리", "${"%.1f".format(report.km)} km", Modifier.weight(1f))
                MiniStat("안전 주행", "${report.cleanPercent}%", Modifier.weight(1f))
                MiniStat("얻은 경험치", "${report.xp} XP", Modifier.weight(1f))
            }

            SectionCard(modifier = Modifier.fillMaxWidth()) {
                Row {
                    Text("종합 능력치", color = Ln.InkSubtle, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    Text(
                        "${report.ovrBefore} → ${report.ovrAfter} (${signed(report.ovrAfter - report.ovrBefore)})",
                        color = Ln.Ink, fontSize = 22.sp
                    )
                }
                StatType.values().forEachIndexed { i, type ->
                    val g = report.statsAfter[i] - report.statsBefore[i]
                    Row {
                        Text(type.label, color = Ln.InkSubtle, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        Text("${report.statsBefore[i]} → ${report.statsAfter[i]}", color = Ln.InkMuted, fontSize = 22.sp)
                        Text(
                            if (g > 0) "  +$g" else if (g < 0) "  $g" else "",
                            color = if (g < 0) Ln.Danger else Ln.Success, fontSize = 22.sp
                        )
                    }
                }
            }

            if (report.firsts.isNotEmpty()) {
                Text("이번 시즌 기록", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                report.firsts.take(6).forEach { Text("· $it", color = Ln.InkMuted, fontSize = 22.sp) }
            }
            if (report.riskTotal > 0) {
                Text("위험 운전 ${report.riskTotal}회가 기록됐어요. 다음 시즌엔 차분하게 달려 봐요.", color = Ln.Danger, fontSize = 22.sp)
            }

            Text("도로 위 반응", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            report.reactions.forEach { r ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ItemShape)
                        .background(Ln.Surface2)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(r.who, color = Ln.InkSubtle, fontSize = 19.sp)
                    Text(r.text, color = Ln.Ink, fontSize = 24.sp)
                }
            }
            Text("게임 속 가상 반응이에요. 실제 사람이나 회사의 말이 아니에요.", color = Ln.InkTertiary, fontSize = 17.sp)

            if (!readOnly) {
                Text("다음 시즌 목표", color = Ln.Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SeasonGoal.values().forEach { g ->
                        val on = goal == g
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(ItemShape)
                                .background(Ln.Surface2)
                                .border(2.dp, if (on) Ln.Primary else Ln.Hairline, ItemShape)
                                .clickable { goal = g }
                                .padding(20.dp)
                        ) {
                            Text(g.label, color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            Text(g.desc, color = Ln.InkSubtle, fontSize = 18.sp)
                        }
                    }
                }
            }
            BigButton(
                if (readOnly) "닫기" else if (goal != null) "다음 시즌 시작" else "목표 없이 다음 시즌 시작",
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!readOnly) goal?.let { onPickGoal(it) }
                onClose()
            }
        }
    }
}

// 뒤 화면이 눌리지 않게 막는 반투명 배경
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
