# CALENDAR BOT

일정을 기록하고, 다음 주를 미리 받아보는 개인 일정 관리 웹 서비스입니다.
등록해 둔 일정을 **매주 일요일 21:00에 텔레그램으로** 보내 주고, 휴대폰 화면에서도 쓸 수 있습니다. (개인 프로젝트, 2026.11)

## 주요 기능

- **주간 브리핑(텔레그램)**: 내 정보 화면에서 연동 코드를 발급받아 봇과 계정을 연결하면, 일요일 21:00에 다음 주(월~일) 일정을 날짜순 목록으로 보냅니다. '지금 보내기'(10초 간격 제한)와 수신 켜기/끄기를 지원합니다.
- **일정 관리**: 월간 달력, 등록·수정·삭제, 시작 시간(HH:mm), 이미지 첨부(선택), 제목·내용 검색, 월별 통계(요일별 일정 수).
- **회원·관리자**: 가입 후 관리자가 승인해야 쓸 수 있습니다(PRE_USER → USER). 관리자는 사용자 목록 검색과 승인을 합니다.
- **보안**: 본인 일정만 접근(다른 사람의 일정 번호로 요청하면 404), 로그인 시도 제한(같은 아이디·IP에서 연속 5회 실패하면 10분 잠금), 이미지가 아닌 파일 업로드 거부.
- **모바일 대응**: 768px 이하에서 접는 메뉴, 달력 칸은 일정 개수만 보이고 날짜를 누르면 목록, 전체 화면 모달.

## 기술 스택

| 구분 | 사용 |
| --- | --- |
| 서버 | Java 17, Spring Boot 4.1.1, Spring Security(폼 로그인), 스케줄러 |
| 데이터 | MySQL 8.x, Spring Data JPA(쓰기·단순 조회), MyBatis(검색·통계 같은 동적 조회) |
| 화면 | Thymeleaf, HTML/CSS, 순수 JavaScript(fetch) |
| 연동 | Telegram Bot API(롱 폴링으로 연동, 일정 발송), Gmail SMTP(비밀번호 찾기) |
| 테스트 | JUnit 5, MockMvc, AssertJ |

## 실행 방법

1. JDK 17과 MySQL을 준비하고, 사용할 DB(기본 이름 `DB_CALENDAR`)를 만듭니다. 테이블은 앱이 처음 뜰 때 만듭니다(`ddl-auto=update`).
2. **환경변수**를 설정합니다. 값은 저장소에 적지 않습니다.

   | 이름 | 용도 | 필수 |
   | --- | --- | --- |
   | `DB_URL` | JDBC 주소(기본 `jdbc:mysql://localhost:3306/DB_CALENDAR`) | 선택 |
   | `DB_USERNAME`, `DB_PASSWORD` | DB 계정 | 필수 |
   | `MAIL_USERNAME`, `MAIL_PASSWORD` | 비밀번호 찾기 메일(Gmail 앱 비밀번호) | 필수(임시 값도 됨) |
   | `ADMIN_ID`, `ADMIN_PASSWORD`, `ADMIN_MAIL` | 첫 관리자 계정(비어 있으면 만들지 않음) | 선택 |
   | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME` | 텔레그램 봇(비어 있으면 연동·발송만 막히고 앱은 뜸) | 선택 |
   | `LOGIN_MAX_FAIL`, `LOGIN_LOCK_MINUTES` | 로그인 잠금 횟수(5)와 시간(10분) | 선택 |
   | `SERVER_PORT` | 포트(기본 8090) | 선택 |

3. 실행합니다.

   ```powershell
   cd calendar
   .\gradlew.bat bootRun
   ```

   브라우저에서 `http://localhost:8090`을 엽니다. 휴대폰은 같은 와이파이에서 `http://<PC의 IP>:8090`으로 열 수 있습니다.

## 테스트

```powershell
cd calendar
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
.\gradlew.bat test
```

- 318개 중 317개는 DB 없이 통과합니다. `CalendarApplicationTests`(contextLoads) 1개는 위의 DB·메일·관리자 환경변수가 있어야 통과합니다.
- 소유자 검증, 로그인 잠금, 연동 코드 만료·재사용 방지, 브리핑 대상 선정, 화면 템플릿 렌더링, 모바일 CSS 규칙 등을 검사합니다.
- 이 밖에 실제 MySQL로 가입 → 승인 → 로그인 → 일정 → 검색 → 통계 → 소유자 검증 → 브리핑 흐름을 실행해 확인했습니다.

## 폴더 구조

```
calendar/
  src/main/java/com/office/calendar/
    config/     보안 설정, 시간·스케줄러 설정
    member/     회원, 로그인, 로그인 잠금(LOGIN_LOCK)
    planner/    일정(달력, 검색, 통계, 이미지 업로드)
    admin/      관리자(사용자 목록·승인)
    brief/      주간 브리핑 생성·발송 스케줄러
    telegram/   텔레그램 연동 코드·롱 폴링·발송
    common/     공통(검색어 처리 등)
  src/main/resources/
    templates/  Thymeleaf 화면
    static/     CSS, JS, 이미지
    mybatis/    검색·통계 SQL
```

## 아직 확인하지 못한 것과 한계

- **휴대폰 실기기**: 브라우저의 좁은 폭과 실제 휴대폰 접속으로 주요 화면을 확인했고, 모든 화면을 하나하나 확인하지는 못했습니다.
- 승인한 권한은 이미 로그인한 사용자가 다시 로그인해야 반영됩니다.
- 일정의 월·일을 서버가 검증하지 않아 2월 31일 같은 날짜도 저장됩니다(통계에서는 제외).
- CSRF 보호는 꺼져 있습니다.

## 향후 계획

CSRF 보호, 계정 정지, 자연어 일정 등록("다음 주 화요일 3시 치과"), 태블릿 폭 배치, 배포 설정.

## 보안 안내

비밀 값(DB·메일 비밀번호, 봇 토큰)은 환경변수로만 받고 저장소에는 두지 않습니다.

## 이미지 출처

홈 화면 사진: [Hanna Pad, Pexels](https://www.pexels.com/photo/smartphone-on-white-open-planner-8533225/) (Pexels 라이선스, 무료 사용)
