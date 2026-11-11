package com.leejinwo0.hyundaigochi

// 주행 중 겪은 경험 (차량 신호에서 옴) → 성장 능력치의 재료
enum class TripEvent(val label: String) {
    RAIN("비"),            // 와이퍼 작동
    NIGHT_LIGHTS("밤길"),  // 전조등(하향등) 켜짐
    HEATER("히터"),        // 열선 시트·핸들 또는 높은 설정 온도
    AIR_CON("에어컨"),     // A/C 켜짐
    PASSENGER("동승자"),   // 다른 좌석 착석 또는 뒷문 열림
    WINDOW_OPEN("창문")    // 앞 창문 열림
}

// 위험 운전 기록 → 컨디션을 깎는 재료
data class RiskCounts(
    val harshAccel: Int = 0,
    val harshBrake: Int = 0,
    val unbuckled: Boolean = false,
    val longDrive: Boolean = false
) {
    val isClean: Boolean
        get() = harshAccel == 0 && harshBrake == 0 && !unbuckled && !longDrive

    val total: Int
        get() = harshAccel + harshBrake + (if (unbuckled) 1 else 0) + (if (longDrive) 1 else 0)
}

// 주행 1회 기록 (이것만 저장하고, 성장 상태는 여기서 매번 계산)
data class TripRecord(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val startOdometer: Float?,
    val endOdometer: Float?,
    val events: Set<TripEvent>,
    val risk: RiskCounts,
    val estimated: Boolean = false // 앱이 꺼져 있던 동안 주행거리 차이로만 반영된 주행
) {
    val distanceKm: Float?
        get() {
            val s = startOdometer
            val e = endOdometer
            return if (s != null && e != null && e >= s) e - s else null
        }

    val minutes: Long get() = (endTime - startTime) / 60_000
}

// 기어·주행거리·차량 신호를 받아 "출발~도착" 한 번을 기록
class TripRecorder {

    var isDriving = false
        private set

    private var latestOdometer: Float? = null
    private val activeNow = mutableSetOf<TripEvent>()
    private val tripEvents = mutableSetOf<TripEvent>()
    private var pendingPassenger = false
    private var startOdometer: Float? = null
    private var startTime = 0L

    // 위험 운전
    private var beltBuckled = true
    private var unbuckledWhileDriving = false
    private var harshAccel = 0
    private var harshBrake = 0
    private var lastSpeed: Float? = null
    private var lastSpeedNs = 0L
    private var lastHarshNs = 0L

    fun onDistance(km: Float?) {
        if (km != null) latestOdometer = km
    }

    // 차량 신호가 켜지거나 꺼졌을 때 (예: 와이퍼 켜짐 → RAIN)
    fun onSignal(event: TripEvent, active: Boolean) {
        if (active) activeNow.add(event) else activeNow.remove(event)
        if (isDriving && active) tripEvents.add(event)
    }

    // 정차 중 뒷문이 열렸다면 다음 주행에 누군가 함께 탄 것으로 봄
    fun onRearDoorOpened() {
        if (isDriving) tripEvents.add(TripEvent.PASSENGER) else pendingPassenger = true
    }

    fun onSeatBelt(buckled: Boolean) {
        beltBuckled = buckled
        if (isDriving && !buckled) unbuckledWhileDriving = true
    }

    // 차속(m/s)과 측정 시각(ns)으로 급가속·급감속 판단
    fun onSpeed(speedMs: Float, timestampNs: Long) {
        val prev = lastSpeed
        val prevNs = lastSpeedNs
        lastSpeed = speedMs
        lastSpeedNs = timestampNs
        if (!isDriving || prev == null || prevNs == 0L) return

        val dt = (timestampNs - prevNs) / 1_000_000_000f
        if (dt < 0.05f || dt > 2f) return
        if (timestampNs - lastHarshNs < 2_000_000_000L) return // 같은 동작을 여러 번 세지 않도록 2초 간격

        val accel = (speedMs - prev) / dt
        if (accel >= HARSH_ACCEL_MS2) {
            harshAccel++
            lastHarshNs = timestampNs
        } else if (accel <= -HARSH_BRAKE_MS2) {
            harshBrake++
            lastHarshNs = timestampNs
        }
    }

    // P에서 벗어나면 출발, 다시 P면 도착 → 도착했을 때만 기록을 돌려줌
    fun onGear(parked: Boolean): TripRecord? {
        val now = System.currentTimeMillis()

        if (!parked && !isDriving) {
            isDriving = true
            startOdometer = latestOdometer
            startTime = now
            tripEvents.clear()
            tripEvents.addAll(activeNow)
            if (pendingPassenger) tripEvents.add(TripEvent.PASSENGER)
            pendingPassenger = false
            harshAccel = 0
            harshBrake = 0
            unbuckledWhileDriving = !beltBuckled
            return null
        }

        if (parked && isDriving) {
            isDriving = false
            val minutes = (now - startTime) / 60_000
            return TripRecord(
                id = now,
                startTime = startTime,
                endTime = now,
                startOdometer = startOdometer,
                endOdometer = latestOdometer,
                events = tripEvents.toSet(),
                risk = RiskCounts(
                    harshAccel = harshAccel,
                    harshBrake = harshBrake,
                    unbuckled = unbuckledWhileDriving,
                    longDrive = minutes >= LONG_DRIVE_MINUTES
                )
            )
        }

        return null
    }

    companion object {
        // [가정] 공인 기준을 확인하지 못해 조정 가능한 값으로 둠
        const val HARSH_ACCEL_MS2 = 3.0f   // 약 11km/h를 1초에
        const val HARSH_BRAKE_MS2 = 3.5f   // 약 13km/h를 1초에
        const val LONG_DRIVE_MINUTES = 120L
    }
}
