package com.leejinwo0.hyundaigochi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

// 처음 시작할 때 한 번 거치는 과정 (정차 중에만 보임)
// 1 모습 고르기 → 2 별명·번호판·용도(여러 개) → 3 내 차 소개(출고 연월·연료, 선택) → 4 집중 육성 → 요약
@Composable
fun Onboarding(onFinish: (Player) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var modelId by remember { mutableStateOf("avante") }
    var colorIndex by remember { mutableStateOf(0) } // 기본은 블랙
    var name by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var usages by remember { mutableStateOf(setOf<Usage>()) }
    var birthYear by remember { mutableStateOf<Int?>(null) }
    var birthMonth by remember { mutableStateOf<Int?>(null) }
    var fuel by remember { mutableStateOf<FuelType?>(null) }
    var focus by remember { mutableStateOf(setOf<StatType>()) }
    var showSummary by remember { mutableStateOf(false) }

    val model = CarModels.byId(modelId)
    val color = Color(CarColors.all[colorIndex].argb)
    val nowYm = CareerEngine.ymOf(System.currentTimeMillis())
    val by = birthYear
    val bm = birthMonth
    val birthYm: Int? = if (by != null && bm != null) CarAge.ym(by, bm) else null
    // 출고 연월은 둘 다 고르거나 둘 다 비워 두어야 하고, 미래면 안 됨
    val birthOk = (by == null && bm == null) || (birthYm != null && CarAge.isValid(birthYm, nowYm))

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        // 왼쪽: 내 차 미리보기
        Column(
            modifier = Modifier
                .width(520.dp)
                .fillMaxHeight()
                .clip(CardShape)
                .background(Ln.Surface1)
                .border(1.dp, Ln.Hairline, CardShape)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PixelCar(model, color, level = 1, mood = Mood.CALM, modifier = Modifier.size(width = 420.dp, height = 280.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (name.isEmpty()) model.label else name,
                color = Ln.Ink,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.8).sp
            )
            Text("${model.label} · ${model.kind}", color = Ln.InkSubtle, fontSize = 22.sp)
            if (Plate.isComplete(plate)) {
                Spacer(Modifier.height(16.dp))
                PlateBadge(plate)
            }
        }

        // 오른쪽: 단계별 선택
        Column(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 1) {
                    TextButton(onClick = { step -= 1 }, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text("← 이전", color = Ln.InkMuted, fontSize = 24.sp)
                    }
                } else {
                    Spacer(Modifier.width(120.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(width = 48.dp, height = 8.dp)
                                .clip(PillShape)
                                .background(if (i <= step) Ln.Primary else Ln.HairlineStrong)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(120.dp))
            }
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (step) {
                    1 -> StepModel(modelId, colorIndex, { modelId = it }, { colorIndex = it })
                    2 -> StepIdentity(
                        name, { name = it },
                        plate, { plate = it },
                        usages
                    ) { u ->
                        usages = when {
                            u in usages -> usages - u
                            usages.size < 3 -> usages + u
                            else -> usages
                        }
                    }
                    3 -> StepInfo(birthYear, birthMonth, fuel, birthOk, { birthYear = it }, { birthMonth = it }, { fuel = it })
                    else -> StepFocus(focus) { type ->
                        focus = when {
                            type in focus -> focus - type
                            focus.size < 2 -> focus + type
                            else -> focus
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            val canNext = when (step) {
                1 -> true
                2 -> name.isNotBlank() && usages.isNotEmpty() && Plate.isValid(plate)
                3 -> birthOk
                else -> focus.isNotEmpty()
            }
            BigButton(
                text = if (step == 4) "내 차 만들기" else "다음",
                enabled = canNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (step == 4) showSummary = true else step += 1
            }
        }
    }

    if (showSummary) {
        val boosts = usages.flatMap { it.boosts }.toSet()
        Dialog(
            onDismissRequest = { showSummary = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Row(
                modifier = Modifier
                    .clip(CardShape)
                    .background(Ln.Surface1)
                    .border(1.dp, Ln.HairlineStrong, CardShape)
                    .padding(40.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelCar(model, color, level = 1, mood = Mood.HAPPY, modifier = Modifier.size(width = 340.dp, height = 227.dp))
                Column(modifier = Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("이 차로 시작할까요?", color = Ln.InkSubtle, fontSize = 22.sp)
                    Text(name, color = Ln.Ink, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                    if (Plate.isComplete(plate)) PlateBadge(plate)
                    SummaryRow("차종", "${model.label} · ${CarColors.all[colorIndex].name}")
                    SummaryRow("용도", usages.sortedBy { it.ordinal }.joinToString(" · ") { it.label })
                    SummaryRow("1.2배 능력치", boosts.joinToString(", ") { it.label })
                    if (birthYm != null) {
                        SummaryRow("출고", "${birthYm / 100}년 ${birthYm % 100}월 · ${CarAge.text(birthYm, nowYm)}")
                    }
                    fuel?.let { SummaryRow("연료", it.label) }
                    SummaryRow("시작 능력치", "안전 99, 나머지 0")
                    SummaryRow("집중 육성", focus.joinToString(", ") { it.label })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton("다시 보기", primary = false, modifier = Modifier.weight(1f)) { showSummary = false }
                        BigButton("1시즌 시작", modifier = Modifier.weight(1f)) {
                            showSummary = false
                            onFinish(
                                Player(
                                    modelId = modelId,
                                    colorIndex = colorIndex,
                                    name = name.trim(),
                                    plate = plate,
                                    usages = usages,
                                    focus = focus,
                                    demoMode = false,
                                    createdAt = System.currentTimeMillis(),
                                    goals = emptyMap(),
                                    fuel = fuel,
                                    birthYm = birthYm
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ln.InkSubtle, fontSize = 22.sp, modifier = Modifier.width(170.dp))
        Text(value, color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StepTitle(small: String, title: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(small, color = Ln.InkSubtle, fontSize = 22.sp, lineHeight = 28.sp)
        Text(
            title,
            color = Ln.Ink,
            fontSize = 38.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp
        )
        if (sub != null) Text(sub, color = Ln.InkSubtle, fontSize = 22.sp, lineHeight = 30.sp)
    }
}

// ─── 1단계: 차종과 색 ───
@Composable
private fun StepModel(
    modelId: String,
    colorIndex: Int,
    onModel: (String) -> Unit,
    onColor: (Int) -> Unit
) {
    StepTitle("나의 차 키우기를 시작해요", "내 차의 모습을 골라 주세요")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CarModels.all.chunked(4).forEach { rowModels ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowModels.forEach { m ->
                    val selected = m.id == modelId
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ItemShape)
                            .background(if (selected) Ln.Surface2 else Ln.Surface1)
                            .border(2.dp, if (selected) Ln.Primary else Ln.Hairline, ItemShape)
                            .clickable { onModel(m.id) }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PixelCar(
                            m, Color(CarColors.all[colorIndex].argb), level = 1, mood = Mood.CALM,
                            animate = false, modifier = Modifier.size(width = 150.dp, height = 100.dp)
                        )
                        Text(m.label, color = Ln.Ink, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        Text(m.kind, color = Ln.InkSubtle, fontSize = 16.sp)
                    }
                }
            }
        }
    }
    Text("색상 · ${CarColors.all[colorIndex].name}", color = Ln.InkSubtle, fontSize = 22.sp)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CarColors.all.chunked(6).forEachIndexed { rowIdx, rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                rowColors.forEachIndexed { i, c ->
                    val index = rowIdx * 6 + i
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(c.argb))
                            .border(
                                if (index == colorIndex) 4.dp else 1.dp,
                                if (index == colorIndex) Ln.Primary else Ln.HairlineStrong,
                                CircleShape
                            )
                            .clickable { onColor(index) }
                    )
                }
            }
        }
    }
}

// ─── 2단계: 별명, 번호판, 용도 ───
@Composable
private fun StepIdentity(
    name: String,
    onName: (String) -> Unit,
    plate: String,
    onPlate: (String) -> Unit,
    usages: Set<Usage>,
    onToggleUsage: (Usage) -> Unit
) {
    StepTitle("고르고 나면 도트 차가 바로 달라져요", "차에게 별명을 지어 주세요")

    BasicTextField(
        value = name,
        onValueChange = { if (it.length <= 10) onName(it) },
        singleLine = true,
        textStyle = TextStyle(color = Ln.Ink, fontSize = 30.sp),
        cursorBrush = SolidColor(Ln.Primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ItemShape)
                    .background(Ln.Surface1)
                    .border(1.dp, Ln.HairlineStrong, ItemShape)
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            ) {
                if (name.isEmpty()) Text("별명 (최대 10자)", color = Ln.InkTertiary, fontSize = 30.sp)
                inner()
            }
        }
    )

    Text("번호판 (선택)", color = Ln.InkSubtle, fontSize = 22.sp)
    PlateField(plate, onPlate)

    Text("주로 어떻게 쓰나요? (최대 3개)", color = Ln.InkSubtle, fontSize = 22.sp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Usage.values().toList().chunked(4).forEach { rowUsages ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowUsages.forEach { u ->
                    val on = u in usages
                    Box(modifier = Modifier.weight(1f)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CardShape)
                                .background(if (on) Ln.PrimarySoft else Ln.Surface1)
                                .border(2.dp, if (on) Ln.Primary else Ln.Hairline, CardShape)
                                .clickable { onToggleUsage(u) }
                                .padding(horizontal = 10.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PixelIcon(u.icon, Color(u.accent), Modifier.size(56.dp))
                            Text(u.label, color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text(
                                u.desc,
                                color = Ln.InkSubtle,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                textAlign = TextAlign.Center,
                                minLines = 2
                            )
                        }
                        if (on) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Ln.Primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = Ln.OnPrimary, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (usages.isNotEmpty()) {
        val boosts = usages.flatMap { it.boosts }.toSet()
        Text(
            "${boosts.joinToString(", ") { it.label }} 능력치가 1.2배 빨리 자라요. 여러 개를 골라도 겹치는 능력치는 한 번만 적용돼요.",
            color = Ln.InkSubtle,
            fontSize = 19.sp,
            lineHeight = 27.sp
        )
    }
}

// ─── 3단계: 내 차 소개 (모두 선택 사항) ───
@Composable
private fun StepInfo(
    year: Int?,
    month: Int?,
    fuel: FuelType?,
    birthOk: Boolean,
    onYear: (Int?) -> Unit,
    onMonth: (Int?) -> Unit,
    onFuel: (FuelType?) -> Unit
) {
    StepTitle(
        "모두 선택 사항이에요. 비워 두고 다음을 눌러도 돼요",
        "내 차를 소개해 주세요",
        "출고 연월을 넣으면 차 나이와 생일 연출이 생기고, 연료 종류를 고르면 주유·충전 문구가 알맞게 바뀌어요."
    )
    CarInfoFields(year, month, fuel, birthOk, onYear, onMonth, onFuel)
}

// 출고 연월과 연료 종류 입력 (시작 과정과 내 차 설정에서 같이 씀)
@Composable
internal fun CarInfoFields(
    year: Int?,
    month: Int?,
    fuel: FuelType?,
    birthOk: Boolean,
    onYear: (Int?) -> Unit,
    onMonth: (Int?) -> Unit,
    onFuel: (FuelType?) -> Unit
) {
    val nowYm = CareerEngine.ymOf(System.currentTimeMillis())
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("출고 연월", color = Ln.InkSubtle, fontSize = 22.sp, modifier = Modifier.weight(1f))
            if (year != null || month != null) {
                TextButton(
                    onClick = {
                        onYear(null)
                        onMonth(null)
                    },
                    modifier = Modifier.heightIn(min = 64.dp)
                ) { Text("지우기", color = Ln.InkSubtle, fontSize = 20.sp) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ItemShape)
                .background(Ln.Surface1)
                .border(1.dp, Ln.HairlineStrong, ItemShape)
                .clickable { showPicker = true }
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (year != null && month != null) "${year}년 ${month}월" else "선택하기",
                color = if (year != null && month != null) Ln.Ink else Ln.InkTertiary,
                fontSize = 30.sp,
                modifier = Modifier.weight(1f)
            )
            Text("›", color = Ln.InkSubtle, fontSize = 34.sp)
        }
        if (showPicker) {
            YearMonthPickerDialog(
                initialYear = year,
                initialMonth = month,
                onConfirm = { y, m ->
                    onYear(y)
                    onMonth(m)
                    showPicker = false
                },
                onDismiss = { showPicker = false }
            )
        }
        if (year != null && month != null && birthOk) {
            Text(
                "우리 차는 ${CarAge.text(CarAge.ym(year, month), nowYm)}이에요",
                color = Ln.PrimaryHover,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (!birthOk) {
            Text(
                if (year == null || month == null) "연도와 월을 모두 고르거나, 모두 비워 주세요." else "아직 오지 않은 달이에요.",
                color = Ln.Danger,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(6.dp))
        Text("연료 종류", color = Ln.InkSubtle, fontSize = 22.sp)
        FuelType.values().toList().chunked(3).forEach { rowFuels ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowFuels.forEach { f ->
                    val on = f == fuel
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ItemShape)
                            .background(if (on) Ln.PrimarySoft else Ln.Surface1)
                            .border(2.dp, if (on) Ln.Primary else Ln.Hairline, ItemShape)
                            .clickable { onFuel(if (on) null else f) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(f.label, color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text("${f.refill}하는 차", color = Ln.InkSubtle, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

// ─── 4단계: 집중 육성 ───
@Composable
private fun StepFocus(focus: Set<StatType>, onToggle: (StatType) -> Unit) {
    StepTitle(
        "새 차는 안전만 99, 나머지는 0에서 시작해요",
        "집중해서 키울 능력치를 골라 주세요",
        "최대 2개까지 고를 수 있어요. 고른 능력치는 1.5배 빨리 자라요."
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Ln.Surface1)
            .border(1.dp, Ln.Hairline, CardShape)
    ) {
        val growable = StatType.values().filter { it != StatType.SAFETY }
        growable.forEachIndexed { i, type ->
            val on = type in focus
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .clickable { onToggle(type) }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(type.label, color = Ln.Ink, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(150.dp))
                Text(type.hint, color = Ln.InkSubtle, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text("0", color = Ln.InkMuted, fontSize = 26.sp)
                Spacer(Modifier.width(24.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (on) Ln.Primary else Color.Transparent)
                        .border(2.dp, if (on) Ln.Primary else Ln.HairlineStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (on) Text("✓", color = Ln.OnPrimary, fontSize = 22.sp)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ln.Hairline)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("안전", color = Ln.InkSubtle, fontSize = 26.sp, modifier = Modifier.width(150.dp))
            Text("이미 99예요. 위험하게 달리면 깎이니 지켜 주세요.", color = Ln.InkSubtle, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("99", color = Ln.InkMuted, fontSize = 26.sp)
            Spacer(Modifier.width(60.dp))
        }
    }
}

// 연·월만 고르는 팝업: 큰 ‹ › 로 연도를 넘기고, 월은 12칸에서 고름 (미래는 눌리지 않음)
@Composable
private fun YearMonthPickerDialog(
    initialYear: Int?,
    initialMonth: Int?,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cal = Calendar.getInstance()
    val thisYear = cal.get(Calendar.YEAR)
    val thisMonth = cal.get(Calendar.MONTH) + 1
    val minYear = 1990
    var y by remember { mutableStateOf(initialYear ?: thisYear) }
    var m by remember { mutableStateOf(initialMonth) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(CardShape)
                .background(Ln.Surface1)
                .border(1.dp, Ln.HairlineStrong, CardShape)
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("출고 연월을 골라 주세요", color = Ln.InkSubtle, fontSize = 22.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                PickerArrow("‹", enabled = y > minYear) {
                    y -= 1
                }
                Text(
                    "${y}년",
                    color = Ln.Ink,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                PickerArrow("›", enabled = y < thisYear) {
                    y += 1
                    val cur = m
                    if (y == thisYear && cur != null && cur > thisMonth) m = null
                }
            }

            (1..12).chunked(4).forEach { rowMonths ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowMonths.forEach { mm ->
                        val future = y == thisYear && mm > thisMonth
                        val on = m == mm
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .clip(ItemShape)
                                .background(if (on) Ln.Primary else Ln.Surface2)
                                .border(1.dp, if (on) Ln.Primary else Ln.HairlineStrong, ItemShape)
                                .then(if (future) Modifier else Modifier.clickable { m = mm }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${mm}월",
                                color = when {
                                    on -> Ln.OnPrimary
                                    future -> Ln.InkTertiary
                                    else -> Ln.Ink
                                },
                                fontSize = 26.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("취소", primary = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                BigButton("확인", enabled = m != null, modifier = Modifier.weight(1f)) {
                    m?.let { onConfirm(y, it) }
                }
            }
        }
    }
}

@Composable
private fun PickerArrow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(ItemShape)
            .background(Ln.Surface2)
            .border(1.dp, Ln.HairlineStrong, ItemShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Ln.Ink else Ln.InkTertiary, fontSize = 44.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ─── 번호판 입력 (키보드로 직접 작성, 선택 사항) ───
@Composable
internal fun PlateField(plate: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BasicTextField(
                value = plate,
                onValueChange = { onChange(Plate.normalize(it)) },
                singleLine = true,
                textStyle = TextStyle(color = Ln.Ink, fontSize = 30.sp, letterSpacing = 2.sp),
                cursorBrush = SolidColor(Ln.Primary),
                modifier = Modifier.width(420.dp),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ItemShape)
                            .background(Ln.Surface1)
                            .border(1.dp, Ln.HairlineStrong, ItemShape)
                            .padding(horizontal = 24.dp, vertical = 22.dp)
                    ) {
                        if (plate.isEmpty()) Text("번호판", color = Ln.InkTertiary, fontSize = 30.sp)
                        inner()
                    }
                }
            )
            if (Plate.isComplete(plate)) PlateBadge(plate)
        }
        Text(
            "숫자 2~3자리, 한글 1자, 숫자 4자리 · 이 기기 안에만 저장돼요. 비워 두면 번호판 없이 시작해요.",
            color = Ln.InkTertiary,
            fontSize = 18.sp
        )
        if (plate.isNotEmpty() && !Plate.isComplete(plate)) {
            Text("번호판 형식을 확인해 주세요.", color = Ln.Danger, fontSize = 20.sp)
        }
    }
}
