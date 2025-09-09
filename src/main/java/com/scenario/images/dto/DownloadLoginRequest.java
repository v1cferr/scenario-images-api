package com.scenario.images.dto;

/**
 * Request para login de download por ambiente
 */
public class DownloadLoginRequest {
    private String secretKey;
    private Long environmentId;

    public DownloadLoginRequest() {}

    public DownloadLoginRequest(String secretKey, Long environmentId) {
        this.secretKey = secretKey;
        this.environmentId = environmentId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(Long environmentId) {
        this.environmentId = environmentId;
    }
}
