package se.magnus.microservices.core.recommendation.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface RecommendationRepository extends
    ReactiveMongoRepository<RecommendationEntity, String> {

    Flux<RecommendationEntity> findByProductId(int productId);
}
