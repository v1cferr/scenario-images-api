package com.scenario.images.repository;

import com.scenario.images.model.EnvironmentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentImageRepository extends JpaRepository<EnvironmentImage, Long> {

    /**
     * Buscar todas as imagens de um ambiente específico
     */
    List<EnvironmentImage> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

    /**
     * Buscar imagem por nome em um ambiente específico
     */
    Optional<EnvironmentImage> findByEnvironmentIdAndImageName(Long environmentId, String imageName);

    /**
     * Verificar se existe imagem com o nome em um ambiente
     */
    boolean existsByEnvironmentIdAndImageName(Long environmentId, String imageName);

    /**
     * Buscar imagem por nome do arquivo
     */
    Optional<EnvironmentImage> findByFileName(String fileName);

    /**
     * Verificar se arquivo já existe
     */
    boolean existsByFileName(String fileName);

    /**
     * Contar imagens por ambiente
     */
    @Query("SELECT COUNT(e) FROM EnvironmentImage e WHERE e.environmentId = :environmentId")
    long countByEnvironmentId(@Param("environmentId") Long environmentId);

    /**
     * Buscar imagens por tipo de conteúdo
     */
    List<EnvironmentImage> findByContentTypeContainingIgnoreCase(String contentType);
}
