package se.magnus.microservices.core.product;

import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.test.StepVerifier;
import se.magnus.microservices.core.product.persistence.ProductEntity;
import se.magnus.microservices.core.product.persistence.ProductRepository;

@DataMongoTest(properties = {})
class PersistenceTests extends MongoDbTestBase {

    @Autowired
    ProductRepository repository;
    ProductEntity savedEntity;

    @BeforeEach
    void setupDb() {
        StepVerifier.create(repository.deleteAll()).verifyComplete();

        ProductEntity entity = new ProductEntity(1, "n", 1);

        StepVerifier.create(repository.save(entity)).expectNextMatches(createdEntity -> {
            savedEntity = createdEntity;
            return areProductEqual(entity, savedEntity);
        }).verifyComplete();
    }

    @Test
    void create() {
        ProductEntity newEntity = new ProductEntity(2, "n", 2);
        StepVerifier.create(repository.save(newEntity))
            .expectNextMatches(createdEntity -> newEntity.getId() == createdEntity.getId())
            .verifyComplete();

        StepVerifier.create(repository.findById(newEntity.getId()))
            .expectNextMatches(foundEntity -> areProductEqual(newEntity, foundEntity))
            .verifyComplete();

        StepVerifier.create(repository.count()).expectNext(2L).verifyComplete();
    }

    @Test
    void update() {
        savedEntity.setName("n2");
        StepVerifier.create(repository.save(savedEntity))
            .expectNextMatches(updatedEntity -> updatedEntity.getName().equals("n2"))
            .verifyComplete();

        StepVerifier.create(repository.findById(savedEntity.getId())).expectNextMatches(
                foundEntity -> foundEntity.getVersion() == 1 && foundEntity.getName().equals("n2"))
            .verifyComplete();
    }

    @Test
    void delete() {
        StepVerifier.create(repository.delete(savedEntity)).verifyComplete();
        StepVerifier.create(repository.existsById(savedEntity.getId())).expectNext(false)
            .verifyComplete();
    }

    @Test
    void getByProductId() {
        StepVerifier.create(repository.findByProductId(savedEntity.getProductId()))
            .expectNextMatches(foundEntity -> areProductEqual(savedEntity, foundEntity))
            .verifyComplete();
    }

    @Test
    void duplicateError() {
        ProductEntity entity = new ProductEntity(savedEntity.getProductId(), "n", 1);

        StepVerifier.create(repository.save(entity)).expectError(DuplicateKeyException.class)
            .verify();
    }

    @Test
    void optimisticLockError() {
        ProductEntity entity1 = repository.findById(savedEntity.getId()).block();
        ProductEntity entity2 = repository.findById(savedEntity.getId()).block();

        entity1.setName("n1");
        repository.save(entity1).block();

        StepVerifier.create(repository.save(entity2))
            .expectError(OptimisticLockingFailureException.class).verify();

        StepVerifier.create(repository.findById(savedEntity.getId())).expectNextMatches(
                foundEntity -> foundEntity.getVersion() == 1 && foundEntity.getName().equals("n1"))
            .verifyComplete();

    }

    private boolean areProductEqual(ProductEntity expectedEntity, ProductEntity actualEntity) {
        return (Objects.equals(expectedEntity.getId(), actualEntity.getId())) && (
            expectedEntity.getVersion() == actualEntity.getVersion()) && (
            expectedEntity.getProductId() == actualEntity.getProductId()) && (Objects.equals(
            expectedEntity.getName(), actualEntity.getName())) && (expectedEntity.getWeight()
            == actualEntity.getWeight());
    }

}
