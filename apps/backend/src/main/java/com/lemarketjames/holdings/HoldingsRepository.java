package com.lemarketjames.holdings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for Holdings entity.
 * Provides database queries for retrieving and managing user holdings.
 */
@Repository
public interface HoldingsRepository extends JpaRepository<Holdings, Long> {

    /**
     * Finds a holding by account ID and instrument ID.
     *
     * @param accountId the account ID
     * @param instrumentId the instrument ID
     * @return an Optional containing the holding if found
     */
    Optional<Holdings> findByAccountIdAndInstrumentId(Long accountId, Long instrumentId);

    /**
     * Finds all holdings for a given account.
     *
     * @param accountId the account ID
     * @return a list of holdings for the account
     */
    List<Holdings> findAllByAccountId(Long accountId);
}
