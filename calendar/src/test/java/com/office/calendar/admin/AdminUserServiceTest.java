package com.office.calendar.admin;

import com.office.calendar.admin.mapper.AdminUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 사용자 목록·검색(adm-001): 검색어 처리, 페이지 범위 보정, 결과 구성
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    @DisplayName("검색어가 없으면 조건 없이(null) 첫 페이지를 20건씩 조회하고 users·total을 돌려준다")
    void getUsers_defaults() {
        AdminUserDto alice = AdminUserDto.builder().no(1).id("alice").role("USER").build();
        when(adminUserMapper.selectUsers(null, 20, 0)).thenReturn(List.of(alice));
        when(adminUserMapper.countUsers(null)).thenReturn(1);

        Map<String, Object> result = adminUserService.getUsers(null, 0, 20);

        assertThat(result.get("users")).isEqualTo(List.of(alice));
        assertThat(result.get("total")).isEqualTo(1);
        assertThat(result.get("page")).isEqualTo(0);
        assertThat(result.get("size")).isEqualTo(20);
    }

    @Test
    @DisplayName("공백뿐인 검색어는 조건 없음(null)으로 본다")
    void getUsers_blankKeyword() {
        adminUserService.getUsers("   ", 0, 20);

        verify(adminUserMapper).selectUsers(null, 20, 0);
        verify(adminUserMapper).countUsers(null);
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 지우고 %·_·!를 문자로 취급하도록 이스케이프한다")
    void getUsers_escapesWildcards() {
        adminUserService.getUsers("  a_b%c!  ", 0, 20);

        verify(adminUserMapper).selectUsers("a!_b!%c!!", 20, 0);
        verify(adminUserMapper).countUsers("a!_b!%c!!");
    }

    @Test
    @DisplayName("페이지 번호로 건너뛸 건수(offset)를 계산한다")
    void getUsers_offset() {
        adminUserService.getUsers(null, 2, 10);

        verify(adminUserMapper).selectUsers(null, 10, 20);
    }

    @Test
    @DisplayName("size가 0 이하이면 기본값(20), 너무 크면 최대값(100)으로 보정한다")
    void getUsers_clampsSize() {
        adminUserService.getUsers(null, 0, 0);
        adminUserService.getUsers(null, 0, -5);
        adminUserService.getUsers(null, 0, 1000);

        verify(adminUserMapper, org.mockito.Mockito.times(2)).selectUsers(null, 20, 0);
        verify(adminUserMapper).selectUsers(null, 100, 0);
    }

    @Test
    @DisplayName("음수 페이지는 0으로 보정하고, 아주 큰 페이지도 offset이 넘치지 않는다")
    void getUsers_clampsPage() {
        adminUserService.getUsers(null, -3, 10);
        adminUserService.getUsers(null, Integer.MAX_VALUE, 100);

        verify(adminUserMapper).selectUsers(null, 10, 0);
        verify(adminUserMapper).selectUsers(null, 100, Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("목록 DTO에는 비밀번호 필드가 없다(해시 노출 방지)")
    void dto_hasNoPasswordField() {
        List<String> names = Arrays.stream(AdminUserDto.class.getDeclaredFields()).map(Field::getName).toList();

        assertThat(names).doesNotContain("pw", "password", "memPw");
    }

}
