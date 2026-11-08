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
