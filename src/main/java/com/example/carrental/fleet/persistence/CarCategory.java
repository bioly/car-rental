package com.example.carrental.fleet.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per car type. Exists only to be locked ({@code SELECT ... FOR UPDATE}) so that
 * bookings and fleet changes for the same type are serialised. Portable across H2/PostgreSQL.
 */
@Entity
@Table(name = "car_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarCategory {

    @Id
    @Column(length = 16)
    private String code;

    public CarCategory(String code) {
        this.code = code;
    }
}
