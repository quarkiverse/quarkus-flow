package org.acme.bestpractices;

import java.time.LocalDate;

public record VacationRequest(String employeeId, LocalDate from, LocalDate to) {
}
