package com.leejinwo0.hyundaigochi

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlin.math.min

// 직접 그린 원본 도트 자동차 (24 x 16 칸). 로고·엠블럼 없음.
private const val GRID_W = 24
private const val GRID_H = 16
private const val GROUND_ROW = 14

private val WindowColor = Color(0xFFCFEFF5)
private val EyeColor = Color(0xFF10151F)
private val LampColor = Color(0xFFF5D76E)
private val TailColor = Color(0xFFDC3C3C)
private val TireColor = Color(0xFF1B1E24)
private val OutlineColor = Color(0xFF2B303B)
private val CheekColor = Color(0xFFF28DA8)
private val RainColor = Color(0xFF5BA4E6)
private val SteamColor = Color(0xFF9AA3AF)
private val IceColor = Color(0xFF6EC1F0)
private val YellowColor = Color(0xFFF0B429)
private val RedColor = Color(0xFFEB5757)
private val MagentaColor = Color(0xFFC04FC9)

// 레벨이 오르면 휠 색이 바뀜 (8단계)
private fun hubColor(level: Int): Color = when {
    level >= 15 -> Color(0xFF7DF9FF)
    level >= 13 -> Color(0xFFE8C14A)
    level >= 11 -> Color(0xFFFF9B4A)
    level >= 9 -> Color(0xFFB07CFF)
    level >= 7 -> Color(0xFF3FCF8E)
    level >= 5 -> Color(0xFF6C8CFF)
    level >= 3 -> Color(0xFFD5DAE3)
    else -> Color(0xFF8A90A0)
}

private fun cheekOf(model: CarModel): Pair<Int, Int>? {
    var ex = -1
    var ey = -1
    model.sprite.forEachIndexed { r, line ->
        val c = line.indexOf('E')
        if (c >= 0) {
            ex = c
            ey = r
        }
    }
    if (ex < 0) return null
    for (dy in 2..3) {
        val row = model.sprite.getOrNull(ey + dy) ?: continue
        for (dx in 1 downTo 0) {
            if (ex + dx < row.length && row[ex + dx] == 'B') return Pair(ex + dx, ey + dy)
        }
    }
    return null
}

@Composable
fun PixelCar(
    model: CarModel,
    bodyColor: Color,
    level: Int,
    mood: Mood,
    modifier: Modifier = Modifier,
    animate: Boolean = true // 주행 중에는 false (움직임 없음)
) {
    // 도트 느낌의 2프레임 움직임: 0.7초마다 반 칸씩 오르내림 (매 프레임 다시 그리지 않아 가벼움)
    var up by remember { mutableStateOf(false) }
    if (animate) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(700)
                up = !up
            }
        }
    }

    Canvas(modifier = modifier) {
        val cell = min(size.width / GRID_W, size.height / GRID_H)
        val origin = Offset((size.width - cell * GRID_W) / 2f, (size.height - cell * GRID_H) / 2f)
        val lift = if (animate && up) -cell * 0.5f else 0f

        fun px(x: Int, y: Int, color: Color, dy: Float = 0f) {
            drawRect(
                color = color,
                topLeft = Offset(origin.x + x * cell, origin.y + y * cell + dy),
                size = Size(cell + 0.5f, cell + 0.5f)
            )
        }

        val trim = lerp(bodyColor, Color.Black, 0.35f)
        val hub = hubColor(level)
        val top = GROUND_ROW - (model.sprite.size - 1)
        val closedEye = mood == Mood.SLEEPY || mood == Mood.TIRED || mood == Mood.BORED || mood == Mood.DIZZY

        // 외곽선 (흰 배경에서 흰색·은색 차도 또렷하게 보이도록)
        val occupied = HashSet<Int>()
        model.sprite.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> if (ch != '.') occupied.add((top + r) * GRID_W + c) }
        }
        for (key in occupied) {
            val x = key % GRID_W
            val y = key / GRID_W
            listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).forEach { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until GRID_W && ny in 0 until GRID_H && !occupied.contains(ny * GRID_W + nx)) {
                    px(nx, ny, OutlineColor, lift)
                }
            }
        }

        // 차체
        model.sprite.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                val color = when (ch) {
                    'B' -> bodyColor
                    'D' -> trim
                    'W', 'E' -> WindowColor
                    'L' -> LampColor
                    'R' -> TailColor
                    'T' -> TireColor
                    'G' -> hub
                    else -> null
                }
                if (color != null) {
                    val wheel = ch == 'T' || ch == 'G'
                    px(col, top + row, color, if (wheel) 0f else lift)
                }
                if (ch == 'E') {
                    if (closedEye) {
                        drawRect(
                            color = EyeColor,
                            topLeft = Offset(origin.x + col * cell, origin.y + (top + row) * cell + cell * 0.55f + lift),
                            size = Size(cell, cell * 0.25f)
                        )
                    } else {
                        px(col, top + row, EyeColor, lift)
                        if (mood == Mood.SCARED && col > 0 && line[col - 1] == 'W') {
                            px(col - 1, top + row, EyeColor, lift) // 놀라서 커진 눈
                        }
                    }
                }
            }
        }

        // 레벨 꾸밈: 5레벨 줄무늬, 8레벨 지붕 램프, 12레벨 반짝임, 15레벨 왕관
        val lastWindow = model.sprite.indexOfLast { 'W' in it }
        val stripeRow = lastWindow + 2
        if (level >= 5 && stripeRow in 1 until model.sprite.size - 3) {
            val lum = 0.299f * bodyColor.red + 0.587f * bodyColor.green + 0.114f * bodyColor.blue
            val stripe = when {
                level >= 9 -> Color(0xFFE8C14A)
                lum > 0.55f -> Color(0xFF2B303B)
                else -> Color(0xFFF4F5F7)
            }
            model.sprite[stripeRow].forEachIndexed { col, ch ->
                if (ch == 'B') px(col, top + stripeRow, stripe, lift)
            }
        }
        val roofCols = model.sprite[0].indices.filter { model.sprite[0][it] == 'B' }
        val roofX = if (roofCols.isEmpty()) 12 else (roofCols.first() + roofCols.last()) / 2
        if (level >= 8 && top - 1 >= 0) {
            px(roofX, top - 1, LampColor, lift)
            px(roofX + 1, top - 1, LampColor, lift)
        }
        if (level >= 12) {
            px(0, 1, YellowColor)
            px(23, 1, YellowColor)
            px(1, 3, YellowColor)
            px(22, 3, YellowColor)
        }
        if (level >= 15 && top - 3 >= 0) {
            val gold = Color(0xFFE8C14A)
            for (dx in -2..2) px(roofX + dx, top - 1, gold, lift)
            px(roofX - 2, top - 2, gold, lift)
            px(roofX, top - 2, gold, lift)
            px(roofX + 2, top - 2, gold, lift)
        }

        // 볼
        if (mood == Mood.WARM || mood == Mood.HAPPY || mood == Mood.PARTY) {
            cheekOf(model)?.let { (cx, cy) -> px(cx, top + (cy), CheekColor, lift) }
        }

        // 기분 소품 (위아래로 흔들리지 않게 고정, 위쪽 빈 공간에만 그림)
        fun dots(color: Color, vararg points: Pair<Int, Int>) {
            points.forEach { (x, y) -> px(x, y, color) }
        }
        when (mood) {
            Mood.WET -> {
                listOf(2 to 0, 7 to 1, 12 to 0, 17 to 1, 21 to 0, 10 to 2).forEach { (x, y) ->
                    px(x, y, RainColor)
                    px(x, y + 1, RainColor)
                }
            }
            Mood.WARM -> dots(SteamColor, 17 to 3, 18 to 2, 17 to 1, 20 to 3, 21 to 2, 20 to 1)
            Mood.COOL -> dots(IceColor, 18 to 0, 17 to 1, 18 to 1, 19 to 1, 18 to 2, 4 to 1)
            Mood.TIRED -> dots(RainColor, 20 to 1, 20 to 2, 19 to 0)
            Mood.SLEEPY -> {
                dots(SteamColor, 17 to 0, 18 to 0, 19 to 0, 18 to 1, 17 to 2, 18 to 2, 19 to 2)
                dots(SteamColor, 21 to 0)
            }
            Mood.BORED -> dots(SteamColor, 15 to 1, 17 to 1, 19 to 1)
            Mood.HUNGRY -> {
                dots(SteamColor, 15 to 0, 16 to 0, 17 to 0, 18 to 0, 19 to 0, 15 to 1, 19 to 1, 20 to 1, 15 to 2, 16 to 2, 17 to 2, 18 to 2, 19 to 2)
                px(16, 1, RedColor)
            }
            Mood.SCARED -> dots(YellowColor, 19 to 0, 19 to 1, 19 to 3)
            Mood.DIZZY -> dots(YellowColor, 8 to 1, 10 to 0, 12 to 1, 14 to 0, 9 to 2, 13 to 2)
            Mood.PARTY -> {
                dots(MagentaColor, 10 to 1, 9 to 2, 10 to 2, 11 to 2, 8 to 3, 9 to 3, 10 to 3, 11 to 3, 12 to 3)
                dots(YellowColor, 10 to 0)
                dots(YellowColor, 3 to 1, 16 to 2, 5 to 0)
                dots(RainColor, 13 to 0, 18 to 1, 21 to 2)
            }
            Mood.HAPPY -> dots(YellowColor, 18 to 0, 17 to 1, 18 to 1, 19 to 1, 18 to 2, 4 to 1)
            Mood.CALM -> Unit
        }
    }
}

// 용도 카드에 쓰는 10 x 10 도트 아이콘
@Composable
fun PixelIcon(rows: List<String>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val n = rows.size
        val cell = min(size.width, size.height) / n
        val ox = (size.width - cell * rows[0].length) / 2f
        val oy = (size.height - cell * n) / 2f
        val light = lerp(accent, Color.White, 0.55f)
        rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                val c = when (ch) {
                    'K' -> Color(0xFF2B303B)
                    'B' -> accent
                    'L' -> light
                    'Y' -> Color(0xFFF2C230)
                    'R' -> Color(0xFFD93D4F)
                    'W' -> Color(0xFFF4F5F7)
                    'G' -> Color(0xFF3FA35B)
                    else -> null
                }
                if (c != null) {
                    drawRect(
                        color = c,
                        topLeft = Offset(ox + x * cell, oy + y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}
