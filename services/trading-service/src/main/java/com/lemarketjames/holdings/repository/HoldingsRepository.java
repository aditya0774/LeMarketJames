package com.lemarketjames.holdings.repository;

import com.lemarketjames.holdings.entity.HoldingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingsRepository extends JpaRepository<HoldingsEntity, Integer> {
    List<HoldingsEntity> findByAccountId(Integer accountId);

    Optional<HoldingsEntity> findByAccountIdAndInstrumentId(Integer accountId, Integer instrumentId);
}
