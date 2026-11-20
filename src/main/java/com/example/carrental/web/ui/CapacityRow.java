package com.example.carrental.web.ui;

import com.example.carrental.fleet.persistence.CarType;

/** One line of the fleet summary table on the admin page. */
public record CapacityRow(CarType type, long cars) {
}
