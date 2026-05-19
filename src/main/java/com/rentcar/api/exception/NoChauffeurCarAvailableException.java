package com.rentcar.api.exception;

public class NoChauffeurCarAvailableException extends RuntimeException {

    public NoChauffeurCarAvailableException(Long categoryId) {
        super("No chauffeur car available for category id=" + categoryId + " in the requested time window");
    }
}
