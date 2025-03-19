package se.magnus.microservices.core.recommendation.persistence;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RecommendationRepository extends CrudRepository<RecommendationEntity, Integer> {
    List<RecommendationEntity> findByProductId(int productId);
}
