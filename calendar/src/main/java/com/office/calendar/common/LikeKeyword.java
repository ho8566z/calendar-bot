package com.office.calendar.common;

// MyBatis의 LIKE 검색어를 만든다. SQL에는 ESCAPE '!'를 함께 써야 한다.
// 사용자가 입력한 %, _ 가 와일드카드로 동작하지 않고 글자 그대로 검색되게 '!'로 이스케이프한다.
public final class LikeKeyword {

    private LikeKeyword() {
    }

    // 앞뒤 공백을 지우고 이스케이프한다. 비었으면 null(검색 조건 없음)
    public static String toPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

    }

}
