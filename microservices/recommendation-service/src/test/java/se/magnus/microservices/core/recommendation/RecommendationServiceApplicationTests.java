package se.magnus.microservices.core.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static se.magnus.api.event.Event.Type.CREATE;
import static se.magnus.api.event.Event.Type.DELETE;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.event.Event;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.microservices.core.recommendation.persistence.RecommendationRepository;

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {})
class RecommendationServiceApplicationTests extends MongoDbTestBase {

    @Autowired
    private WebTestClient client;

    @Autowired
    private RecommendationRepository repository;

    @Autowired
    @Qualifier("messageProcessor")
    private Consumer<Event<Integer, Recommendation>> messageProcessor;

    @BeforeEach
    void setupDb() {
        repository.deleteAll().block();
    }

    @Test
    void getRecommendationsByProductId() {

        int productId = 1;

        sendCreateRecommendationEvent(productId, 1);
        sendCreateRecommendationEvent(productId, 2);
        sendCreateRecommendationEvent(productId, 3);

        assertThat(repository.findByProductId(productId).count().block()).isEqualTo(3);

        getAndVerifyRecommendationsByProductId(productId, OK).jsonPath("$.length()").isEqualTo(3)
            .jsonPath("$[2].productId").isEqualTo(productId).jsonPath("$[2].recommendationId")
            .isEqualTo(3);
    }

    @Test
    void duplicateError() {

        int productId = 1;
        int recommendationId = 1;

        sendCreateRecommendationEvent(productId, recommendationId);

        assertThat(repository.count().block()).isEqualTo(1);

        assertThatThrownBy(() -> sendCreateRecommendationEvent(productId, recommendationId))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Duplicate key, Product Id: 1, Recommendation Id:1");

        assertThat(repository.count().block()).isEqualTo(1);
    }

    @Test
    void deleteRecommendations() {

        int productId = 1;
        int recommendationId = 1;

        sendCreateRecommendationEvent(productId, recommendationId);
        assertThat(repository.findByProductId(productId).count().block()).isEqualTo(1);

        sendDeleteRecommendationEvent(productId);
        assertThat(repository.findByProductId(productId).count().block()).isEqualTo(0);

        sendDeleteRecommendationEvent(productId);
    }

    @Test
    void getRecommendationsMissingParameter() {

        getAndVerifyRecommendationsByProductId("", BAD_REQUEST).jsonPath("$.path")
            .isEqualTo("/recommendation").jsonPath("$.message")
            .isEqualTo("Required query parameter 'productId' is not present.");
    }

    @Test
    void getRecommendationsInvalidParameter() {

        getAndVerifyRecommendationsByProductId("?productId=no-integer", BAD_REQUEST).jsonPath(
                "$.path").isEqualTo("/recommendation").jsonPath("$.message")
            .isEqualTo("Type mismatch.");
    }

    @Test
    void getRecommendationsNotFound() {

        getAndVerifyRecommendationsByProductId("?productId=113", OK).jsonPath("$.length()")
            .isEqualTo(0);
    }

    @Test
    void getRecommendationsInvalidParameterNegativeValue() {

        int productIdInvalid = -1;

        getAndVerifyRecommendationsByProductId("?productId=" + productIdInvalid,
            UNPROCESSABLE_ENTITY).jsonPath("$.path").isEqualTo("/recommendation")
            .jsonPath("$.message").isEqualTo("Invalid productId: " + productIdInvalid);
    }

    private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(int productId,
        HttpStatus expectedStatus) {
        return getAndVerifyRecommendationsByProductId("?productId=" + productId, expectedStatus);
    }

    private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(
        String productIdQuery, HttpStatus expectedStatus) {
        return client.get().uri("/recommendation" + productIdQuery).accept(APPLICATION_JSON)
            .exchange().expectStatus().isEqualTo(expectedStatus).expectHeader()
            .contentType(APPLICATION_JSON).expectBody();
    }

    private void sendCreateRecommendationEvent(int productId, int recommendationId) {
        Recommendation recommendation = new Recommendation(productId, recommendationId,
            "Author " + recommendationId, recommendationId, "Content " + recommendationId, "SA");
        Event<Integer, Recommendation> event = new Event(CREATE, productId, recommendation);
        messageProcessor.accept(event);
    }

    private void sendDeleteRecommendationEvent(int productId) {
        Event<Integer, Recommendation> event = new Event(DELETE, productId, null);
        messageProcessor.accept(event);
    }
}