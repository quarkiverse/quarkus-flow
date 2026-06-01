package com.carmanagement.model;

/**
 * Car information from the workshop Step 02.
 * Matches the workshop's car management domain model.
 */
public record CarInfo(
        String make,
        String model,
        Integer year,
        String condition) {
}
