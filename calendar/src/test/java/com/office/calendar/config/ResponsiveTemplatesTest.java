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
