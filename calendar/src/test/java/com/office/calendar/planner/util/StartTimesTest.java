package com.office.calendar.planner.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 시작 시간(plan-007) 형식 검증과 변환
class StartTimesTest {

    @Test
    @DisplayName("비었거나 공백뿐이면 시간 없음(null)")
    void normalize_blank() {
        assertThat(StartTimes.normalize(null)).isNull();
        assertThat(StartTimes.normalize("")).isNull();
        assertThat(StartTimes.normalize("   ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "09:30", "12:00", "23:59"})
    @DisplayName("HH:mm 형식은 그대로 받는다")
    void normalize_valid(String value) {
        assertThat(StartTimes.normalize(value)).isEqualTo(value);
    }

    @Test
    @DisplayName("앞뒤 공백은 지우고, 초가 붙어 오면(HH:mm:ss) 초는 버린다")
    void normalize_trimsAndDropsSeconds() {
        assertThat(StartTimes.normalize(" 14:05 ")).isEqualTo("14:05");
        assertThat(StartTimes.normalize("09:30:45")).isEqualTo("09:30");
    }

    @ParameterizedTest
    @ValueSource(strings = {"9:30", "24:00", "12:60", "12:5", "abc", "09-30", "09:30:99", "09:30:5", "09:30 PM", "9", "0930", "-1:00"})
    @DisplayName("형식에 맞지 않으면 IllegalArgumentException")
    void normalize_invalid(String value) {
        assertThatThrownBy(() -> StartTimes.normalize(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열과 시간 사이를 오가며 null은 null로 둔다")
    void convert() {
        assertThat(StartTimes.toLocalTime("09:05")).isEqualTo(LocalTime.of(9, 5));
        assertThat(StartTimes.toLocalTime(null)).isNull();
        assertThat(StartTimes.format(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(StartTimes.format(LocalTime.of(23, 59, 30))).as("초는 표시하지 않는다").isEqualTo("23:59");
        assertThat(StartTimes.format(null)).isNull();
    }

}
