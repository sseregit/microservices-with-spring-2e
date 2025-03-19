package se.magnus.microservices.core.product;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import se.magnus.microservices.core.product.persistence.ProductEntity;
import se.magnus.microservices.core.product.persistence.ProductRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.stream.IntStream.rangeClosed;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.domain.Sort.Direction.ASC;

@DataMongoTest
class PersistenceTests extends MongoDbTestBase {

    @Autowired
    ProductRepository repository;
    ProductEntity savedEntity;

    @BeforeEach
    void setupDb() {
        repository.deleteAll();
        ProductEntity entity = new ProductEntity(1, "n", 1);
        savedEntity = repository.save(entity);
        assertThat(entity).isEqualTo(savedEntity);
    }

    @Test
    void create() {
        ProductEntity newEntity = new ProductEntity(2, "n", 2);
        savedEntity = repository.save(newEntity);

        ProductEntity foundEntity = repository.findById(newEntity.getId()).get();
        assertEqualsProduct(foundEntity, newEntity);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void update() {
        savedEntity.setName("n2");
        repository.save(savedEntity);

        ProductEntity foundEntity = repository.findById(savedEntity.getId()).get();

        assertThat(foundEntity.getVersion()).isEqualTo(1);
        assertThat(foundEntity.getName()).isEqualTo("n2");
    }

    @Test
    void delete() {
        repository.delete(savedEntity);
        assertThat(repository.existsById(savedEntity.getId())).isFalse();
    }

    @Test
    void getByProductId() {
        Optional<ProductEntity> entity = repository.findByProductId(savedEntity.getProductId());
        assertThat(entity.isPresent()).isTrue();
        assertEqualsProduct(savedEntity, entity.get());
    }

    @Test
    void duplicateError() {
        assertThatThrownBy(() -> {
            ProductEntity entity = new ProductEntity(savedEntity.getProductId(), "n", 1);
            repository.save(entity);
        }).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void optimisticLockError() {
        ProductEntity entity1 = repository.findById(savedEntity.getId()).get();
        ProductEntity entity2 = repository.findById(savedEntity.getId()).get();

        entity1.setName("n1");
        repository.save(entity1);

        assertThatThrownBy(() -> {
            entity2.setName("n2");
            repository.save(entity2);
        }).isInstanceOf(OptimisticLockingFailureException.class);

        ProductEntity updateEntity = repository.findById(savedEntity.getId()).get();
        assertThat(updateEntity.getVersion()).isEqualTo(1);
        assertThat(updateEntity.getName()).isEqualTo("n1");
    }

    @Test
    void paging() {
        repository.deleteAll();

        List<ProductEntity> newProducts = rangeClosed(1001, 1010)
                .mapToObj(i -> new ProductEntity(i, "name " + i, i))
                .toList();
        repository.saveAll(newProducts);

        Pageable nextPage = PageRequest.of(0, 4, ASC, "productId");
        nextPage = testNextPage(nextPage, "[1001, 1002, 1003, 1004]", true);
        nextPage = testNextPage(nextPage, "[1005, 1006, 1007, 1008]", true);
        nextPage = testNextPage(nextPage, "[1009, 1010]", false);

    }

    private Pageable testNextPage(Pageable nextPage, String expectedProductIds, boolean expectsNextPage) {
        Page<ProductEntity> productPage = repository.findAll(nextPage);
        assertThat(expectedProductIds).isEqualTo(productPage.getContent().stream().map(p -> p.getProductId()).toList().toString());
        assertThat(productPage.hasNext()).isEqualTo(expectsNextPage);
        return productPage.nextPageable();
    }

    private void assertEqualsProduct(ProductEntity expectedEntity, ProductEntity actualEntity) {
        assertThat(actualEntity.getId()).isEqualTo(expectedEntity.getId());
        assertThat(actualEntity.getVersion()).isEqualTo(expectedEntity.getVersion());
        assertThat(actualEntity.getProductId()).isEqualTo(expectedEntity.getProductId());
        assertThat(actualEntity.getName()).isEqualTo(expectedEntity.getName());
        assertThat(actualEntity.getWeight()).isEqualTo(expectedEntity.getWeight());
    }
}
