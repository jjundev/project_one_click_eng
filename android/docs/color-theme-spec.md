# Android Color Theme Spec (Light/Dark)

## 목적
- 역할 기반 색상(`colorBackground`, `colorSurface`, `colorSurfaceVariant`, `colorOnBackground`, `colorOnSurfaceVariant`)을 라이트/다크 모드에서 고정 정의한다.
- `Theme.MaterialComponents.DayNight.NoActionBar`를 유지하고, 테마 attribute 매핑으로 일관성을 확보한다.

## 역할별 색상 매핑

### 라이트 모드
| 요소 | 색상 | Attribute |
|---|---|---|
| 배경 (최하단) | `#F5F5F5` | `colorBackground` |
| Toolbar 배경 | `#F5F5F5` | `colorSurface` |
| Card 배경 | `#FFFFFF` | `colorSurfaceVariant` |
| 메인 글씨 | `#000000` | `colorOnBackground` |
| 보조 글씨 | `#BDBDBD` | `colorOnSurfaceVariant` |

### 다크 모드
| 요소 | 색상 | Attribute |
|---|---|---|
| 배경 (최하단) | `#121212` | `colorBackground` |
| Toolbar 배경 | `#363537` | `colorSurface` |
| Card 배경 | `#363537` | `colorSurfaceVariant` |
| 메인 글씨 | `#FFFFFF` | `colorOnBackground` |
| 보조 글씨 | `#757575` | `colorOnSurfaceVariant` |

## 요소 -> Attribute 매핑 규칙
- 앱 최하단 배경은 `?attr/colorBackground`를 기준으로 사용한다.
- 상단 바/표면 계층은 `?attr/colorSurface`를 사용한다.
- 카드/보조 컨테이너는 `?attr/colorSurfaceVariant`를 사용한다.
- 기본 본문 텍스트는 `?attr/colorOnBackground`를 사용한다.
- 보조 텍스트/힌트/서브레이블은 `?attr/colorOnSurfaceVariant`를 사용한다.
- 윈도우 기본 배경은 `android:windowBackground`에 `colorBackground`와 동일 톤을 매핑한다.

## 적용 범위
- `values/colors.xml`, `values-night/colors.xml`에 역할 색상 토큰 정의
- `values/themes.xml`, `values-night/themes.xml`에 role attribute 매핑
- 기존 브랜드 계열 색상(`purple_*`, `teal_*`, `toss_blue` 등) 유지

## 비범위
- 기존 화면 XML/Java의 직접 색상 참조(`@color/...`) 대량 치환
- Material3 테마 전환
