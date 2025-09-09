package com.scenario.images.controller;

import com.scenario.images.dto.DownloadLoginRequest;
import com.scenario.images.dto.EditLoginRequest;
import com.scenario.images.dto.JwtResponse;
import com.scenario.images.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@Tag(name = "Autenticação", description = "Endpoints para autenticação e geração de tokens JWT")
public class AuthController {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Value("${app.jwt.secret-key:scenarioImagesSecretKey2024}")
    private String secretKey;

    /**
     * Login para operações de edição (upload/remoção)
     */
    @PostMapping("/login/edit")
    @Operation(summary = "Login para edição", 
               description = "Gera token JWT para operações de upload e remoção de imagens")
    public ResponseEntity<?> loginForEdit(@RequestBody EditLoginRequest request) {
        try {
            if (!secretKey.equals(request.getSecretKey())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Secret key inválida");
                return ResponseEntity.badRequest().body(error);
            }

            String token = tokenProvider.generateEditToken();
            JwtResponse response = new JwtResponse(token, "UPLOAD, DELETE");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro interno do servidor");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Login para download por ambiente
     */
    @PostMapping("/login/download")
    @Operation(summary = "Login para download por ambiente", 
               description = "Gera token JWT específico para download de imagens de um ambiente")
    public ResponseEntity<?> loginForDownload(@RequestBody DownloadLoginRequest request) {
        try {
            if (!secretKey.equals(request.getSecretKey())) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Secret key inválida");
                return ResponseEntity.badRequest().body(error);
            }

            if (request.getEnvironmentId() == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Environment ID é obrigatório");
                return ResponseEntity.badRequest().body(error);
            }

            String token = tokenProvider.generateDownloadToken(request.getEnvironmentId());
            JwtResponse response = new JwtResponse(token, "DOWNLOAD", request.getEnvironmentId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro interno do servidor");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Verificar validade do token
     */
    @PostMapping("/validate")
    @Operation(summary = "Validar token", 
               description = "Verifica se um token JWT é válido e retorna suas permissões")
    public ResponseEntity<?> validateToken(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Token é obrigatório");
                return ResponseEntity.badRequest().body(error);
            }

            boolean isValid = tokenProvider.validateToken(token);
            if (!isValid) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Token inválido ou expirado");
                return ResponseEntity.badRequest().body(error);
            }

            var claims = tokenProvider.getClaimsFromToken(token);
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("subject", claims.getSubject());
            response.put("permissions", claims.get("permissions"));
            response.put("environmentId", claims.get("environmentId"));
            response.put("expiration", claims.getExpiration());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro ao validar token");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Health check específico para autenticação
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Verifica se o serviço de autenticação está funcionando")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "auth-service");
        return ResponseEntity.ok(response);
    }
}
