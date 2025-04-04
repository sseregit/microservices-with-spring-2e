package se.magnus.microservices.core.recommendation.services;

import static java.util.logging.Level.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.recommendation.RecommendationService;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.microservices.core.recommendation.persistence.RecommendationEntity;
import se.magnus.microservices.core.recommendation.persistence.RecommendationRepository;
import se.magnus.util.http.ServiceUtil;

@RestController
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    private final ServiceUtil serviceUtil;
    private final RecommendationRepository repository;
    private final RecommendationMapper mapper;

    public RecommendationServiceImpl(ServiceUtil serviceUtil, RecommendationRepository repository,
        RecommendationMapper mapper) {
        this.serviceUtil = serviceUtil;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {

        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }

        log.info("Will get recommendations for product with id={}", productId);

        return repository.findByProductId(productId).log(log.getName(), FINE)
            .map(mapper::entityToApi)
            .map(this::setServiceAddress);
    }

    private Recommendation setServiceAddress(Recommendation e) {
        e.setServiceAddress(serviceUtil.getServiceAddress());
        return e;
    }

    @Override
    public Mono<Recommendation> createRecommendation(Recommendation body) {

        if (body.getProductId() < 1) {
            throw new InvalidInputException("Invalid productId: " + body.getProductId());
        }

        RecommendationEntity entity = mapper.apiToEntity(body);
        Mono<Recommendation> newEntity = repository.save(entity)
            .log(log.getName(), FINE)
            .onErrorMap(DuplicateKeyException.class,
                ex -> new InvalidInputException(
                    "Duplicate key, Product Id: " + body.getProductId() + ", Recommendation Id:"
                        + body.getRecommendationId()))
            .map(e -> mapper.entityToApi(e));

        return newEntity;
    }

    @Override
    public Mono<Void> deleteRecommendations(int productId) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }

        log.debug(
            "deleteRecommendations: tries to delete recommendations for the product with productId: {}",
            productId);
        return repository.deleteAll(repository.findByProductId(productId));
    }
}
