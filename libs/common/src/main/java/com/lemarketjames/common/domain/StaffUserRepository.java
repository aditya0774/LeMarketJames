package com.lemarketjames.common.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** findByEmail backs staff login, which uses the same email-based login as clients. */
public interface StaffUserRepository extends JpaRepository<StaffUserEntity, Integer> {

    Optional<StaffUserEntity> findByEmail(String email);
}
