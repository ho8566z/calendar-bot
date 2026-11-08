package com.office.calendar.brief.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 브리핑 매퍼 XML이 읽히는지, 대상 조건과 일정 범위 조건이 맞는지 확인한다.
// 실제 쿼리 실행은 MySQL이 준비된 환경에서 런타임으로 확인한다.
class BriefMapperXmlTest {

    private static final String NAMESPACE = "com.office.calendar.brief.mapper.BriefMapper";

    private Configuration config;

    @BeforeEach
    void setUp() throws Exception {
        config = new Configuration();
        String resource = "mybatis/mappers/brief-mapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
    }

    private BoundSql bound(String id, Map<String, Object> params) {
        return config.getMappedStatement(NAMESPACE + "." + id).getBoundSql(params);
    }

    private static String normalized(BoundSql sql) {
        return sql.getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("대상: 수신을 켜고 텔레그램이 연동된 USER만, 그 주에 이미 기록된 회원은 제외")
    void targets() {
        Map<String, Object> params = new HashMap<>();
        params.put("weekStart", LocalDate.of(2026, 11, 9));
        BoundSql sql = bound("selectTargets", params);
        String text = normalized(sql);

        assertThat(text).contains("JOIN USER_AUTHORITY a ON m.AUTHORITY_NO = a.NO");
        assertThat(text).contains("m.BRIEF_YN = 1").contains("m.TELEGRAM_CHAT_ID IS NOT NULL").contains("a.ROLE_NAME = 'USER'");
        assertThat(text).contains("NOT EXISTS ( SELECT 1 FROM BRIEF_LOG l WHERE l.MEMBER_NO = m.NO AND l.WEEK_START = ? )");
        assertThat(sql.getParameterMappings()).extracting(m -> m.getProperty()).containsExactly("weekStart");
    }

    @Test
    @DisplayName("대상: 비밀번호·메일·전화번호는 조회하지 않는다")
    void targetsDoNotSelectSecrets() {
        Map<String, Object> params = new HashMap<>();
        params.put("weekStart", LocalDate.of(2026, 11, 9));
        String text = normalized(bound("selectTargets", params));

        assertThat(text).doesNotContain("m.PW").doesNotContain("m.MAIL").doesNotContain("m.PHONE");
        assertThat(text).contains("m.NO AS memberNo", "m.ID AS memberId", "m.TELEGRAM_CHAT_ID AS chatId");
    }

    @Test
    @DisplayName("일정: 본인 일정만, yyyymmdd 정수 범위로 비교하고 날짜 → 시작 시간(없으면 뒤) → 번호 순")
    void plans() {
        Map<String, Object> params = new HashMap<>();
        params.put("ownerId", "alice");
        params.put("fromKey", 20261228);
        params.put("toKey", 20270103);
        BoundSql sql = bound("selectPlans", params);
        String text = normalized(sql);

        assertThat(text).contains("p.OWNER_ID = ?");
        assertThat(text).contains("(p.`YEAR` * 10000 + p.`MONTH` * 100 + p.`DATE`) BETWEEN ? AND ?");
        assertThat(text).contains("ORDER BY p.`YEAR`, p.`MONTH`, p.`DATE`, (p.START_TIME IS NULL) ASC, p.START_TIME ASC, p.NO ASC");
        assertThat(text).contains("p.`DATE` AS `date`").contains("TIME_FORMAT(p.START_TIME, '%H:%i') AS startTime");
        assertThat(sql.getParameterMappings()).extracting(m -> m.getProperty()).containsExactly("ownerId", "fromKey", "toKey");
    }

    @Test
    @DisplayName("일정: 값이 SQL에 직접 들어가지 않는다")
    void plansUseBinding() {
        Map<String, Object> params = new HashMap<>();
        params.put("ownerId", "x' OR '1'='1");
        params.put("fromKey", 1);
        params.put("toKey", 2);

        assertThat(bound("selectPlans", params).getSql()).doesNotContain("OR '1'='1");
    }

    @Test
    @DisplayName("매퍼 인터페이스의 모든 메서드에 같은 id의 SQL이 있다")
    void everyMethodHasStatement() {
        assertThat(config.hasMapper(BriefMapper.class)).isTrue();
        for (Method method : BriefMapper.class.getDeclaredMethods()) {
            assertThat(config.hasStatement(NAMESPACE + "." + method.getName())).as(method.getName()).isTrue();
        }
    }

}
