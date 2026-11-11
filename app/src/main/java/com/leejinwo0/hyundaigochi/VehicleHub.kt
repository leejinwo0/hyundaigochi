package com.leejinwo0.hyundaigochi

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.HvacAcStateListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener
import ai.pleos.playground.vehicle.listener.HvacSteeringWheelHeatStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper

// 차량 신호를 받는 쪽 (MainActivity)
interface VehicleSignalListener {
    fun onDistance(km: Float?)
    fun onGear(gear: Int)
    fun onSignal(event: TripEvent, active: Boolean)
    fun onRearDoorOpened()
    fun onSeatBelt(buckled: Boolean)
    fun onSpeed(speedMs: Float, timestampNs: Long)
    fun onBattery(percent: Int?)
    fun onFuel(percent: Int?)
    fun onUxRestricted(restricted: Boolean)
    fun onStatus(key: String, text: String)
}

/**
 * 차량 신호를 한곳에서 등록·해제
 *  - Pleos Vehicle SDK: 주행거리, 와이퍼, 전조등, 공조, 좌석·벨트, 문, 창문, 배터리
 *  - AAOS 표준: 기어, 차속, 주행 규제
 * 모든 콜백은 메인 스레드로 넘겨 줌. 하나가 실패해도 나머지는 계속 동작.
 */
class VehicleHub(context: Context, private val out: VehicleSignalListener) {

    private val appContext = context.applicationContext
    private val vehicle = Vehicle(context)
    private var car: Car? = null
    private var cpm: CarPropertyManager? = null
    private var ux: CarUxRestrictionsManager? = null
    private val main = Handler(Looper.getMainLooper())
    private val cleanups = mutableListOf<() -> Unit>()

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)

    // 아래 상태들은 메인 스레드에서만 바꿈
    private var hvacPower = true
    private var acOn = false
    private var setTemp: Float? = null
    private var seatHeat = 0
    private var wheelHeat = 0
    private val occupied = mutableMapOf<SeatValue, Boolean>()
    private val windowOpen = mutableMapOf<WindowValue, Boolean>()
    private var beltBuckled: Boolean? = null
    private var beltWarning: Boolean? = null

    private fun ui(block: () -> Unit) {
        main.post { block() }
    }

    private fun ok(key: String, text: String) = ui { out.onStatus(key, "✓ $text") }

    private fun fail(key: String, e: Exception) = ui { out.onStatus(key, "✕ ${e.message}") }

    private fun safe(key: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail(key, e)
        }
    }

    fun start() {
        vehicle.initialize()
        startOdometer()
        startWiper()
        startLight()
        startClimate()
        startSeats()
        startDoors()
        startWindows()
        startBattery()
        startFuel()
        startCar()
    }

    // ─── Pleos: 누적 주행거리 (km) ───
    private fun startOdometer() = safe("주행거리") {
        val odo = vehicle.getOdometer()
        odo.getCurrentDistanceDriven(
            onComplete = { km ->
                ui { out.onDistance(km) }
                ok("주행거리", "${km}km")
            },
            onFailed = { e -> fail("주행거리", e) }
        )
        val l = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(value: Float?) {
                ui { out.onDistance(value) }
                ok("주행거리", "${value}km")
            }

            override fun onFailed(e: Exception) = fail("주행거리", e)
        }
        odo.registerDistanceDriven(l)
        cleanups += { odo.unregisterDistanceDriven(l) }
    }

    // ─── Pleos: 앞유리 와이퍼 → 비 ───
    private fun startWiper() = safe("와이퍼") {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val l = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                ui { out.onSignal(TripEvent.RAIN, state == WiperState.ON) }
                ok("와이퍼", "$state")
            }

            override fun onFailed(e: Exception) = fail("와이퍼", e)
        }
        vehicle.getWiper().registerWiperState(area, l)
        cleanups += { vehicle.getWiper().unregisterWiperState(area, l) }
    }

    // ─── Pleos: 하향등 → 밤길 ───
    private fun startLight() = safe("전조등") {
        val l = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                ui { out.onSignal(TripEvent.NIGHT_LIGHTS, state == LightState.ON) }
                ok("전조등", "$state")
            }

            override fun onFailed(e: Exception) = fail("전조등", e)
        }
        vehicle.getLight().registerLowBeamLightState(l)
        cleanups += { vehicle.getLight().unregisterLowBeamLightState(l) }
    }

    // ─── Pleos: 공조 → 히터·에어컨 ───
    private fun updateClimate() {
        out.onSignal(TripEvent.AIR_CON, hvacPower && acOn)
        val warmAir = hvacPower && !acOn && (setTemp ?: 0f) >= HEATER_TEMP_C
        out.onSignal(TripEvent.HEATER, seatHeat > 0 || wheelHeat > 0 || warmAir)
        out.onStatus("공조", "✓ 전원 $hvacPower, A/C $acOn, 설정 ${setTemp}℃, 열선 시트 $seatHeat, 열선 핸들 $wheelHeat")
    }

    private fun startClimate() {
        val hvac = vehicle.getHVAC()

        safe("공조") {
            val l = object : HvacPowerStateListener {
                override fun onHvacPowerStateUpdated(value: Boolean?) = ui {
                    hvacPower = value != false
                    updateClimate()
                }

                override fun onFailed(e: Exception) = fail("공조", e)
            }
            hvac.registerHvacPowerState(driverSeat, l)
            cleanups += { hvac.unregisterHvacPowerState(driverSeat, l) }
        }

        safe("공조") {
            val l = object : HvacAcStateListener {
                override fun onHvacAcStateUpdated(value: Boolean?) = ui {
                    acOn = value == true
                    updateClimate()
                }

                override fun onFailed(e: Exception) = fail("공조", e)
            }
            hvac.registerHvacAcState(driverSeat, l)
            cleanups += { hvac.unregisterHvacAcState(driverSeat, l) }
        }

        safe("공조") {
            val l = object : HvacTemperatureListener {
                override fun onHvacTemperatureUpdated(value: Float?) = ui {
                    setTemp = value
                    updateClimate()
                }

                override fun onFailed(e: Exception) = fail("공조", e)
            }
            hvac.registerHvacTemperature(driverSeat, l)
            cleanups += { hvac.unregisterHvacTemperature(driverSeat, l) }
        }

        safe("공조") {
            val l = object : HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(value: Int?) = ui {
                    seatHeat = value ?: 0 // 양수 = 열선, 음수 = 통풍
                    updateClimate()
                }

                override fun onFailed(e: Exception) = fail("공조", e)
            }
            val seat = vehicle.getSeat() // 열선 시트는 Seat 도메인
            seat.registerHvacSeatTemperature(driverSeat, l)
            cleanups += { seat.unregisterHvacSeatTemperature(driverSeat, l) }
        }

        safe("공조") {
            val l = object : HvacSteeringWheelHeatStateListener {
                override fun onHvacSteeringWheelHeatStateUpdated(value: Int?) = ui {
                    wheelHeat = value ?: 0
                    updateClimate()
                }

                override fun onFailed(e: Exception) = fail("공조", e)
            }
            val wheel = vehicle.getSteeringWheel() // 열선 핸들은 SteeringWheel 도메인
            wheel.registerHvacSteeringWheelHeatState(l)
            cleanups += { wheel.unregisterHvacSteeringWheelHeatState(l) }
        }
    }

    // ─── Pleos: 다른 좌석 착석 → 동승자 / 운전석 벨트 → 위험 운전 ───
    private fun updateBelt() {
        val unbuckled = beltBuckled == false || beltWarning == true
        out.onSeatBelt(!unbuckled)
        out.onStatus("안전벨트", "✓ 착용 $beltBuckled, 경고 $beltWarning")
    }

    private fun startSeats() {
        val seat = vehicle.getSeat()

        listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).forEach { value ->
            safe("동승석") {
                val area = SeatArea(value)
                val l = object : SeatOccupancyListener {
                    override fun onSeatOccupancyUpdated(value2: Boolean?) = ui {
                        occupied[value] = value2 == true
                        out.onSignal(TripEvent.PASSENGER, occupied.values.any { it })
                        out.onStatus("동승석", "✓ ${occupied.filterValues { it }.keys}")
                    }

                    override fun onFailed(e: Exception) = fail("동승석", e)
                }
                seat.registerSeatOccupancy(area, l)
                cleanups += { seat.unregisterSeatOccupancy(area, l) }
            }
        }

        safe("안전벨트") {
            val l = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(value: Boolean?) = ui {
                    beltBuckled = value
                    updateBelt()
                }

                override fun onFailed(e: Exception) = fail("안전벨트", e)
            }
            seat.registerSeatBeltBuckledState(driverSeat, l)
            cleanups += { seat.unregisterSeatBeltBuckledState(driverSeat, l) }
        }

        safe("안전벨트") {
            val safety = vehicle.getSafety()
            val l = object : SeatBeltNotBuckledWarningListener {
                override fun onSeatBeltNotBuckledWarningUpdated(value: Boolean?) = ui {
                    beltWarning = value
                    updateBelt()
                }

                override fun onFailed(e: Exception) = fail("안전벨트", e)
            }
            safety.registerSeatBeltNotBuckledWarning(driverSeat, l)
            cleanups += { safety.unregisterSeatBeltNotBuckledWarning(driverSeat, l) }
        }
    }

    // ─── Pleos: 뒷문 열림 → 동승자 ───
    private fun startDoors() {
        val door = vehicle.getDoor()
        listOf(DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT).forEach { value ->
            safe("뒷문") {
                val area = DoorArea(value)
                val l = object : DoorPositionListener {
                    override fun onPositionUpdated(pos: Int?) = ui {
                        if ((pos ?: 0) > 0) out.onRearDoorOpened()
                        out.onStatus("뒷문", "✓ $value = $pos")
                    }

                    override fun onFailed(e: Exception) = fail("뒷문", e)
                }
                door.registerDoorPosition(area, l)
                cleanups += { door.unregisterDoorPosition(area, l) }
            }
        }
    }

    // ─── Pleos: 앞 창문 열림 → 창문 ───
    private fun startWindows() {
        val window = vehicle.getWindow()
        listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT).forEach { value ->
            safe("창문") {
                val area = WindowArea(value)
                val l = object : WindowPositionListener {
                    override fun onWindowPositionUpdated(pos: Int?) = ui {
                        windowOpen[value] = (pos ?: 0) > 0
                        out.onSignal(TripEvent.WINDOW_OPEN, windowOpen.values.any { it })
                        out.onStatus("창문", "✓ $value = $pos")
                    }

                    override fun onFailed(e: Exception) = fail("창문", e)
                }
                window.registerWindowPosition(area, l)
                cleanups += { window.unregisterWindowPosition(area, l) }
            }
        }
    }

    // ─── Pleos: 배터리 잔량 → 배고픔 ───
    private fun startBattery() = safe("배터리") {
        val ev = vehicle.getEvBattery()
        val l = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                ui { out.onBattery(value) }
                ok("배터리", "$value%")
            }

            override fun onFailed(e: Exception) = fail("배터리", e)
        }
        ev.registerEvBatteryStateOfCharge(l)
        cleanups += { ev.unregisterEvBatteryStateOfCharge(l) }
    }

    // ─── Pleos: 연료 잔량(%) → 배고픔 (내연기관·하이브리드·수소) ───
    private fun startFuel() = safe("연료") {
        val fuel = vehicle.getFuel()
        val l = object : FuelPercentListener {
            override fun onFuelPercentUpdated(value: Int?) {
                ui { out.onFuel(value) }
                ok("연료", "$value%")
            }

            override fun onFailed(e: Exception) = fail("연료", e)
        }
        fuel.registerFuelPercent(l)
        cleanups += { fuel.unregisterFuelPercent(l) }
    }

    // ─── AAOS 표준: 기어, 주행 규제 ───
    private val gearCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val gear = value.value as? Int ?: return
            ui { out.onGear(gear) }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            ui { out.onStatus("기어", "✕ propertyId=$propertyId") }
        }
    }

    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val speed = value.value as? Float ?: return
            val time = value.timestamp
            ui { out.onSpeed(speed, time) }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            ui { out.onStatus("차속", "✕ propertyId=$propertyId") }
        }
    }

    private fun startCar() = safe("기어") {
        car = Car.createCar(appContext)
        cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        cpm?.registerCallback(gearCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        ok("기어", "연결")

        ux = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        val restricted = ux?.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: false
        ui { out.onUxRestricted(restricted) }
        ux?.registerListener { r ->
            ui { out.onUxRestricted(r.isRequiresDistractionOptimization) }
        }
    }

    // 차속은 사용자가 권한을 허용한 뒤에 시작 (급가속·급감속 감지)
    fun startSpeed() = safe("차속") {
        cpm?.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI)
        ok("차속", "감지 중")
    }

    fun release() {
        cleanups.asReversed().forEach { runCatching { it() } }
        cleanups.clear()
        runCatching { cpm?.unregisterCallback(gearCallback) }
        runCatching { cpm?.unregisterCallback(speedCallback) }
        runCatching { ux?.unregisterListener() }
        runCatching { vehicle.release() }
        runCatching { car?.disconnect() }
    }

    companion object {
        const val HEATER_TEMP_C = 26f // [가정] 에어컨 없이 이 온도 이상으로 맞추면 난방으로 봄
    }
}
