package com.rentcar.api.exception;

public class CarNotAvailableException extends RuntimeException {
    private final Long carId;

    public CarNotAvailableException(Long carId) {
        super("Car is not available for selected dates");
        this.carId = carId;
    }

    public Long getCarId() {
        return carId;
    }
}
