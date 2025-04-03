package se.magnus.microservices.core.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
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
import se.magnus.api.core.product.Product;
import se.magnus.api.event.Event;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.microservices.core.product.persistence.ProductRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductServiceApplicationTests extends MongoDbTestBase {

    @Autowired
    private WebTestClient client;

    @Autowired
    private ProductRepository repository;

    @Autowired
    @Qualifier("messageProcessor")
    private Consumer<Event<Integer, Product>> messageProcessor;

    @BeforeEach
    void setupDb() {
        repository.deleteAll().block();
    }

    @Test
    void getProductById() {
        int productId = 1;

        assertThat(repository.findByProductId(productId).block()).isNull();
        assertThat(repository.count().block()).isZero();

        sendCreateProductEvent(productId);

        assertThat(repository.findByProductId(productId).block()).isNotNull();
        assertThat(repository.count().block()).isEqualTo(1);

        getAndVerifyProduct(productId, OK).jsonPath("$.productId").isEqualTo(productId);
    }

    @Test
    void duplicateError() {
        int productId = 1;

        assertThat(repository.findByProductId(productId).block()).isNull();

        sendCreateProductEvent(productId);

        assertThat(repository.findByProductId(productId).block()).isNotNull();

        assertThatThrownBy(() -> sendCreateProductEvent(productId))
            .isInstanceOf(InvalidInputException.class)
            .hasMessage("Duplicate key, Product Id: " + productId);
    }

    @Test
    void deleteProduct() {
        int productId = 1;

        sendCreateProductEvent(productId);
        assertThat(repository.findByProductId(productId).block()).isNotNull();

        sendDeleteProductEvent(productId);
        assertThat(repository.findByProductId(productId).block()).isNull();

        sendDeleteProductEvent(productId);
    }

    @Test
    void getProductInvalidParameterString() {
        getAndVerifyProduct("/no-integer", BAD_REQUEST).jsonPath("$.path")
            .isEqualTo("/product/no-integer").jsonPath("$.message").isEqualTo("Type mismatch.");
    }

    @Test
    void getProductNotFound() {
        int productIdNotFound = 13;
        getAndVerifyProduct(productIdNotFound, NOT_FOUND).jsonPath("$.path")
            .isEqualTo("/product/" + productIdNotFound).jsonPath("$.message")
            .isEqualTo("No product found for productId: " + productIdNotFound);
    }

    @Test
    void getProductInvalidParameterNegativeValue() {
        int productIdInvalid = -1;
        getAndVerifyProduct(productIdInvalid, UNPROCESSABLE_ENTITY).jsonPath("$.path")
            .isEqualTo("/product/" + productIdInvalid).jsonPath("$.message")
            .isEqualTo("Invalid productId: " + productIdInvalid);
    }

    private WebTestClient.BodyContentSpec getAndVerifyProduct(int productId,
        HttpStatus expectedStatus) {
        return getAndVerifyProduct("/" + productId, expectedStatus);
    }

    private WebTestClient.BodyContentSpec getAndVerifyProduct(String productIdPath,
        HttpStatus expectedStatus) {
        return client.get().uri("/product" + productIdPath).accept(APPLICATION_JSON).exchange()
            .expectStatus().isEqualTo(expectedStatus).expectHeader().contentType(APPLICATION_JSON)
            .expectBody();
    }

    private void sendCreateProductEvent(int productId) {
        Product product = new Product(productId, "Name " + productId, productId, "SA");
        Event<Integer, Product> event = new Event<>(CREATE, productId, product);
        messageProcessor.accept(event);
    }

    private void sendDeleteProductEvent(int productId) {
        Event<Integer, Product> event = new Event<>(DELETE, productId, null);
        messageProcessor.accept(event);
    }
}
