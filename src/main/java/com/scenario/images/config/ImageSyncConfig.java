package com.scenario.images.config;

import com.scenario.images.model.EnvironmentImage;
import com.scenario.images.repository.EnvironmentImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Componente para sincronizar imagens físicas com o banco de dados na inicialização
 */
@Component
public class ImageSyncConfig implements CommandLineRunner {

    @Autowired
    private EnvironmentImageRepository imageRepository;

    @Value("${app.images.upload-dir:./images}")
    private String uploadDir;

    @Override
    public void run(String... args) throws Exception {
        syncPhysicalImagesWithDatabase();
    }

    private void syncPhysicalImagesWithDatabase() {
        System.out.println("🔄 Iniciando sincronização de imagens físicas com banco de dados...");
        
        File imagesDirectory = new File(uploadDir);
        if (!imagesDirectory.exists()) {
            System.out.println("📁 Diretório de imagens não existe: " + uploadDir);
            return;
        }

        File[] imageFiles = imagesDirectory.listFiles((dir, name) -> 
            name.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|webp)$"));

        if (imageFiles == null || imageFiles.length == 0) {
            System.out.println("📂 Nenhuma imagem física encontrada em: " + uploadDir);
            return;
        }

        System.out.println("📸 Encontradas " + imageFiles.length + " imagens físicas");

        for (File imageFile : imageFiles) {
            String fileName = imageFile.getName();
            
            // Verificar se já existe no banco
            boolean existsInDatabase = imageRepository.existsByFileName(fileName);
            
            if (!existsInDatabase) {
                System.out.println("🔧 Sincronizando imagem órfã: " + fileName);
                
                // Tentar extrair environmentId do nome do arquivo (formato: YYYYMMDD_HHMMSS_hash.ext)
                Long environmentId = extractEnvironmentIdFromFileName(fileName);
                if (environmentId == null) {
                    environmentId = 1L; // Fallback para ambiente padrão
                }
                
                // Criar registro no banco
                EnvironmentImage orphanImage = new EnvironmentImage();
                orphanImage.setEnvironmentId(environmentId);
                orphanImage.setImageName("Imagem sincronizada - " + fileName);
                orphanImage.setFileName(fileName);
                orphanImage.setFilePath(uploadDir + File.separator + fileName);
                orphanImage.setContentType(determineContentType(fileName));
                orphanImage.setFileSize(imageFile.length());
                orphanImage.setDescription("Imagem recuperada após sincronização");
                orphanImage.setCreatedAt(LocalDateTime.now());
                orphanImage.setUpdatedAt(LocalDateTime.now());
                
                imageRepository.save(orphanImage);
                System.out.println("✅ Imagem sincronizada: " + fileName + " -> Ambiente: " + environmentId);
            }
        }
        
        System.out.println("🏁 Sincronização concluída!");
    }

    private Long extractEnvironmentIdFromFileName(String fileName) {
        // Se o arquivo tem metadata em algum lugar, podemos tentar extrair
        // Por enquanto, vamos usar um padrão ou fallback
        
        // Pattern para tentar extrair ID do nome do arquivo se seguir algum padrão
        Pattern pattern = Pattern.compile(".*env(\\d+).*");
        Matcher matcher = pattern.matcher(fileName);
        
        if (matcher.matches()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                // Ignorar erro
            }
        }
        
        // Fallback: retornar null para usar ID padrão
        return null;
    }

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
