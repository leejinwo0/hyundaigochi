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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Linear 디자인 시스템 분석(getdesign.md)의 밝은 테마 — 차량 화면 크기에 맞게 확대 적용
internal object Ln {
    val Canvas = Color(0xFFFFFFFF)
    val Surface1 = Color(0xFFF8F9FA)
    val Surface2 = Color(0xFFF1F3F5)
    val Surface3 = Color(0xFFE9ECEF)
    val Hairline = Color(0xFFE3E6EA)
    val HairlineStrong = Color(0xFFCDD2D9)
    val Ink = Color(0xFF0F1011)
    val InkMuted = Color(0xFF3C4149)
    val InkSubtle = Color(0xFF6B7079)
    val InkTertiary = Color(0xFF9AA0A8)
    val Primary = Color(0xFF5E6AD2)
    val PrimaryHover = Color(0xFF4651B8) // 흰 배경 위 글씨·링크용
    val PrimarySoft = Color(0xFFEEF0FD)
    val OnPrimary = Color(0xFFFFFFFF)
    val Success = Color(0xFF1F8A3B)
    val Warn = Color(0xFFB7791F)
    val Danger = Color(0xFFD93D3D) // 위험 운전·삭제 전용
}

internal val CardShape = RoundedCornerShape(16.dp)
internal val ItemShape = RoundedCornerShape(12.dp)
internal val PillShape = RoundedCornerShape(50)

@Composable
internal fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 80.dp)
            .clip(ItemShape)
            .background(if (!enabled) Ln.Surface3 else if (primary) Ln.Primary else Ln.Surface2)
            .then(if (!primary && enabled) Modifier.border(1.dp, Ln.HairlineStrong, ItemShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (!enabled) Ln.InkTertiary else if (primary) Ln.OnPrimary else Ln.Ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(PillShape)
            .background(if (selected) Ln.Primary else Ln.Surface2)
            .border(1.dp, if (selected) Ln.Primary else Ln.HairlineStrong, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Ln.OnPrimary else Ln.InkMuted,
            fontSize = 24.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
internal fun Badge(text: String, danger: Boolean = false) {
    Text(
        text,
        color = if (danger) Ln.Danger else Ln.InkMuted,
        fontSize = 18.sp,
        modifier = Modifier
            .clip(PillShape)
            .background(Ln.Surface2)
            .border(1.dp, if (danger) Ln.Danger else Ln.Hairline, PillShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
internal fun Gauge(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
    barHeight: Int = 12,
    sub: String? = null,
    extra: String? = null,
    extraColor: Color = Ln.Success
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, color = Ln.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            if (sub != null) {
                Spacer(Modifier.width(10.dp))
                Text(sub, color = Ln.InkTertiary, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            if (extra != null) {
                Text(extra, color = extraColor, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
            }
            Text(valueText, color = Ln.InkSubtle, fontSize = 22.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Ln.Surface3)
                .border(1.dp, Ln.HairlineStrong, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color)
            )
        }
    }
}

@Composable
internal fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Ln.Surface1)
            .border(1.dp, Ln.Hairline, CardShape)
            .padding(20.dp)
    ) {
        Text(label, color = Ln.InkSubtle, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Ln.Ink, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp)
    }
}

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(Ln.Surface1)
            .border(1.dp, Ln.Hairline, CardShape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

// 번호판 모양 (직접 입력한 번호판을 도트 차 옆에 보여 줌)
@Composable
internal fun PlateBadge(plate: String, modifier: Modifier = Modifier) {
    val text = Plate.display(plate)
    if (text.isEmpty()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F2F0))
            .border(3.dp, Color(0xFF1C2433), RoundedCornerShape(8.dp))
            .padding(horizontal = 28.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF1C2433), fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ln.Surface2,
        titleContentColor = Ln.Ink,
        textContentColor = Ln.InkMuted,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = Ln.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", color = Ln.InkMuted) }
        }
    )
}

internal fun fmtDate(pattern: String, millis: Long): String =
    java.text.SimpleDateFormat(pattern, java.util.Locale.KOREA).format(java.util.Date(millis))

internal fun fmtMinutes(m: Long): String = when {
    m < 1 -> "1분 미만"
    m < 60 -> "${m}분"
    else -> "${m / 60}시간 ${m % 60}분"
}
