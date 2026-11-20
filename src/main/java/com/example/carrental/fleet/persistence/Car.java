package com.example.carrental.fleet.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A physical car in the fleet. Bookings do not point at a car; they only count against the capacity of its type.
 */
@Entity
@Table(name = "car")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String plate;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_type", nullable = false, length = 16)
    private CarType carType;

    /** Soft delete: reservations keep referring to history. */
    @Column(nullable = false)
    private boolean active = true;

    public Car(String plate, CarType carType) {
        this.plate = plate;
        this.carType = carType;
    }

    /** Soft delete: the row stays so history remains intact, but the car no longer counts towards capacity. */
    public void deactivate() {
        this.active = false;
    }
}
