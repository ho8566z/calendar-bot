package com.office.calendar.planner.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 매퍼 XML이 읽히는지, keyword에 따라 SQL이 바뀌는지, 항상 본인 일정만 대상인지 확인한다.
// 실제 쿼리 실행(정렬·LIKE 동작)은 MySQL이 준비된 환경에서 런타임으로 확인한다.
class PlannerMapperXmlTest {

    private static final String NAMESPACE = "com.office.calendar.planner.mapper.PlannerMapper";

    private Configuration config;

    @BeforeEach
    void setUp() throws Exception {
        config = new Configuration();
        String resource = "mybatis/mappers/planner-mapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
    }

    private BoundSql bound(String statementId, String keyword) {
        MappedStatement statement = config.getMappedStatement(NAMESPACE + "." + statementId);
        Map<String, Object> params = new HashMap<>();
        params.put("ownerId", "alice");
        params.put("keyword", keyword);
        params.put("limit", 100);
        return statement.getBoundSql(params);
    }

    private String sql(String statementId, String keyword) {
        return bound(statementId, keyword).getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("keyword가 없어도 항상 본인(OWNER_ID) 일정만 대상이다")
    void search_alwaysOwnerScoped() {
        String sql = sql("searchPlans", null);

        assertThat(sql).contains("WHERE p.OWNER_ID = ?");
        assertThat(sql).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("keyword가 있으면 제목 또는 내용 LIKE가 본인 조건과 AND로 붙는다")
    void search_withKeyword() {
        String sql = sql("searchPlans", "치과");

        assertThat(sql).contains("p.OWNER_ID = ? AND (p.TITLE LIKE CONCAT('%', ?, '%') ESCAPE '!' OR p.BODY LIKE CONCAT('%', ?, '%') ESCAPE '!')");
    }

    @Test
    @DisplayName("keyword가 비어 있으면 검색 조건을 붙이지 않는다")
    void search_blankKeyword() {
        assertThat(sql("searchPlans", "")).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("파라미터는 모두 바인딩(?)이고 SQL에 값이 직접 들어가지 않는다")
    void search_usesBindParameters() {
        BoundSql boundSql = bound("searchPlans", "x' OR '1'='1");

        assertThat(boundSql.getSql()).doesNotContain("OR '1'='1");
        assertThat(boundSql.getParameterMappings()).extracting(m -> m.getProperty())
                .containsExactly("ownerId", "keyword", "keyword", "limit");
    }

    @Test
    @DisplayName("최신 날짜순, 같은 날은 시작 시간순(시간 없는 일정은 뒤)이고 건수를 제한한다")
    void search_ordersAndLimits() {
        String sql = sql("searchPlans", "a");

        assertThat(sql).contains("ORDER BY p.`YEAR` DESC, p.`MONTH` DESC, p.`DATE` DESC, (p.START_TIME IS NULL) ASC, p.START_TIME ASC, p.NO DESC");
        assertThat(sql).endsWith("LIMIT ?");
    }

    @Test
    @DisplayName("예약어와 겹치는 컬럼은 백틱으로 감싸고 시작 시간은 HH:mm 문자열로 가져온다")
    void search_columnsAndFormats() {
        String sql = sql("searchPlans", null);

        assertThat(sql).contains("p.`DATE` AS `date`").contains("p.`YEAR` AS `year`").contains("p.`MONTH` AS `month`");
        assertThat(sql).contains("TIME_FORMAT(p.START_TIME, '%H:%i') AS start_time");
    }

    @Test
    @DisplayName("매퍼 인터페이스의 모든 메서드에 같은 id의 SQL이 있다(id 오타 방지)")
    void everyMapperMethodHasStatement() {
        assertThat(config.hasMapper(PlannerMapper.class)).isTrue();
        for (Method method : PlannerMapper.class.getDeclaredMethods()) {
            assertThat(config.hasStatement(NAMESPACE + "." + method.getName()))
                    .as("SQL id: " + method.getName())
                    .isTrue();
        }
    }

}
