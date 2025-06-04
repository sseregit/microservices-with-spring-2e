package se.magnus.microservices.composite.product;

import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.review.Review;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.microservices.composite.product.services.ProductCompositeIntegration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.main.allow-bean-definition-overriding=true"},
    classes = {TestSecurityConfig.class})
class ProductCompositeServiceApplicationTests {

    private static final int PRODUCT_ID_OK = 1;
    private static final int PRODUCT_ID_NOT_FOUND = 2;
    private static final int PRODUCT_ID_INVALID = 3;

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private ProductCompositeIntegration compositeIntegration;

    @BeforeEach
    void setUp() {
        when(compositeIntegration.getProduct(PRODUCT_ID_OK, 0, 0)).thenReturn(
            Mono.just(new Product(PRODUCT_ID_OK, "name", 1, "mock-address")));
        when(compositeIntegration.getRecommendations(PRODUCT_ID_OK)).thenReturn(
            Flux.fromIterable(List.of(
                new Recommendation(PRODUCT_ID_OK, 1, "author", 1, "content", "mock address"))));
        when(compositeIntegration.getReviews(PRODUCT_ID_OK)).thenReturn(
            Flux.fromIterable(List.of(
                new Review(PRODUCT_ID_OK, 1, "author", "subject", "content", "mock address"))));
        when(compositeIntegration.getProduct(PRODUCT_ID_NOT_FOUND, 0, 0)).thenThrow(
            new NotFoundException("NOT FOUND: " + PRODUCT_ID_NOT_FOUND));
        when(compositeIntegration.getProduct(PRODUCT_ID_INVALID, 0, 0)).thenThrow(
            new InvalidInputException("INVALID: " + PRODUCT_ID_INVALID));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void getProductById() {
        getAndVerifyProduct(PRODUCT_ID_OK, OK).jsonPath("$.productId").isEqualTo(PRODUCT_ID_OK)
            .jsonPath("$.recommendations.length()").isEqualTo(1).jsonPath("$.reviews.length()")
            .isEqualTo(1);
    }

    @Test
    void getProductNotFound() {
        getAndVerifyProduct(PRODUCT_ID_NOT_FOUND, NOT_FOUND).jsonPath("$.path")
            .isEqualTo("/product-composite/" + PRODUCT_ID_NOT_FOUND).jsonPath("$.message")
            .isEqualTo("NOT FOUND: " + PRODUCT_ID_NOT_FOUND);
    }

    @Test
    void getProductInvalidInput() {
        getAndVerifyProduct(PRODUCT_ID_INVALID, UNPROCESSABLE_ENTITY).jsonPath("$.path")
            .isEqualTo("/product-composite/" + PRODUCT_ID_INVALID).jsonPath("$.message")
            .isEqualTo("INVALID: " + PRODUCT_ID_INVALID);
    }

    private WebTestClient.BodyContentSpec getAndVerifyProduct(int productId,
        HttpStatus expectedStatus) {
        return client.get().uri("/product-composite/" + productId).accept(APPLICATION_JSON)
            .exchange().expectStatus().isEqualTo(expectedStatus).expectHeader()
            .contentType(APPLICATION_JSON).expectBody();
    }
}
