package com.office.calendar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// LIKE 검색어 처리: 와일드카드를 글자로 취급
class LikeKeywordTest {

    @Test
    @DisplayName("null·빈 값·공백뿐인 값은 조건 없음(null)")
    void blank() {
        assertThat(LikeKeyword.toPattern(null)).isNull();
        assertThat(LikeKeyword.toPattern("")).isNull();
        assertThat(LikeKeyword.toPattern("   ")).isNull();
    }

    @Test
    @DisplayName("앞뒤 공백을 지운다")
    void trims() {
        assertThat(LikeKeyword.toPattern("  치과  ")).isEqualTo("치과");
    }

    @Test
    @DisplayName("%, _, !는 '!'로 이스케이프해서 글자로 검색되게 한다")
    void escapes() {
        assertThat(LikeKeyword.toPattern("100%")).isEqualTo("100!%");
        assertThat(LikeKeyword.toPattern("a_b")).isEqualTo("a!_b");
        assertThat(LikeKeyword.toPattern("wow!")).isEqualTo("wow!!");
        assertThat(LikeKeyword.toPattern("a_b%c!")).isEqualTo("a!_b!%c!!");
    }

    @Test
    @DisplayName("이스케이프 문자 '!'가 먼저 처리되어 이중으로 바뀌지 않는다")
    void escapesBangFirst() {
        // '%'를 먼저 바꾸면 만들어진 '!'가 다시 바뀐다. 순서가 맞는지 고정한다.
        assertThat(LikeKeyword.toPattern("%")).isEqualTo("!%");
        assertThat(LikeKeyword.toPattern("!%")).isEqualTo("!!!%");
    }

}
