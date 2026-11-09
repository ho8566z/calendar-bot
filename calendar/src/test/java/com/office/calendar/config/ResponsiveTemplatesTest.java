package com.office.calendar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 모바일 대응(nfr-001): 모든 화면이 기기 폭에 맞춰 열리도록 viewport 메타를 갖는지 확인한다.
class ResponsiveTemplatesTest {

    private static final Path TEMPLATES = Paths.get("src/main/resources/templates");

    // include/ 아래는 <head>가 없는 조각(fragment)이라 제외한다
    private static List<Path> pages() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            return files.filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> !TEMPLATES.relativize(p).startsWith("include"))
                    .collect(Collectors.toList());
        }
    }

    @Test
    @DisplayName("화면 템플릿이 있다(경로가 바뀌어 검사가 비어 통과하는 일을 막는다)")
    void pagesExist() throws IOException {
        assertThat(pages().size()).isGreaterThanOrEqualTo(13);
    }

    @Test
    @DisplayName("모든 화면에 viewport 메타(width=device-width, initial-scale=1)가 정확히 하나 있다")
    void everyPageHasViewport() throws IOException {
        for (Path page : pages()) {
            String html = Files.readString(page, StandardCharsets.UTF_8);

            assertThat(html).as(page.toString()).contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            assertThat(html.split("name=\"viewport\"", -1).length - 1).as(page + " viewport 중복").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("공통 머리글에 MENU 버튼과 메뉴 스크립트가 있고, 링크 사이에 &nbsp; 빈 글자를 두지 않는다")
    void headerHasMenuToggle() throws IOException {
        String html = Files.readString(TEMPLATES.resolve("include/header_nav_footer.html"), StandardCharsets.UTF_8);
        String withoutComments = html.replaceAll("(?s)<!--.*?-->", "");

        assertThat(withoutComments).contains("id=\"menu_toggle\"").contains("aria-expanded=\"false\"").contains("@{/js/menu.js}");
        assertThat(withoutComments).doesNotContain("&nbsp;");
    }

    @Test
    @DisplayName("공통 스타일에 768px 이하 모바일 규칙과 메뉴 열림 규칙이 있다")
    void commonCssHasMobileRules() throws IOException {
        Path css = Paths.get("src/main/resources/static/css");
        String header = Files.readString(css.resolve("header_nav_footer.css"), StandardCharsets.UTF_8);
        String common = Files.readString(css.resolve("common.css"), StandardCharsets.UTF_8);

        assertThat(header).contains("@media (max-width: 768px)").contains("body.menu_open #header_wrap").contains("body.menu_open nav");
        assertThat(common).contains("@media (max-width: 768px)").contains("font-size: 16px");
    }

    @Test
    @DisplayName("달력 화면에 날짜별 일정 목록 패널이 있고, CSS 는 PC에서 숨기고 모바일에서만 보이며 칸에는 개수만 보인다")
    void calendarMobileLayout() throws IOException {
        String html = Files.readString(TEMPLATES.resolve("planner/home.html"), StandardCharsets.UTF_8);
        String css = Files.readString(Paths.get("src/main/resources/static/css/planner.css"), StandardCharsets.UTF_8);
        int mobileStart = css.indexOf("@media (max-width: 768px)");

        assertThat(html).contains("id=\"day_list\"").contains("id=\"day_list_title\"").contains("id=\"day_list_items\"").contains("id=\"day_list_add\"");
        assertThat(mobileStart).isGreaterThan(0);

        // PC 폭: 패널은 숨김. 모바일 규칙은 미디어 쿼리 안에서만 보이게 한다
        String desktopPart = css.substring(0, mobileStart);
        String mobilePart = css.substring(mobileStart);
        assertThat(desktopPart).containsPattern("#day_list\\s*\\{\\s*display:\\s*none;");
        assertThat(mobilePart).containsPattern("#day_list\\s*\\{\\s*display:\\s*block;");

        // 모바일 폭: 제목 목록은 숨기고 data-count 개수를 보여 주며 고정 폭 1400px 을 풀어 준다
        assertThat(mobilePart).contains("ul.plan").contains("data-count").contains("attr(data-count)").contains("table-layout: fixed");
        assertThat(mobilePart).containsPattern("#section_wrap\\s*\\{\\s*width:\\s*auto;");

        // 년·월 선택(이전/다음 버튼 + 선택 상자 2개)이 좁은 폭에서 줄바꿈되지 않고 한 줄에 들어간다(실제 브라우저 375px 확인에서 발견)
        assertThat(mobilePart).contains("flex-wrap: nowrap").contains("width: 72px");
    }

    @Test
    @DisplayName("폭을 481px 이상으로 고정한 스타일 파일은 모두 768px 이하 모바일 규칙을 함께 가진다(새 CSS 가 PC 폭에 고정되는 것을 막는다)")
    void fixedWideCssHasMobileOverride() throws IOException {
        Path cssDir = Paths.get("src/main/resources/static/css");
        // width: 1000px 같은 고정 폭(min-width·max-width 는 제외)
        java.util.regex.Pattern fixedWidth = java.util.regex.Pattern.compile("(?<![-\\w])width:\\s*(\\d+)px");

        try (Stream<Path> files = Files.list(cssDir)) {
            for (Path css : files.filter(p -> p.toString().endsWith(".css")).collect(Collectors.toList())) {
                String text = Files.readString(css, StandardCharsets.UTF_8);
                boolean hasWide = false;
                java.util.regex.Matcher m = fixedWidth.matcher(text);
                while (m.find()) {
                    if (Integer.parseInt(m.group(1)) > 480) {
                        hasWide = true;
                    }
                }
                if (hasWide) {
                    assertThat(text).as(css.getFileName() + " 은 폭을 고정했으므로 모바일 규칙이 필요하다").contains("@media (max-width: 768px)");
                }
            }
        }
    }

    @Test
    @DisplayName("모달 화면은 모바일에서 전체 크기로 열리고 PC용 id 선택자 규칙을 같은 선택자로 덮어쓴다")
    void modalsAreFullScreenOnMobile() throws IOException {
        String css = Files.readString(Paths.get("src/main/resources/static/css/planner.css"), StandardCharsets.UTF_8);
        String mobile = css.substring(css.indexOf("MOBILE MODAL START"));

        assertThat(mobile).contains("#write_plan .write_plan_wrap").contains("#show_plan .show_plan_wrap");
        assertThat(mobile).contains("#write_plan .write_plan_wrap input[type='file']").contains("#show_plan .show_plan_wrap input[type='file']");
        assertThat(mobile).contains("#show_plan .show_plan_wrap ul li.detail").contains("float: none").contains("overflow-y: auto");
        assertThat(mobile).contains("#show_plan .show_plan_wrap img.plan_img");
    }

    @Test
    @DisplayName("홈 화면의 이미지는 home.css 의 '#section_wrap .content img' 규칙이 적용되도록 class=\"content\" 안에 있다(없으면 모바일에서 이미지가 화면 밖으로 넘친다)")
    void homeImageIsInsideContent() throws IOException {
        String html = Files.readString(TEMPLATES.resolve("home.html"), StandardCharsets.UTF_8);
        String css = Files.readString(Paths.get("src/main/resources/static/css/home.css"), StandardCharsets.UTF_8);

        assertThat(css).contains("#section_wrap .content img").contains("max-width: 100%");
        assertThat(html).containsPattern("(?s)<div class=\"content\">\\s*<img ");
    }

    @Test
    @DisplayName("viewport 메타는 <head> 안, charset 메타 다음에 있다")
    void viewportInsideHead() throws IOException {
        for (Path page : pages()) {
            String html = Files.readString(page, StandardCharsets.UTF_8);
            int head = html.indexOf("<head>");
            int headEnd = html.indexOf("</head>");
            int viewport = html.indexOf("name=\"viewport\"");

            assertThat(viewport).as(page.toString()).isGreaterThan(head).isLessThan(headEnd);
            assertThat(html.indexOf("<meta charset=\"UTF-8\">")).as(page.toString()).isLessThan(viewport);
        }
    }

}
