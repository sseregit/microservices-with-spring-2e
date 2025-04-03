package se.magnus.microservices.core.product.services;

import static java.util.logging.Level.FINE;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.product.ProductService;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.microservices.core.product.persistence.ProductEntity;
import se.magnus.microservices.core.product.persistence.ProductRepository;
import se.magnus.util.http.ServiceUtil;

@RestController
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ServiceUtil serviceUtil;
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductServiceImpl(ServiceUtil serviceUtil, ProductRepository repository,
        ProductMapper mapper) {
        this.serviceUtil = serviceUtil;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Product> getProduct(int productId) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }

        return repository.findByProductId(productId).switchIfEmpty(
                Mono.error(new NotFoundException("No product found for productId: " + productId)))
            .log(log.getName(), FINE).map(mapper::entityToApi).map(e -> setServiceAddress(e));
    }

    private Product setServiceAddress(Product e) {
        e.setServiceAddress(serviceUtil.getServiceAddress());
        return e;
    }

    @Override
    public Mono<Product> createProduct(Product body) {

        if (body.getProductId() < 1) {
            throw new InvalidInputException("Invalid productId: " + body.getProductId());
        }

        ProductEntity entity = mapper.apiToEntity(body);
        Mono<Product> newEntity = repository.save(entity)
            .log(log.getName(), FINE)
            .onErrorMap(DuplicateKeyException.class,
                ex -> new InvalidInputException(
                    "Duplicate key, Product Id: " + body.getProductId()))
            .map(e -> mapper.entityToApi(e));

        return newEntity;
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        if (productId < 1) {
            throw new InvalidInputException("Invalid productId: " + productId);
        }

        log.debug("deleteProduct: tries to delete an entity with productId: {}", productId);
        return repository.findByProductId(productId).log(log.getName(), FINE)
            .map(e -> repository.delete(e)).flatMap(e -> e);
    }
}
