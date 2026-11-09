package com.office.calendar.member.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorityRepository extends JpaRepository<AuthorityEntity, Byte> {

    public boolean existsByAuthRoleName(String authRoleName);

    public Optional<AuthorityEntity> findByAuthRoleName(String authRoleName);

}
