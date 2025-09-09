package com.scenario.images.service;

import com.scenario.images.model.EnvironmentImage;
import com.scenario.images.repository.EnvironmentImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ImageService {

    @Autowired
    private EnvironmentImageRepository imageRepository;

    @Value("${app.images.upload-dir:./images}")
    private String uploadDir;

    @Value("${app.images.max-file-size:5242880}") // 5MB default
    private long maxFileSize;

    private static final String[] ALLOWED_CONTENT_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    };

    /**
     * Upload de imagem para um ambiente
     */
    public EnvironmentImage uploadImage(Long environmentId, String imageName, MultipartFile file) throws IOException {
        // Validações
        validateFile(file);
        validateImageName(environmentId, imageName);

        // Gerar nome único para o arquivo
        String fileName = generateUniqueFileName(file.getOriginalFilename());
        
        // Criar diretório se não existir
        ensureUploadDirectoryExists();
        
        // Definir caminho completo
        Path filePath = Paths.get(uploadDir, fileName);
        
        // Salvar arquivo fisicamente
        Files.copy(file.getInputStream(), filePath);
        
        // Criar registro no banco
        EnvironmentImage image = new EnvironmentImage(
            environmentId,
            imageName,
            fileName,
            filePath.toString(),
            file.getContentType(),
            file.getSize()
        );
        
        return imageRepository.save(image);
    }

    /**
     * Buscar imagens por ambiente
     */
    @Transactional(readOnly = true)
    public List<EnvironmentImage> getImagesByEnvironment(Long environmentId) {
        return imageRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId);
    }

    /**
     * Buscar imagem por ID
     */
    @Transactional(readOnly = true)
    public Optional<EnvironmentImage> getImageById(Long id) {
        return imageRepository.findById(id);
    }

    /**
     * Buscar imagem por nome em um ambiente
     */
    @Transactional(readOnly = true)
    public Optional<EnvironmentImage> getImageByNameAndEnvironment(Long environmentId, String imageName) {
        return imageRepository.findByEnvironmentIdAndImageName(environmentId, imageName);
    }

    /**
     * Deletar imagem
     */
    public boolean deleteImage(Long id) throws IOException {
        Optional<EnvironmentImage> imageOpt = imageRepository.findById(id);
        if (imageOpt.isPresent()) {
            EnvironmentImage image = imageOpt.get();
            
            // Deletar arquivo físico
            Path filePath = Paths.get(image.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
            
            // Deletar registro do banco
            imageRepository.delete(image);
            return true;
        }
        return false;
    }

    /**
     * Atualizar nome da imagem
     */
    public EnvironmentImage updateImageName(Long id, String newImageName) {
        EnvironmentImage image = imageRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Imagem não encontrada"));
        
        // Verificar se o novo nome já existe no mesmo ambiente
        if (!image.getImageName().equals(newImageName) && 
            imageRepository.existsByEnvironmentIdAndImageName(image.getEnvironmentId(), newImageName)) {
            throw new RuntimeException("Já existe uma imagem com este nome no ambiente");
        }
        
        image.setImageName(newImageName);
        return imageRepository.save(image);
    }

    /**
     * Obter arquivo físico
     */
    @Transactional(readOnly = true)
    public File getPhysicalFile(String fileName) {
        Optional<EnvironmentImage> imageOpt = imageRepository.findByFileName(fileName);
        if (imageOpt.isPresent()) {
            return new File(imageOpt.get().getFilePath());
        }
        throw new RuntimeException("Arquivo não encontrado: " + fileName);
    }

    /**
     * Contar imagens por ambiente
     */
    @Transactional(readOnly = true)
    public long countImagesByEnvironment(Long environmentId) {
        return imageRepository.countByEnvironmentId(environmentId);
    }

    // Métodos auxiliares

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("Arquivo está vazio");
        }

        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("Arquivo muito grande. Tamanho máximo: " + (maxFileSize / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        boolean isValidType = false;
        for (String allowedType : ALLOWED_CONTENT_TYPES) {
            if (allowedType.equals(contentType)) {
                isValidType = true;
                break;
            }
        }

        if (!isValidType) {
            throw new RuntimeException("Tipo de arquivo não permitido. Tipos aceitos: JPEG, PNG, GIF, WebP");
        }
    }

    private void validateImageName(Long environmentId, String imageName) {
        if (imageName == null || imageName.trim().isEmpty()) {
            throw new RuntimeException("Nome da imagem é obrigatório");
        }

        if (imageRepository.existsByEnvironmentIdAndImageName(environmentId, imageName)) {
            throw new RuntimeException("Já existe uma imagem com este nome no ambiente");
        }
    }

    private String generateUniqueFileName(String originalFileName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFileName);
        return timestamp + "_" + uuid + extension;
    }

    private String getFileExtension(String fileName) {
        if (fileName != null && fileName.lastIndexOf('.') > 0) {
            return fileName.substring(fileName.lastIndexOf('.'));
        }
        return "";
    }

    private void ensureUploadDirectoryExists() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }
}
