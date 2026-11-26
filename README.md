# Psychics (paper 기반의 능력자 플러그인)

## Paper 26.3 포트

이 저장소는 [monun/psychics](https://github.com/monun/psychics)의 GPL-3.0
소스를 기반으로 합니다. Java 25와 Paper 26.3이 필요합니다.

공통 설정 스키마, 스케줄링, 명령, 인벤토리 UI와 표시 엔티티 기능은
Kolor API로 처리합니다. Psychics 전용 설정 어노테이션과 능력 규칙은
Psychics에 남기며, Tap 및 InvFX 런타임 의존성은 없습니다. Kolor 확장은
특정 제3자 구현을 복제하지 않고 독립적으로 작성했습니다.

전체 능력 모듈이 빌드 및 배포 ZIP에 포함됩니다. Paper 26.3에서의 빌드
검증은 완료했지만, 실제 플레이어가 참여하는 모든 능력의 게임 내 동작은
별도 테스트 서버에서 확인하세요.

### 빌드와 설치

먼저 서브모듈을 포함해 저장소를 복제합니다.

```powershell
git clone --recurse-submodules https://github.com/Shai0717/psychics.git
```

이미 복제했다면 `git submodule update --init --recursive`를 실행하세요.
Windows에서 Java 25의 `JAVA_HOME`을 설정한 뒤 다음을 실행합니다.

```powershell
.\gradlew.bat build distribution
```

`build\distributions`의 ZIP을 서버 폴더에 풀고 재시작합니다. ZIP은
`plugins\Kolor.jar`, `plugins\Psychics.jar`와
`plugins\Psychics\abilities`, `plugins\Psychics\psychics` 폴더를
포함합니다. Psychics는 Kolor를 필수 플러그인으로 사용하므로 두 플러그인을
함께 설치하세요. 최초 실행 시 Kolor가 Kotlin 표준 라이브러리를 Maven Central에서
내려받아야 합니다. 기존 설정은 덮어쓰기 전에 백업하세요.

예제 능력 `adventurer`에는 다음 스킬이 들어 있습니다.

| 스킬 | 사용 아이템 | 효과 | 기본 마나 / 대기시간 |
| --- | --- | --- | --- |
| 돌진 | 깃털 | 보는 방향으로 이동 | 8 / 5초 |
| 회복 | 가스트의 눈물 | 체력 6 회복 | 15 / 12초 |
| 충격파 | 브리즈 막대 | 주변 적 공격 및 넉백 | 20 / 10초 |

아이템을 들고 우클릭해 사용합니다. 예제 설정은
`plugins\Psychics\psychics\adventurer.yml`이며 첫 실행 후 각 스킬의
설정값이 자동으로 추가됩니다.

### 명령어

- `/psychics attach <접속 중인 플레이어> adventurer` — 능력 부여
- `/psychics detach <플레이어>` — 능력 제거
- `/psychics supply` — 자신의 스킬 아이템 지급
- `/psychics info [능력]`, `/ability` — 능력 정보
- `/psychics enchant <0~5>` — 들고 있는 아이템의 초능력 레벨 지정
- `/psychics reload` — 능력 및 설정 다시 읽기

관리 명령은 기본적으로 OP에게만 허용되는 `psychics.commands` 권한이
필요합니다. 다른 플레이어에게 아이템을 지급하려면 해당 플레이어가
`/psychics supply`를 실행할 권한을 가져야 합니다.

---

현재 개발 진행중으로 배포버전이 없습니다.

* [docs](https://monun.github.io/psychics/)

[![GitHub Sponsors](https://img.shields.io/github/sponsors/Shai0717?style=flat&logo=githubsponsors&color=ea4aaa)](https://github.com/sponsors/Shai0717)
