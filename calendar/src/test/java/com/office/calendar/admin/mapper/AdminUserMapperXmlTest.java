package com.office.calendar.admin.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 매퍼 XML이 읽히는지, 동적 조건(keyword)에 따라 SQL이 바뀌는지, 비밀번호를 조회하지 않는지 확인한다.
// 실제 쿼리 실행(조인·페이징)은 MySQL이 준비된 뒤 런타임에서 확인한다.
class AdminUserMapperXmlTest {

    private static final String NAMESPACE = "com.office.calendar.admin.mapper.AdminUserMapper";

    private Configuration config;

    @BeforeEach
    void setUp() throws Exception {
        config = new Configuration();
        String resource = "mybatis/mappers/admin-user-mapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
    }

    private String sql(String statementId, String keyword) {
        MappedStatement statement = config.getMappedStatement(NAMESPACE + "." + statementId);
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("size", 20);
        params.put("offset", 0);
        return statement.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("keyword가 없으면 검색 조건 없이 전체를 조회한다")
    void select_withoutKeyword() {
        String sql = sql("selectUsers", null);

        assertThat(sql).doesNotContain("WHERE").doesNotContain("LIKE");
        assertThat(sql).contains("LIMIT ? OFFSET ?");
    }

    @Test
    @DisplayName("keyword가 비어 있어도 검색 조건을 붙이지 않는다")
    void select_blankKeyword() {
        assertThat(sql("selectUsers", "")).doesNotContain("LIKE");
    }

    @Test
    @DisplayName("keyword가 있으면 아이디 LIKE 조건이 붙고 와일드카드 이스케이프를 지정한다")
    void select_withKeyword() {
        String sql = sql("selectUsers", "ali");

        assertThat(sql).contains("WHERE").contains("m.ID LIKE CONCAT('%', ?, '%') ESCAPE '!'");
    }

    @Test
    @DisplayName("건수 조회도 같은 조건을 쓴다")
    void count_followsSameCondition() {
        assertThat(sql("countUsers", null)).doesNotContain("LIKE");
        assertThat(sql("countUsers", "ali")).contains("LIKE");
    }

    @Test
    @DisplayName("매퍼 인터페이스의 모든 메서드에 같은 id의 SQL이 있다(id 오타 방지)")
    void everyMapperMethodHasStatement() {
        assertThat(config.hasMapper(AdminUserMapper.class)).isTrue();
        for (java.lang.reflect.Method method : AdminUserMapper.class.getDeclaredMethods()) {
            assertThat(config.hasStatement(NAMESPACE + "." + method.getName()))
                    .as("SQL id: " + method.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("사용자 목록 쿼리는 비밀번호 컬럼(PW)을 조회하지 않는다")
    void select_doesNotReadPassword() {
        String sql = sql("selectUsers", "ali");

        assertThat(sql).doesNotContain("m.PW").doesNotContain("PW ");
    }

}
