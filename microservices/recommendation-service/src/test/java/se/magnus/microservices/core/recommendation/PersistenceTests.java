package se.magnus.microservices.core.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import se.magnus.microservices.core.recommendation.persistence.RecommendationEntity;
import se.magnus.microservices.core.recommendation.persistence.RecommendationRepository;

@DataMongoTest
class PersistenceTests extends MongoDbTestBase {

    @Autowired
    private RecommendationRepository repository;

    private RecommendationEntity savedEntity;

    @BeforeEach
    void setupDb() {
        repository.deleteAll().block();

        RecommendationEntity entity = new RecommendationEntity(1, 2, "a", 3, "c");
        savedEntity = repository.save(entity).block();

        assertEqualsRecommendation(entity, savedEntity);
    }

    @Test
    void create() {
        RecommendationEntity newEntity = new RecommendationEntity(1, 3, "a", 3, "c");
        repository.save(newEntity).block();

        RecommendationEntity foundEntity = repository.findById(newEntity.getId()).block();
        assertEqualsRecommendation(newEntity, foundEntity);

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void update() {
        savedEntity.setAuthor("a2");
        repository.save(savedEntity).block();

        RecommendationEntity foundEntity = repository.findById(savedEntity.getId()).block();
        assertThat(foundEntity.getVersion()).isEqualTo(1);
        assertThat(foundEntity.getAuthor()).isEqualTo("a2");
    }

    @Test
    void delete() {
        repository.delete(savedEntity);
        assertThat(repository.existsById(savedEntity.getId()).block()).isFalse();
    }

    @Test
    void getByProductId() {
        List<RecommendationEntity> entityList = repository.findByProductId(
            savedEntity.getProductId()).collectList().block();

        assertThat(entityList).hasSize(1);
        assertEqualsRecommendation(savedEntity, entityList.get(0));
    }

    @Test
    void duplicateError() {
        assertThatThrownBy(() -> {
            RecommendationEntity entity = new RecommendationEntity(1, 2, "a", 3, "c");
            repository.save(entity).block();
        }).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void optimisticLockError() {
        RecommendationEntity entity1 = repository.findById(savedEntity.getId()).block();
        RecommendationEntity entity2 = repository.findById(savedEntity.getId()).block();

        entity1.setAuthor("a1");
        repository.save(entity1);

        assertThatThrownBy(() -> {
            entity2.setAuthor("a2");
            repository.save(entity2).block();
        }).isInstanceOf(OptimisticLockingFailureException.class);

        RecommendationEntity updatedEntity = repository.findById(savedEntity.getId()).block();
        assertThat(updatedEntity.getVersion()).isEqualTo(1);
        assertThat(updatedEntity.getAuthor()).isEqualTo("a1");
    }

    private void assertEqualsRecommendation(RecommendationEntity expectedEntity,
        RecommendationEntity actualEntity) {
        assertThat(actualEntity.getId()).isEqualTo(expectedEntity.getId());
        assertThat(actualEntity.getVersion()).isEqualTo(expectedEntity.getVersion());
        assertThat(actualEntity.getProductId()).isEqualTo(expectedEntity.getProductId());
        assertThat(actualEntity.getRecommendationId()).isEqualTo(
            expectedEntity.getRecommendationId());
        assertThat(actualEntity.getAuthor()).isEqualTo(expectedEntity.getAuthor());
        assertThat(actualEntity.getRating()).isEqualTo(expectedEntity.getRating());
        assertThat(actualEntity.getContent()).isEqualTo(expectedEntity.getContent());
    }
}
