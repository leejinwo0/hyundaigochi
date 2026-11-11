package com.leejinwo0.hyundaigochi

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 앱 프로세스가 살아 있는 동안 차량 신호를 계속 받아 주행을 기록하는 곳.
 * 화면(Activity)이 닫혀도 RecorderService 덕분에 계속 기록되고,
 * 서비스가 허용되지 않아 프로세스가 종료됐다면 다음에 앱을 열 때 누적 주행거리 차이로 달린 거리를 되찾음.
 */
object GameRuntime : VehicleSignalListener {

    private var started = false
    private lateinit var app: Context
    private lateinit var hub: VehicleHub
    private lateinit var store: TripStore
    private lateinit var prefs: GamePrefs
    private val recorder = TripRecorder()

    // 화면이 관찰하는 상태
    var player by mutableStateOf<Player?>(null)
    var trips by mutableStateOf<List<TripRecord>>(emptyList())
    var settledUntil by mutableStateOf(0L)
    var lastReportedSeason by mutableStateOf(0L)
    var isDriving by mutableStateOf(false)
    var uxRestricted by mutableStateOf(false)
    var gear by mutableStateOf("-")
    var odometer by mutableStateOf<Float?>(null)
    var battery by mutableStateOf<Int?>(null)
    var fuelPercent by mutableStateOf<Int?>(null)
    var speedGranted by mutableStateOf(false)
    var tick by mutableStateOf(0) // 화면을 다시 열 때마다 올려서 시간 기반 상태를 새로 계산
    val status = mutableStateMapOf<String, String>()
    val active = mutableStateMapOf<TripEvent, Boolean>()

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        app = context.applicationContext
        prefs = GamePrefs(app)
        store = TripStore(app)
        player = prefs.loadPlayer()
        trips = store.loadAll()
        settledUntil = prefs.settledUntil
        lastReportedSeason = prefs.lastReportedSeason

        hub = VehicleHub(app, this)
        hub.start()

        speedGranted = app.checkSelfPermission(SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (speedGranted) hub.startSpeed()
    }

    fun refreshTick() {
        tick++
    }

    fun onSpeedPermission(granted: Boolean) {
        speedGranted = granted
        if (granted) hub.startSpeed()
    }

    // ─── 플레이어 ───
    fun createPlayer(p: Player) {
        prefs.savePlayer(p)
        prefs.settledUntil = p.createdAt
        prefs.lastReportedSeason = 0L
        settledUntil = p.createdAt
        lastReportedSeason = 0L
        player = p
    }

    fun updatePlayer(p: Player) {
        prefs.savePlayer(p)
        player = p
    }

    fun setDemoMode(on: Boolean) {
        val p = player ?: return
        val next = p.copy(demoMode = on, goals = emptyMap())
        prefs.savePlayer(next)
        player = next
        // 방식이 바뀌면 이미 끝난 시즌의 성적표는 다시 띄우지 않음
        val mine = trips.filter { it.endTime >= next.createdAt }
        val done = CareerEngine.seasons(mine, on, System.currentTimeMillis()).filter { it.complete }
        setLastReported(done.maxOfOrNull { it.key } ?: 0L)
    }

    fun chooseGoal(nextKey: Long, goal: SeasonGoal) {
        val p = player ?: return
        val next = p.copy(goals = p.goals + (nextKey to goal))
        prefs.savePlayer(next)
        player = next
    }

    fun settle(until: Long) {
        prefs.settledUntil = until
        settledUntil = until
    }

    fun setLastReported(key: Long) {
        prefs.lastReportedSeason = key
        lastReportedSeason = key
    }

    // ─── 기록 지우기 ───
    fun deleteTrip(id: Long) {
        trips = store.delete(id)
    }

    fun clearTrips() {
        trips = store.clear()
    }

    fun resetAll() {
        trips = store.clear()
        prefs.clearAll()
        player = null
        settledUntil = 0L
        lastReportedSeason = 0L
    }

    // ─── 차량 신호 (모두 메인 스레드에서 들어옴) ───
    override fun onDistance(km: Float?) {
        if (km == null) return
        odometer = km
        recorder.onDistance(km)
        val base = prefs.odoBaseline
        when {
            base == null -> prefs.odoBaseline = km
            recorder.isDriving -> prefs.odoBaseline = km // 주행 중에는 기준이 따라감
            km < base -> prefs.odoBaseline = km          // 계기판이 초기화된 경우
            km - base >= MISSED_KM -> addEstimatedTrip(base, km)
        }
    }

    // 앱이 꺼져 있던 동안 달린 거리를 "기록 없는 주행"으로 되찾음 (이벤트는 알 수 없음)
    private fun addEstimatedTrip(from: Float, to: Float) {
        val now = System.currentTimeMillis()
        val t = TripRecord(
            id = now + 1,
            startTime = now,
            endTime = now,
            startOdometer = from,
            endOdometer = to,
            events = emptySet(),
            risk = RiskCounts(),
            estimated = true
        )
        trips = store.add(t)
        prefs.odoBaseline = to
    }

    override fun onGear(gear: Int) {
        this.gear = when (gear) {
            GEAR_PARK -> "P"
            1 -> "R"
            2 -> "N"
            8 -> "D"
            else -> "?($gear)"
        }
        val trip = recorder.onGear(parked = (gear == GEAR_PARK))
        isDriving = recorder.isDriving
        if (trip != null) {
            trips = store.add(trip)
            trip.endOdometer?.let { prefs.odoBaseline = it }
        }
    }

    override fun onSignal(event: TripEvent, active: Boolean) {
        this.active[event] = active
        recorder.onSignal(event, active)
    }

    override fun onRearDoorOpened() = recorder.onRearDoorOpened()

    override fun onSeatBelt(buckled: Boolean) = recorder.onSeatBelt(buckled)

    override fun onSpeed(speedMs: Float, timestampNs: Long) = recorder.onSpeed(speedMs, timestampNs)

    override fun onBattery(percent: Int?) {
        battery = percent
    }

    override fun onFuel(percent: Int?) {
        fuelPercent = percent
    }

    // 연료 종류에 맞는 잔량 (전기는 배터리, 그 밖은 연료. 종류를 모르면 있는 쪽)
    fun energyFor(p: Player): Int? = when (p.fuel) {
        FuelType.ELECTRIC -> battery
        null -> battery ?: fuelPercent
        else -> fuelPercent
    }

    override fun onUxRestricted(restricted: Boolean) {
        uxRestricted = restricted
    }

    override fun onStatus(key: String, text: String) {
        status[key] = text
    }

    private const val GEAR_PARK = 4 // AAOS GEAR_SELECTION의 P 값
    private const val MISSED_KM = 0.5f
    const val SPEED_PERMISSION = "android.car.permission.CAR_SPEED"
}
