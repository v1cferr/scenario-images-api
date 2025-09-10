package com.scenario.images.controller;

import com.scenario.images.model.EnvironmentImage;
import com.scenario.images.security.JwtTokenProvider;
import com.scenario.images.service.ImageService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
@Tag(name = "Gerenciamento de Imagens", description = "Endpoints para upload, download e gerenciamento de imagens")
public class ImageController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    /**
     * Upload de imagem para um ambiente
     */
    @PostMapping("/upload")
    @Operation(summary = "Upload de imagem", 
               description = "Faz upload de uma imagem para um ambiente específico. Requer autenticação.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> uploadImage(
            @Parameter(description = "ID do ambiente") @RequestParam("environmentId") Long environmentId,
            @Parameter(description = "Nome da imagem") @RequestParam("imageName") String imageName,
            @Parameter(description = "Arquivo da imagem") @RequestParam("file") MultipartFile file) {
        try {
            EnvironmentImage savedImage = imageService.uploadImage(environmentId, imageName, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedImage);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro ao fazer upload da imagem");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro interno ao salvar arquivo");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Buscar todas as imagens de um ambiente (endpoint interno)
     */
    @GetMapping("/internal/environment/{environmentId}")
    @Operation(summary = "Listar imagens do ambiente (interno)", 
               description = "Lista todas as imagens de um ambiente específico - uso interno entre APIs")
    public ResponseEntity<List<EnvironmentImage>> getImagesByEnvironmentInternal(
            @Parameter(description = "ID do ambiente") @PathVariable Long environmentId,
            @RequestHeader("X-Internal-API-Key") String apiKey) {
        
        // Verificar chave de API interna
        if (!"ScenarioInternalAPIKey2024ForImagesCommunication".equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<EnvironmentImage> images = imageService.getImagesByEnvironment(environmentId);
        return ResponseEntity.ok(images);
    }

    /**
     * Buscar todas as imagens de um ambiente
     */
    @GetMapping("/environment/{environmentId}")
    @Operation(summary = "Listar imagens do ambiente", 
               description = "Lista todas as imagens de um ambiente específico")
    public ResponseEntity<List<EnvironmentImage>> getImagesByEnvironment(
            @Parameter(description = "ID do ambiente") @PathVariable Long environmentId) {
        List<EnvironmentImage> images = imageService.getImagesByEnvironment(environmentId);
        return ResponseEntity.ok(images);
    }

    /**
     * Buscar imagem por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getImageById(@PathVariable Long id) {
        Optional<EnvironmentImage> image = imageService.getImageById(id);
        if (image.isPresent()) {
            return ResponseEntity.ok(image.get());
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Imagem não encontrada");
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Download da primeira imagem de um ambiente
     */
    @GetMapping("/environment/{environmentId}/download")
    @Operation(summary = "Download da imagem do ambiente", 
               description = "Faz download da primeira imagem de um ambiente específico")
    public ResponseEntity<Resource> downloadEnvironmentImage(
            @Parameter(description = "ID do ambiente") @PathVariable Long environmentId) {
        try {
            // Buscar primeira imagem do ambiente
            List<EnvironmentImage> images = imageService.getImagesByEnvironment(environmentId);
            if (images.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            EnvironmentImage image = images.get(0); // Primeira imagem
            File file = imageService.getPhysicalFile(image.getFileName());
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            
            // Determinar content type baseado na extensão
            String contentType = determineContentType(image.getFileName());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.getFileName() + "\"")
                    .body(resource);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Buscar imagem por nome em um ambiente específico
     */
    @GetMapping("/environment/{environmentId}/name/{imageName}")
    public ResponseEntity<?> getImageByNameAndEnvironment(
            @PathVariable Long environmentId, 
            @PathVariable String imageName) {
        Optional<EnvironmentImage> image = imageService.getImageByNameAndEnvironment(environmentId, imageName);
        if (image.isPresent()) {
            return ResponseEntity.ok(image.get());
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Imagem não encontrada");
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Servir arquivo de imagem
     */
    @GetMapping("/file/{fileName}")
    @Operation(summary = "Download de imagem", 
               description = "Faz download de uma imagem. Requer token de download específico para o ambiente.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Resource> getImageFile(
            @Parameter(description = "Nome do arquivo") @PathVariable String fileName,
            HttpServletRequest request) {
        try {
            // Verificar permissão de download
            String token = getJwtFromRequest(request);
            if (token == null || !tokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Buscar informações da imagem para verificar o ambiente
            EnvironmentImage imageInfo = imageService.getImageInfoByFileName(fileName);
            if (imageInfo == null) {
                return ResponseEntity.notFound().build();
            }

            File file = imageService.getPhysicalFile(fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            
            // Determinar content type baseado na extensão
            String contentType = determineContentType(fileName);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Atualizar nome da imagem
     */
    @PatchMapping("/{id}/name")
    public ResponseEntity<?> updateImageName(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            String newImageName = request.get("imageName");
            if (newImageName == null || newImageName.trim().isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Nome da imagem é obrigatório");
                return ResponseEntity.badRequest().body(error);
            }

            EnvironmentImage updatedImage = imageService.updateImageName(id, newImageName);
            return ResponseEntity.ok(updatedImage);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro ao atualizar nome da imagem");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Deletar imagem
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar imagem", 
               description = "Remove uma imagem do sistema. Requer autenticação.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> deleteImage(
            @Parameter(description = "ID da imagem") @PathVariable Long id) {
        try {
            boolean deleted = imageService.deleteImage(id);
            if (deleted) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Imagem deletada com sucesso");
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Imagem não encontrada");
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro ao deletar arquivo");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Gerar URL temporária para uma imagem específica
     */
    @PostMapping("/generate-temp-url")
    @Operation(summary = "Gerar URL temporária", 
               description = "Gera uma URL temporária com token para acesso seguro a uma imagem")
    public ResponseEntity<?> generateTemporaryImageUrl(@RequestBody Map<String, Object> request) {
        try {
            Long environmentId = Long.valueOf(request.get("environmentId").toString());
            String fileName = (String) request.get("fileName");
            Integer expirationMinutes = (Integer) request.getOrDefault("expirationMinutes", 10);
            
            // Verificar se a imagem existe e pertence ao ambiente
            EnvironmentImage image = imageService.getImageInfoByFileName(fileName);
            if (image == null || !image.getEnvironmentId().equals(environmentId)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Imagem não encontrada ou não pertence ao ambiente especificado");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            
            // Gerar token específico para esta imagem
            Map<String, Object> claims = new HashMap<>();
            claims.put("environmentId", environmentId);
            claims.put("fileName", fileName);
            claims.put("type", "image-download");
            claims.put("exp", System.currentTimeMillis() + (expirationMinutes * 60 * 1000));
            
            String tempToken = tokenProvider.generateTokenWithClaims("image-download", claims, expirationMinutes * 60 * 1000);
            String tempUrl = String.format("http://localhost:8081/api/images/secure-file/%d/%s?token=%s", environmentId, fileName, tempToken);
            
            Map<String, Object> response = new HashMap<>();
            response.put("url", tempUrl);
            response.put("fileName", fileName);
            response.put("environmentId", environmentId);
            response.put("expiresInMinutes", expirationMinutes);
            response.put("token", tempToken);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro ao gerar URL temporária");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Servir imagem com token temporário específico
     */
    /**
     * Endpoint de teste ultra-simples
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Test OK");
    }

    @GetMapping("/secure-file/{environmentId}/{fileName}")
    @Operation(summary = "Download seguro de imagem", 
               description = "Faz download de uma imagem usando token temporário específico")
    public ResponseEntity<Resource> getSecureImageFile(
            @PathVariable Long environmentId,
            @PathVariable String fileName,
            @RequestParam("token") String token) {
        
        try {
            // Validar token
            if (!tokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Extrair claims do token
            Claims claims = tokenProvider.getClaimsFromToken(token);
            String tokenFileName = (String) claims.get("fileName");
            Object envIdObj = claims.get("environmentId");
            Long tokenEnvironmentId = envIdObj instanceof Integer ? ((Integer) envIdObj).longValue() : (Long) envIdObj;
            String tokenType = (String) claims.get("type");
            Long expiration = Long.valueOf(claims.get("exp").toString());
            
            // Verificar se o token não expirou
            long currentTimeInSeconds = System.currentTimeMillis() / 1000;
            if (currentTimeInSeconds > expiration) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Verificar se é o tipo correto de token
            if (!"image-download".equals(tokenType)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Verificar se o arquivo do token corresponde ao solicitado
            if (!fileName.equals(tokenFileName)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            // Verificar se o environment ID da URL corresponde ao do token
            if (!environmentId.equals(tokenEnvironmentId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Verificar se a imagem existe e pertence ao ambiente correto
            EnvironmentImage image = imageService.getImageInfoByFileName(fileName);
            if (image == null) {
                return ResponseEntity.notFound().build();
            }
            
            if (!image.getEnvironmentId().equals(tokenEnvironmentId)) {
                return ResponseEntity.notFound().build();
            }
            
            // Servir arquivo
            File file = imageService.getPhysicalFile(fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            String contentType = determineContentType(fileName);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .header("Access-Control-Allow-Origin", "*")
                    .header("Cache-Control", "max-age=3600")
                    .body(resource);
                    
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletar todas as imagens de um ambiente
     */
    @DeleteMapping("/environment/{environmentId}")
    @Operation(summary = "Deletar todas as imagens de um ambiente", 
               description = "Remove todas as imagens de um ambiente específico. Requer autenticação.",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> deleteAllImagesByEnvironment(
            @Parameter(description = "ID do ambiente") @PathVariable Long environmentId) {
        try {
            int deletedCount = imageService.deleteAllImagesByEnvironment(environmentId);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Imagens do ambiente deletadas com sucesso");
            response.put("deletedCount", deletedCount);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erro ao deletar imagens do ambiente");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Contar imagens de um ambiente
     */
    @GetMapping("/environment/{environmentId}/count")
    public ResponseEntity<Map<String, Long>> countImagesByEnvironment(@PathVariable Long environmentId) {
        long count = imageService.countImagesByEnvironment(environmentId);
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "scenario-images-api");
        return ResponseEntity.ok(response);
    }

    // Método auxiliar para extrair JWT do request
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Endpoint de teste para debug do JWT
     */
    @GetMapping("/debug/simple")
    public ResponseEntity<Map<String, Object>> debugSimple() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Endpoint simples funcionando");
        response.put("tokenProvider", tokenProvider != null ? "OK" : "NULL");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/debug/token")
    public ResponseEntity<Map<String, Object>> debugToken(@RequestParam("token") String token) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("🔍 DEBUG - Testando token: " + token.substring(0, 50) + "...");
            System.out.println("🔧 DEBUG - TokenProvider: " + (tokenProvider != null ? "OK" : "NULL"));
            
            if (tokenProvider == null) {
                response.put("error", "TokenProvider is null");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            boolean isValid = tokenProvider.validateToken(token);
            response.put("isValid", isValid);
            
            if (isValid) {
                Claims claims = tokenProvider.getClaimsFromToken(token);
                response.put("subject", claims.getSubject());
                response.put("fileName", claims.get("fileName"));
                response.put("environmentId", claims.get("environmentId"));
                response.put("type", claims.get("type"));
                response.put("expiration", claims.getExpiration());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("💥 Erro no debug do token: " + e.getMessage());
            e.printStackTrace();
            response.put("error", e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    private String determineContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            default:
                return "application/octet-stream";
        }
    }
}
