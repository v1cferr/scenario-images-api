package com.scenario.images.controller;

import com.scenario.images.model.EnvironmentImage;
import com.scenario.images.service.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
public class ImageController {

    @Autowired
    private ImageService imageService;

    /**
     * Upload de imagem para um ambiente
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("environmentId") Long environmentId,
            @RequestParam("imageName") String imageName,
            @RequestParam("file") MultipartFile file) {
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
     * Buscar todas as imagens de um ambiente
     */
    @GetMapping("/environment/{environmentId}")
    public ResponseEntity<List<EnvironmentImage>> getImagesByEnvironment(@PathVariable Long environmentId) {
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
    public ResponseEntity<Resource> getImageFile(@PathVariable String fileName) {
        try {
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
    public ResponseEntity<?> deleteImage(@PathVariable Long id) {
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

    // Método auxiliar para determinar content type
    private String determineContentType(String fileName) {
        String extension = fileName.toLowerCase();
        if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (extension.endsWith(".png")) {
            return "image/png";
        } else if (extension.endsWith(".gif")) {
            return "image/gif";
        } else if (extension.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }
}
