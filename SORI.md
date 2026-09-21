# Sori Desktop

Sori Desktop는 Windows용 Sori다. [SimpMusic](https://github.com/maxrave-dev/SimpMusic)(GPL-3.0)을 포크했다.
Android용 Sori는 [saootikim/Sori](https://github.com/saootikim/Sori)(Metrolist 포크)에 따로 있다.

## 설계 원칙

- Sori 변경은 **메인 저장소에만** 한다. `core` 서브모듈(maxrave-dev/core)은 원본 그대로 쓴다. 그래야 원본을 병합할 때 충돌이 적다.
- 배포 대상은 Windows x64 MSI 하나뿐이다. Android, macOS, Linux 빌드는 건드리지 않는다.
- 친구들 PC에 바로 설치할 수 있게 한다. 인증서 설치나 관리자 권한 없이 사용자 단위로 설치된다.

## 원본과 다른 점

| 파일 | 변경 |
|---|---|
| `composeApp/src/commonMain/composeResources/values/app_name.xml` | 앱 이름 `Sori` |
| `composeApp/src/jvmMain/.../CrashDialog.kt` | 오류 창 제목 |
| `composeApp/icon/circle_app_icon.{png,ico}`, `composeResources/drawable/{circle_app_icon,app_icon}.png` | Sori 아이콘 (음파 막대, 보라→코랄) |
| `desktopApp/build.gradle.kts` | `packageName = "Sori"`, MSI 버전 계산, 고정 `upgradeUuid`, 사용자 단위 설치, 시작 메뉴와 바로가기 |
| `gradle/libs.versions.toml` | `version-name = "<원본>-sori.<리비전>"` |
| `composeApp/src/jvmMain/.../sori/SoriUpdateRepository.kt` (신규) | 업데이트 확인 대상을 `saootikim/Sori-Desktop` 릴리스로 변경 |
| `composeApp/src/jvmMain/.../DesktopApp.kt` | 위 저장소 구현을 Koin으로 덮어쓰기 (한 줄) |
| `composeApp/src/commonMain/.../App.kt` | 업데이트 알림의 "다운로드" 링크 → 내 릴리스 페이지 |
| `.github/workflows/sori-desktop-release.yml` (신규) | Windows 러너에서 MSI를 빌드하고 릴리스 |

원본 워크플로(`android.yml`, `android-release.yml`, `desktop-package.yml`, `pr-triage.yml`)는 파일은 그대로 두고 GitHub에서 비활성화만 했다.

## 버전 규칙

- `version-name = "X.Y.Z-sori.R"` (예: `2.1.0-sori.1`)
- 앱은 릴리스 태그 `vX.Y.Z-sori.R`이 자기 버전과 다르면 업데이트 알림을 띄운다.
- MSI 설치 파일 버전은 숫자 세 자리만 받는다. 그래서 `X.Y.(Z×100+R)`로 계산한다 (예: `2.1.1`). `upgradeUuid`가 고정이라 새 MSI를 설치하면 기존 버전이 교체된다.
- 원본이 `2.2.0`이 되면 → `2.2.0-sori.1`부터 다시 시작한다.

## 릴리스 방법

1. `gradle/libs.versions.toml`의 `version-name`을 올린다.
2. `dev` 브랜치에 push한다.
3. `sori-desktop-release.yml`이 `Sori-<MSI버전>.msi`를 빌드하고 릴리스 `v<version-name>`에 올린다.
4. 친구들 앱은 다음 실행 때 업데이트 알림을 띄운다. "다운로드"를 누르면 릴리스 페이지가 열린다.

수동 실행: `gh workflow run sori-desktop-release.yml -R saootikim/Sori-Desktop`

## 원본 동기화

```bash
git fetch upstream
git merge upstream/dev
git submodule update --init --recursive
# 충돌은 위 표의 파일에서만 난다. 원본 version-name이 바뀌었으면 위 규칙대로 맞춘다.
git push origin dev
```

## 알려진 제약

- **로그인:** 브라우저에서 YouTube Music 쿠키를 복사해 붙여넣는 방식이다 (SimpMusic 방식 그대로). SimpMusic의 휴대폰 QR 로그인은 SimpMusic Android 앱에서만 된다. Sori Android와는 연동되지 않는다.
- **데이터 위치:** `%USERPROFILE%\.simpmusic`. 경로가 `core`에 정의돼 있어서 바꾸지 않았다. 같은 PC에 SimpMusic도 설치돼 있으면 데이터를 같이 쓴다.
- **서명 안 된 MSI:** 코드 서명 인증서가 없어서 SmartScreen이 "Windows의 PC 보호" 경고를 띄운다. "추가 정보 → 실행"을 누르면 설치된다.

## 로컬 빌드

JDK 21이 필요하다. 저장소 경로에는 한글이 들어가면 안 된다 (`C:\dev\sori-desktop`).

```bash
./gradlew :composeApp:mpvSetupAll          # libmpv 네이티브 다운로드 (처음 한 번)
./gradlew :desktopApp:run                  # 개발 실행
./gradlew :desktopApp:packageReleaseMsi    # 설치 파일 → desktopApp/build/compose/binaries/main-release/msi/
```
