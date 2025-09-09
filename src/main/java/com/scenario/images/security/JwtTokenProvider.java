package com.scenario.images.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret:imagesApiSecretKeyForJWTTokenGeneration2024ScenarioImagesAPI}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:86400000}") // 24 horas
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Gerar token para operações de edição (upload/remoção)
     */
    public String generateEditToken() {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject("EDIT_PERMISSION")
                .claim("permissions", List.of("UPLOAD", "DELETE"))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Gerar token para download de imagem por ambiente
     */
    public String generateDownloadToken(Long environmentId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .setSubject("DOWNLOAD_PERMISSION")
                .claim("environmentId", environmentId)
                .claim("permissions", List.of("DOWNLOAD"))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validar token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(getSigningKey())
                .parseClaimsJws(token);
            return true;
        } catch (SignatureException ex) {
            System.err.println("Invalid JWT signature: " + ex.getMessage());
        } catch (MalformedJwtException ex) {
            System.err.println("Invalid JWT token: " + ex.getMessage());
        } catch (ExpiredJwtException ex) {
            System.err.println("Expired JWT token: " + ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            System.err.println("Unsupported JWT token: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            System.err.println("JWT claims string is empty: " + ex.getMessage());
        }
        return false;
    }

    /**
     * Extrair claims do token
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(getSigningKey())
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Verificar se token tem permissão específica
     */
    public boolean hasPermission(String token, String permission) {
        try {
            Claims claims = getClaimsFromToken(token);
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) claims.get("permissions");
            return permissions != null && permissions.contains(permission);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obter environment ID do token (para downloads)
     */
    public Long getEnvironmentIdFromToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Object envId = claims.get("environmentId");
            if (envId instanceof Integer) {
                return ((Integer) envId).longValue();
            } else if (envId instanceof Long) {
                return (Long) envId;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Verificar se token permite download para ambiente específico
     */
    public boolean canDownloadFromEnvironment(String token, Long environmentId) {
        if (!hasPermission(token, "DOWNLOAD")) {
            return false;
        }
        
        Long tokenEnvironmentId = getEnvironmentIdFromToken(token);
        return tokenEnvironmentId != null && tokenEnvironmentId.equals(environmentId);
    }
}
