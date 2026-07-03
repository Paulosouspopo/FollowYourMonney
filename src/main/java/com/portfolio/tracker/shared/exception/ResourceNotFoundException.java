package com.portfolio.tracker.shared.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " introuvable avec l'id : " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}