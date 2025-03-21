package se.magnus.microservices.core.recommendation.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecommendationRepository extends MongoRepository<RecommendationEntity, String> {
    List<RecommendationEntity> findByProductId(int productId);
}
