# TangTaMukei

Paper **26.3** 로비 플러그인 — Java 유저와 **Bedrock(GeyserMC)** 유저 모두 지원.

## 기능
- 접속하면 로비 스폰으로 이동 + 게임모드/체력/인벤토리 초기화 + 환영 타이틀·메시지·사운드
- 핫바 아이템: **게임 선택 나침반**, **플레이어 숨기기/보이기** 토글
- 게임 선택 메뉴
  - Java: 상자 GUI
  - Bedrock: Floodgate 가 있으면 **베드락 전용 버튼 폼**, 없으면 상자 GUI (Geyser 가 변환)
  - 메뉴 동작: `server:<이름>` (BungeeCord/Velocity 서버 이동), `command:<명령어>`, `message:<텍스트>`
- 로비 보호: 블록 파괴/설치, 데미지, 배고픔, 아이템 버리기/줍기, 인벤토리 이동 차단, 낙사 시 스폰 복귀
  - `tangtamukei.build` 권한 + 크리에이티브 모드면 보호 무시 (로비 꾸미기용)

## 명령어
| 명령어 | 설명 | 권한 |
|---|---|---|
| `/lobby` (`/hub`, `/spawn`, `/로비`) | 로비로 이동 | 모두 |
| `/setlobby` | 현재 위치를 로비 스폰으로 설정 | `tangtamukei.admin` |
| `/lobbyreload` | config.yml 다시 불러오기 | `tangtamukei.admin` |

## 빌드
Java 25 필요 (Paper 26.x 요구사항).
```
./gradlew build
```
결과물: `build/libs/TangTaMukei-1.0.0.jar` → 서버 `plugins/` 폴더에 넣기.

## 설정
`plugins/TangTaMukei/config.yml` 에서 문구(MiniMessage), 아이템, 메뉴, 보호 옵션을 바꿀 수 있습니다.
