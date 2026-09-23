package com.lemarketjames.common.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** No custom queries yet; AuthService only ever inserts one row per client on registration. */
public interface AddressRepository extends JpaRepository<AddressEntity, Integer> {
}
