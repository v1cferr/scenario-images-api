package com.scenario.images.dto;

/**
 * Request para login de edição
 */
public class EditLoginRequest {
    private String secretKey;

    public EditLoginRequest() {}

    public EditLoginRequest(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
