package com.example.carrental.fleet.persistence;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for cars. Other packages go through FleetService.
 */
public interface CarRepository extends JpaRepository<Car, Long> {

    /** Capacity of a type = number of active cars. */
    long countByCarTypeAndActiveTrue(CarType carType);

    /** Plates must be unique among active cars (a removed car's plate can be reused). */
    boolean existsByPlateAndActiveTrue(String plate);

    List<Car> findByActiveTrueOrderByCarTypeAscPlateAsc();
}
