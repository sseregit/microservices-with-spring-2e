package se.magnus.microservices.core.product.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ProductRepository extends ReactiveMongoRepository<ProductEntity, String> {

    Mono<ProductEntity> findByProductId(int productId);
}
