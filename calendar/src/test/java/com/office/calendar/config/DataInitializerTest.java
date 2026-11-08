package com.office.calendar.config;

import com.office.calendar.admin.AdminSeedService;
import com.office.calendar.member.jpa.AuthorityEntity;
import com.office.calendar.member.jpa.AuthorityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 서버 시작 때 역할(PRE_USER, USER, ADMIN)을 채우고 관리자 시드를 부른다
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private AuthorityRepository authorityRepository;
    @Mock
    private AdminSeedService adminSeedService;

    private void runInitializer() throws Exception {
        new DataInitializer().initDatabase(authorityRepository, adminSeedService).run();
    }

    @Test
    @DisplayName("역할이 하나도 없으면 PRE_USER, USER, ADMIN 순서로 저장한 뒤 관리자 시드를 부른다")
    void createsMissingRolesInOrder() throws Exception {
        runInitializer();

        ArgumentCaptor<AuthorityEntity> captor = ArgumentCaptor.forClass(AuthorityEntity.class);
        verify(authorityRepository, times(3)).save(captor.capture());
        List<String> names = captor.getAllValues().stream().map(AuthorityEntity::getAuthRoleName).toList();
        // 번호는 저장 순서대로 붙고 가입 로직이 PRE_USER를 1번으로 가정하므로 순서가 중요하다
        assertThat(names).containsExactly("PRE_USER", "USER", "ADMIN");

        // 관리자 시드는 역할이 모두 준비된 뒤에 불러야 한다
        InOrder inOrder = Mockito.inOrder(authorityRepository, adminSeedService);
        inOrder.verify(authorityRepository, times(3)).save(any());
        inOrder.verify(adminSeedService).seedIfAbsent();
    }

    @Test
    @DisplayName("이미 있는 역할은 다시 만들지 않는다(재시작해도 중복 없음)")
    void skipsExistingRoles() throws Exception {
        when(authorityRepository.existsByAuthRoleName("PRE_USER")).thenReturn(true);
        when(authorityRepository.existsByAuthRoleName("USER")).thenReturn(true);
        when(authorityRepository.existsByAuthRoleName("ADMIN")).thenReturn(true);

        runInitializer();

        verify(authorityRepository, never()).save(any());
        verify(adminSeedService).seedIfAbsent();
    }

    @Test
    @DisplayName("기존 DB에 ADMIN 역할만 없으면 ADMIN만 추가한다")
    void addsOnlyAdminToExistingDatabase() throws Exception {
        when(authorityRepository.existsByAuthRoleName("PRE_USER")).thenReturn(true);
        when(authorityRepository.existsByAuthRoleName("USER")).thenReturn(true);

        runInitializer();

        ArgumentCaptor<AuthorityEntity> captor = ArgumentCaptor.forClass(AuthorityEntity.class);
        verify(authorityRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthRoleName()).isEqualTo("ADMIN");
    }

}
