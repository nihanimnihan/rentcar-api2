package com.rentcar.api.exception;

public class ChauffeurCategoryNotFoundException extends RuntimeException {

    public ChauffeurCategoryNotFoundException(Long id) {
        super("Chauffeur category not found: id=" + id);
    }
}
