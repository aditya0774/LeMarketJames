package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.PriceCandleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceCandleRepository extends JpaRepository<PriceCandleEntity, PriceCandleEntity.Key> {
}
