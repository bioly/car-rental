package com.example.carrental.fleet.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Access to the per-type lock row. See {@link CarCategory}.
 */
public interface CarCategoryRepository extends JpaRepository<CarCategory, String> {

    /** Loads the row with {@code SELECT ... FOR UPDATE}. Held until the transaction ends: this is the inventory lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CarCategory c where c.code = :code")
    Optional<CarCategory> findForUpdate(@Param("code") String code);
}
