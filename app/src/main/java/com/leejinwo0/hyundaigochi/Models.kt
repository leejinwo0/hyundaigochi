package com.leejinwo0.hyundaigochi

// 순수 Kotlin (Android 의존 없음)

enum class StatType(val label: String, val hint: String) {
    ENDURANCE("지구력", "달린 거리"),
    DILIGENCE("성실함", "함께 달린 날"),
    ADAPT("적응력", "비·밤·추위·더위"),
    SOCIAL("사교성", "동승자"),
    ADVENTURE("모험심", "장거리·창문 열고 달리기"),
    SAFETY("안전", "급가속·급감속·벨트")
}

// 용도: 고른 용도에 맞는 능력치는 1.2배 빨리 자람 (최대 3개까지 고를 수 있고, 겹치는 능력치도 1.2배는 한 번만)
enum class Usage(
    val label: String,
    val desc: String,
    val boosts: Set<StatType>,
    val accent: Long,
    val icon: List<String> // 10 x 10 도트 아이콘 (K 테두리, B 포인트색, L 밝은 포인트색, Y 노랑, R 빨강, W 흰색)
) {
    COMMUTE(
        "출퇴근", "집과 일터를 오가요",
        setOf(StatType.DILIGENCE, StatType.ENDURANCE),
        0xFF4F6BD9,
        listOf(
            "..........",
            "...KKKK...",
            "...K..K...",
            ".KKKKKKKK.",
            ".KBBBBBBK.",
            ".KBBYYBBK.",
            ".KBBBBBBK.",
            ".KBBBBBBK.",
            ".KKKKKKKK.",
            ".........."
        )
    ),
    SCHOOL(
        "통학", "학교와 집을 오가요",
        setOf(StatType.DILIGENCE, StatType.SOCIAL),
        0xFFF07A2A,
        listOf(
            "...KKKK...",
            "..KBBBBK..",
            "..KBBBBK..",
            ".KBBBBBBK.",
            ".KBYYYYBK.",
            ".KBBBBBBK.",
            ".KBLLLLBK.",
            ".KBLLLLBK.",
            ".KBBBBBBK.",
            "..KKKKKK.."
        )
    ),
    FAMILY(
        "가족", "가족을 태우고 달려요",
        setOf(StatType.SOCIAL, StatType.SAFETY),
        0xFFD93D4F,
        listOf(
            "..........",
            ".KKK..KKK.",
            "KRRRKKRRRK",
            "KRWRRRRRRK",
            "KRRRRRRRRK",
            ".KRRRRRRK.",
            "..KRRRRK..",
            "...KRRK...",
            "....KK....",
            ".........."
        )
    ),
    TRAVEL(
        "여행", "멀리, 오래 달리는 걸 좋아해요",
        setOf(StatType.ENDURANCE, StatType.ADVENTURE),
        0xFF3FB8AF,
        listOf(
            "...KKKK...",
            "...K..K...",
            ".KKKKKKKK.",
            ".KBBLLBBK.",
            ".KBBLLBBK.",
            ".KBBLLBBK.",
            ".KBBLLBBK.",
            ".KKKKKKKK.",
            "..KK..KK..",
            ".........."
        )
    ),
    CAMPING(
        "캠핑·차박", "자연 속으로 떠나요",
        setOf(StatType.ADAPT, StatType.ADVENTURE),
        0xFF3FA35B,
        listOf(
            "..........",
            "....KK....",
            "...KBBK...",
            "..KBBBBK..",
            ".KBBLLBBK.",
            "KBBBLLBBBK",
            "KBBBLLBBBK",
            "KKKKKKKKKK",
            "..........",
            ".........."
        )
    ),
    WORK(
        "업무", "거래처와 현장을 돌아요",
        setOf(StatType.DILIGENCE, StatType.ADAPT),
        0xFF6B7280,
        listOf(
            "...KKKK...",
            "..KKBBKK..",
            ".KKWWWWKK.",
            ".KWWWWWWK.",
            ".KWKKKKWK.",
            ".KWWWWWWK.",
            ".KWKKKKWK.",
            ".KWWWWWWK.",
            ".KKKKKKKK.",
            ".........."
        )
    ),
    DELIVERY(
        "배달·운송", "짐을 싣고 부지런히 달려요",
        setOf(StatType.ADAPT, StatType.ENDURANCE),
        0xFFB9824A,
        listOf(
            "..........",
            "..KKKKKK..",
            ".KLLBBLLK.",
            "KKKKKKKKKK",
            "KBBBYYBBBK",
            "KBBBYYBBBK",
            "KBBBBBBBBK",
            "KKKKKKKKKK",
            "..........",
            ".........."
        )
    ),
    DRIVE(
        "주말 드라이브", "바람 쐬러 가볍게 나서요",
        setOf(StatType.SOCIAL, StatType.ADVENTURE),
        0xFFF2C230,
        listOf(
            "....YY....",
            ".Y..YY..Y.",
            "..YYYYYY..",
            ".YYYYYYYY.",
            "YYYYYYYYYY",
            "YYYYYYYYYY",
            ".YYYYYYYY.",
            "..YYYYYY..",
            ".Y..YY..Y.",
            "....YY...."
        )
    );
}

// 연료 종류: 문구(연료/배터리, 주유/충전)를 알맞게 바꾸는 데 씀
enum class FuelType(val label: String, val energyWord: String, val refill: String) {
    GASOLINE("휘발유", "연료", "주유"),
    DIESEL("경유", "연료", "주유"),
    LPG("LPG", "연료", "충전"),
    HYBRID("하이브리드", "연료", "주유"),
    ELECTRIC("전기", "배터리", "충전"),
    HYDROGEN("수소", "수소", "충전")
}

// 출고 연월(yyyyMM) 계산: 차 나이와 생일 달
object CarAge {
    fun ym(year: Int, month: Int): Int = year * 100 + month

    fun isValid(ym: Int, nowYm: Int): Boolean {
        val y = ym / 100
        val m = ym % 100
        return y in 1990..9999 && m in 1..12 && ym <= nowYm
    }

    fun months(birthYm: Int, nowYm: Int): Int =
        (nowYm / 100 - birthYm / 100) * 12 + (nowYm % 100 - birthYm % 100)

    fun text(birthYm: Int, nowYm: Int): String {
        val m = months(birthYm, nowYm).coerceAtLeast(0)
        return when {
            m < 12 -> "${m}개월"
            m % 12 == 0 -> "${m / 12}살"
            else -> "${m / 12}살 ${m % 12}개월"
        }
    }

    // 출고한 달과 같은 달이고 1년 이상 지났으면 생일 달
    fun isBirthdayMonth(birthYm: Int, nowYm: Int): Boolean =
        months(birthYm, nowYm) >= 12 && nowYm % 100 == birthYm % 100

    fun nth(birthYm: Int, nowYm: Int): Int = nowYm / 100 - birthYm / 100
}

data class CarColor(val name: String, val argb: Long)

// 무채색 → 빨주노초민파보분 순서. 기본 색은 0번(블랙)
object CarColors {
    val all = listOf(
        CarColor("블랙", 0xFF1F2227),
        CarColor("화이트", 0xFFF4F5F7),
        CarColor("실버", 0xFFB9BFC8),
        CarColor("그레이", 0xFF6B7280),
        CarColor("레드", 0xFFD93D4F),
        CarColor("오렌지", 0xFFF07A2A),
        CarColor("옐로", 0xFFF2C230),
        CarColor("그린", 0xFF3FA35B),
        CarColor("민트", 0xFF3FB8AF),
        CarColor("블루", 0xFF3B7BE8),
        CarColor("퍼플", 0xFF7C5CE0),
        CarColor("핑크", 0xFFEE6FA3)
    )
}

// 차종은 직접 그린 단순 도트이며 로고·엠블럼은 그리지 않음. 차량 이름은 글자로만 표기.
// [확인 필요] 차종 이름·디자인 사용 조건은 공모전 규정/FAQ 확인, 최신 라인업 확인
// 스프라이트 문자: B 차체, D 어두운 부분, W 유리, E 눈, L 헤드램프, R 후미등, T 타이어, G 휠
// 24칸 폭, 바닥이 14번째 줄에 닿도록 아래 정렬해서 그림 (차는 오른쪽을 봄)
data class CarModel(val id: String, val label: String, val kind: String, val sprite: List<String>)

object CarModels {
    val all: List<CarModel> = listOf(
        CarModel(
            id = "casper", label = "캐스퍼", kind = "경형 SUV",
            sprite = listOf(
            "....BBBBBBBBBBBB........",
            "...BBWWWWBWWWWWBB.......",
            "...BWWWWWBWWWEWWB.......",
            "...BWWWWWBWWWWWWB.......",
            "..BBBBBBBBBBBBBBBB......",
            "..BBBBBBBBBBBBBBBBL.....",
            "RBBBBBBBBBBBBBBBBBLL....",
            "DBBBBBBBBBBBBBBBBBBD....",
            "DBBBBTTTBBBBBTTTBBBD....",
            "..BBBTGTBBBBBTGTBBB.....",
            "......TTT.....TTT......."
            )
        ),
        CarModel(
            id = "avante", label = "아반떼", kind = "준중형 세단",
            sprite = listOf(
            "........BBBBBBBB........",
            "......BBWWWWBWWWBB......",
            "....BBWWWWWWBWWEWWBB....",
            "..BBBBBBBBBBBBBBBBBBB...",
            "..BBBBBBBBBBBBBBBBBBBL..",
            "RBBBBBBBBBBBBBBBBBBBBLL.",
            "DBBBBBBBBBBBBBBBBBBBBBD.",
            "DBBBBTTTBBBBBBBTTTBBBBD.",
            "..BBBTGTBBBBBBBTGTBBBB..",
            "......TTT.......TTT....."
            )
        ),
        CarModel(
            id = "grandeur", label = "그랜저", kind = "대형 세단",
            sprite = listOf(
            ".........BBBBBBBB.......",
            ".......BBWWWWBWWWBB.....",
            ".....BBWWWWWWBWWEWWBB...",
            "..BBBBBBBBBBBBBBBBBBBBB.",
            "..BBBBBBBBBBBBBBBBBBBBBL",
            "RBBBBBBBBBBBBBBBBBBBBBLL",
            "DBBBBBBBBBBBBBBBBBBBBBBD",
            "DBBBBTTTBBBBBBBBTTTBBBBD",
            "..BBBTGTBBBBBBBBTGTBBBB.",
            "......TTT........TTT...."
            )
        ),
        CarModel(
            id = "tucson", label = "투싼", kind = "SUV",
            sprite = listOf(
            "....BBBBBBBBBBBBB.......",
            "...BBWWWWWBWWWWWBB......",
            "..BBWWWWWWBWWWEWWBB.....",
            "..BWWWWWWWBWWWWWWWB.....",
            "..BBBBBBBBBBBBBBBBBB....",
            "..BBBBBBBBBBBBBBBBBBL...",
            "RBBBBBBBBBBBBBBBBBBBLL..",
            "DBBBBBBBBBBBBBBBBBBBBD..",
            "DBBBBTTTBBBBBBBTTTBBBD..",
            "..BBBTGTBBBBBBBTGTBBB...",
            "......TTT.......TTT....."
            )
        ),
        CarModel(
            id = "palisade", label = "팰리세이드", kind = "대형 SUV",
            sprite = listOf(
            "..BBBBBBBBBBBBBBBBB.....",
            ".BBWWWWWWBWWWWWWWWBB....",
            ".BWWWWWWWBWWWWWEWWWB....",
            ".BWWWWWWWBWWWWWWWWWB....",
            ".BBBBBBBBBBBBBBBBBBBB...",
            ".BBBBBBBBBBBBBBBBBBBBL..",
            "RBBBBBBBBBBBBBBBBBBBBLL.",
            "DBBBBBBBBBBBBBBBBBBBBBD.",
            "DBBBBBTTTBBBBBBBTTTBBBD.",
            "..BBBBTGTBBBBBBBTGTBBB..",
            "......TTT........TTT...."
            )
        ),
        CarModel(
            id = "ioniq5", label = "아이오닉 5", kind = "전기 SUV",
            sprite = listOf(
            "......BBBBBBBBB.........",
            "....BBWWWWWBWWWBB.......",
            "...BBWWWWWWBWWEWBBB.....",
            "..BBBBBBBBBBBBBBBBBBB...",
            "..BBBBBBBBBBBBBBBBBBBL..",
            "RBBBBBBBBBBBBBBBBBBBBLL.",
            "DBBBBBBBBBBBBBBBBBBBBBD.",
            "DBBBBTTTBBBBBBBTTTBBBBD.",
            "..BBBTGTBBBBBBBTGTBBBB..",
            "......TTT.......TTT....."
            )
        ),
        CarModel(
            id = "staria", label = "스타리아", kind = "승합",
            sprite = listOf(
            "..BBBBBBBBBBBBBBBBBB....",
            ".BBWWWWWBWWWWBWWWWWBB...",
            ".BWWWWWWBWWWWBWWWEWWB...",
            ".BWWWWWWBWWWWBWWWWWWB...",
            ".BBBBBBBBBBBBBBBBBBBBB..",
            ".BBBBBBBBBBBBBBBBBBBBBL.",
            "RBBBBBBBBBBBBBBBBBBBBBLL",
            "DBBBBBBBBBBBBBBBBBBBBBBD",
            "DBBBBBTTTBBBBBBBTTTBBBBD",
            "..BBBBTGTBBBBBBBTGTBBBB.",
            "......TTT........TTT...."
            )
        ),
        CarModel(
            id = "porter", label = "포터", kind = "소형 트럭",
            sprite = listOf(
            "..........BBBBBBB.......",
            "..........BWWWBWWBB.....",
            "D.......D.BWWWBWEWB.....",
            "DDDDDDDDD.BBBBBBBBBB....",
            "DBBBBBBBDBBBBBBBBBBBL...",
            "RBBBBBBBDBBBBBBBBBBBLL..",
            "DBBBBBBBBBBBBBBBBBBBBD..",
            "DBBTTTBBBBBBBBBBTTTBBD..",
            "..BBTGTBBBBBBBBBTGTBB...",
            "....TTT..........TTT...."
            )
        )
    )

    fun byId(id: String): CarModel = all.firstOrNull { it.id == id } ?: all[1]
}

// 번호판: 사용자가 직접 입력(선택), 기기 안에만 저장
object Plate {
    private val pattern = Regex("^\\d{2,3}[가-힣]\\d{4}$")

    // 입력 중에는 숫자, 완성된 한글, 입력 중인 자모(ㄱ~ㅣ)만 남김
    fun normalize(input: String): String =
        input.filter { it.isDigit() || it in '가'..'힣' || it in 'ㄱ'..'ㅣ' }.take(8)

    fun isComplete(plate: String): Boolean = pattern.matches(plate)

    fun isValid(plate: String): Boolean = plate.isEmpty() || isComplete(plate)

    fun display(plate: String): String =
        if (isComplete(plate)) plate.dropLast(4) + " " + plate.takeLast(4) else ""
}
