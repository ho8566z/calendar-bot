package com.office.calendar.config;

import com.office.calendar.member.MemberDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// 실제 Thymeleaf(와 sec:authorize)로 화면을 렌더링해, 템플릿 문법 오류·남은 th:/sec: 속성·역할별 표시를 DB 없이 확인한다.
// 주소와 접근 규칙은 실제 SecurityConfig 를 그대로 쓴다.
@SpringJUnitWebConfig(classes = {SecurityConfig.class, ThymeleafRenderingTest.WebConfig.class})
class ThymeleafRenderingTest {

    @Configuration
    @EnableWebMvc
    static class WebConfig {

        @Bean
        SpringResourceTemplateResolver templateResolver(ApplicationContext context) {
            SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
            resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/");
            resolver.setSuffix(".html");
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }

        @Bean
        SpringTemplateEngine templateEngine(SpringResourceTemplateResolver resolver) {
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            engine.addDialect(new SpringSecurityDialect());     // sec:authorize
            return engine;
        }

        @Bean
        ThymeleafViewResolver viewResolver(SpringTemplateEngine engine) {
            ThymeleafViewResolver resolver = new ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }

        @Bean
        PageController pageController() {
            return new PageController();
        }

    }

    // 실제 컨트롤러 없이 화면 이름만 돌려주는 테스트용 컨트롤러(주소는 접근 규칙과 같은 것을 쓴다)
    @Controller
    static class PageController {

        @GetMapping("/")
        String home() {
            return "home";
        }

        @GetMapping("/member/signin")
        String signin() {
            return "member/signin_form";
        }

        @GetMapping("/member/signup")
        String signup() {
            return "member/signup_form";
        }

        @GetMapping("/member/modify")
        String modify(Model model) {
            model.addAttribute("loginedMemberDto", MemberDto.builder().no(1).id("alice").mail("a@example.com").phone("010").build());
            return "member/modify_form";
        }

        @GetMapping("/planner")
        String planner() {
            return "planner/home";
        }

        @GetMapping("/planner/stats")
        String stats() {
            return "planner/stats";
        }

        @GetMapping("/admin/users")
        String adminUsers() {
            return "admin/users";
        }

    }

    @Autowired
    private WebApplicationContext context;

    private String render(String path, RequestPostProcessor... auth) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var request = get(path);
        for (RequestPostProcessor p : auth) {
            request = request.with(p);
        }
        var response = mockMvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(path).isEqualTo(200);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private static String withoutComments(String html) {
        return html.replaceAll("(?s)<!--.*?-->", "");
    }

    private static RequestPostProcessor asUser() {
        return user("alice").roles("USER");
    }

    private static RequestPostProcessor asAdmin() {
        return user("boss").roles("ADMIN");
    }

    @Test
    @DisplayName("모든 화면이 오류 없이 렌더링되고 th:·sec: 속성이 남지 않으며 viewport 메타와 메뉴 스크립트가 들어간다")
    void allPagesRender() throws Exception {
        String[][] pages = {
                {"/", null}, {"/member/signin", null}, {"/member/signup", null},
                {"/member/modify", "user"}, {"/planner", "user"}, {"/planner/stats", "user"}, {"/admin/users", "admin"}};

        for (String[] page : pages) {
            String html = page[1] == null ? render(page[0])
                    : render(page[0], "admin".equals(page[1]) ? asAdmin() : asUser());
            String body = withoutComments(html);

            assertThat(body).as(page[0] + " 남은 th: 속성").doesNotContainPattern("\\sth:(href|src|replace|value|action|text|if|each|fragment|block)");
            assertThat(body).as(page[0] + " 남은 sec: 속성").doesNotContain("sec:authorize");
            assertThat(body).as(page[0] + " viewport").contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
            assertThat(body).as(page[0] + " 메뉴 버튼").contains("id=\"menu_toggle\"").contains("src=\"/js/menu.js\"");
            assertThat(body).as(page[0] + " 공통 CSS").contains("href=\"/css/header_nav_footer.css\"");
        }
    }

    @Test
    @DisplayName("비로그인 머리글: 가입·로그인 링크만 보인다")
    void anonymousHeader() throws Exception {
        String body = withoutComments(render("/"));

        assertThat(body).contains("href=\"/member/signup\"").contains("href=\"/member/signin\"");
        assertThat(body).doesNotContain("href=\"/member/modify\"").doesNotContain("href=\"/member/signout_confirm\"");
        assertThat(body).doesNotContain("&nbsp;");
    }

    @Test
    @DisplayName("로그인 머리글: USER 는 modify·signout·planner 링크, 관리자는 admin 링크(planner 는 숨김)")
    void loggedInHeader() throws Exception {
        String user = withoutComments(render("/planner", asUser()));
        assertThat(user).contains("href=\"/member/modify\"").contains("href=\"/member/signout_confirm\"").contains("href=\"/planner\"");
        assertThat(user).doesNotContain("href=\"/member/signin\"").doesNotContain("href=\"/admin/users\"");

        String admin = withoutComments(render("/admin/users", asAdmin()));
        assertThat(admin).contains("href=\"/admin/users\"").doesNotContain("href=\"/planner\"");
    }

    @Test
    @DisplayName("내 정보 화면: 텔레그램 영역은 USER 에게만 렌더링된다")
    void telegramSectionOnlyForUser() throws Exception {
        String user = withoutComments(render("/member/modify", asUser()));
        assertThat(user).contains("id=\"telegram\"").contains("id=\"tg_link_btn\"").contains("src=\"/js/member_telegram.js\"")
                .contains("href=\"/css/member_telegram.css\"").contains("value=\"alice\"");

        String admin = withoutComments(render("/member/modify", asAdmin()));
        assertThat(admin).doesNotContain("id=\"telegram\"").doesNotContain("tg_link_btn");
    }

    @Test
    @DisplayName("달력 화면: 검색창, 시간 입력칸 2곳, 통계 링크, 날짜별 목록 패널이 렌더링된다")
    void plannerPage() throws Exception {
        String body = withoutComments(render("/planner", asUser()));

        assertThat(body).contains("name=\"s_keyword\"").contains("id=\"day_list\"").contains("class=\"btn_stats\" href=\"/planner/stats\"");
        assertThat(body.split("name=\"p_time\"", -1).length - 1).isEqualTo(2);
        assertThat(body).contains("src=\"/js/planner.js\"").contains("src=\"/js/planner_fetch.js\"").contains("href=\"/css/planner.css\"");
    }

    @Test
    @DisplayName("통계 화면: 전용 스크립트와 스타일이 연결된다")
    void statsPage() throws Exception {
        String body = withoutComments(render("/planner/stats", asUser()));

        assertThat(body).contains("src=\"/js/planner_stats.js\"").contains("href=\"/css/planner_stats.css\"").contains("id=\"weekday_rows\"");
    }

}
