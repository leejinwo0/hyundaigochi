# 기름만 줘도 잘 커요

> **달린 만큼 자라는 내 차.** 차량 신호로 도트 캐릭터 차를 키우는 Pleos Connect 차량용 앱입니다.
> (현대자동차그룹 차량용 앱 개발 공모전 출품작이며, 공식 서비스가 아닙니다.)

## 실행 방법

1. Android Studio에서 이 폴더를 엽니다. (Open)
2. Gradle Sync 가 끝나면 Pleos Connect 에뮬레이터를 실행합니다.
3. ▶ Run 으로 설치합니다.

APK로 설치하려면 아래 명령을 사용합니다. (`-g` 는 권한을 처음부터 허용합니다.)

```
adb install -r -g apk/이진우_기름만줘도잘커요_release.apk
```

## 테스트 환경 및 필요한 설정

- 개발 도구: Android Studio, Kotlin, Jetpack Compose, minSdk 28
- Pleos SDK: `ai.pleos.playground:Vehicle:2.3.2`
  (Maven 저장소 `https://nexus-playground.pleos.ai/repository/maven-releases/` 는 `settings.gradle.kts` 에 설정되어 있습니다.)
- 급가속·급감속 감지에는 차속 런타임 권한이 필요합니다. `adb install -g` 로 설치하거나, 앱 차고의 "급가속·급감속 감지 켜기"에서 허용합니다.
- 차량 상태는 Pleos Connect Virtual Console 로 바꿔 가며 테스트했습니다.

```
adb forward tcp:8088 tcp:8088
```

브라우저에서 `http://127.0.0.1:8088/` 를 엽니다.

### 확인 방법

1. 앱을 처음 실행하면 모습, 별명·용도, 내 차 소개, 집중 육성을 고르는 시작 과정이 나옵니다.
2. Virtual Console에서 기어를 **D** 로 바꾸면 앱에는 "기록 중"만 남습니다.
3. 와이퍼를 켜고, 뒷문을 열고 닫고, Odometer 를 조금 올립니다.
4. 기어를 **P** 로 바꾸면 정산 화면이 나옵니다.
5. 앱의 **내 차 → 차량 연결 상태**에서 각 차량 신호가 들어오는지(✓/✕) 확인할 수 있습니다.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

- 에뮬레이터 버전: **Pleos Connect v2.1.0** (Android 14, API 34, x86_64, Google APIs)
- 화면 규격: **12.8인치, 2560 × 1440 px, 160 dpi**
- 에뮬레이터 설정: Default boot Cold, RAM 4GB, CPU 4코어

## APK 파일 위치

- `apk/이진우_기름만줘도잘커요_release.apk`
