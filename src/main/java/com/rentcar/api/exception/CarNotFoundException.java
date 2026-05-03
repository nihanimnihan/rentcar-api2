package com.rentcar.api.exception;

public class CarNotFoundException extends RuntimeException {

    private final Long carId;

    public CarNotFoundException(Long carId) {
        super("Car not found");
        this.carId = carId;
    }

    public Long getCarId() {
        return carId;
    }
}
