package com.creatorhub.backend.dto;

public record RegisterRequest(String username, String password) {
    @Override
    public String toString() {
        return "RegisterRequest[credentials=REDACTED]";
    }
}
