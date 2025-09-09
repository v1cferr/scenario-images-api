package com.scenario.images.dto;

/**
 * Response para login com JWT token
 */
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String permissions;
    private Long environmentId; // Para tokens de download

    public JwtResponse(String token, String permissions) {
        this.token = token;
        this.permissions = permissions;
    }

    public JwtResponse(String token, String permissions, Long environmentId) {
        this.token = token;
        this.permissions = permissions;
        this.environmentId = environmentId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public Long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(Long environmentId) {
        this.environmentId = environmentId;
    }
}
