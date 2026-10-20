package com.lemarketjames.market.repository;

import com.lemarketjames.market.entity.MarketQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketQuoteRepository extends JpaRepository<MarketQuoteEntity, Integer> {
}
