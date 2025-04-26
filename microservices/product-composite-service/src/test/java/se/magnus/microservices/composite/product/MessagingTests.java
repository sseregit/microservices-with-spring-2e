package se.magnus.microservices.composite.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static se.magnus.api.event.Event.Type.CREATE;
import static se.magnus.api.event.Event.Type.DELETE;
import static se.magnus.microservices.composite.product.IsSameEvent.sameEventExceptCreatedAt;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import se.magnus.api.composite.product.ProductAggregate;
import se.magnus.api.composite.product.RecommendationSummary;
import se.magnus.api.composite.product.ReviewSummary;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.review.Review;
import se.magnus.api.event.Event;

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.main.allow-bean-definition-overriding=true",
        "eureka.client.enabled=false",
        "spring.cloud.stream.defaultBinder=rabbit",
        "spring.cloud.config.enabled=false"},
    classes = {TestSecurityConfig.class})
@Import({TestChannelBinderConfiguration.class})
@Slf4j
public class MessagingTests {

    @Autowired
    private WebTestClient client;

    @Autowired
    private OutputDestination target;

    @BeforeEach
    void setUp() {
        purgeMessage("products");
        purgeMessage("recommendations");
        purgeMessage("reviews");
    }

    @Test
    void createCompositeProduct1() {
        ProductAggregate composite = new ProductAggregate(1, "name", 1, null, null, null);
        postAndVerifyProduct(composite, ACCEPTED);

        final List<String> productMessages = getMessages("products");
        final List<String> recommendationMessages = getMessages("recommendations");
        final List<String> reviewMessages = getMessages("reviews");

        assertThat(productMessages.size()).isEqualTo(1);

        Event<Integer, Product> expectedEvent = new Event(CREATE, composite.productId(),
            new Product(composite.productId(), composite.name(), composite.weight(), null));

        org.hamcrest.MatcherAssert.assertThat(productMessages.get(0),
            is(sameEventExceptCreatedAt(expectedEvent)));

        assertThat(recommendationMessages.size()).isEqualTo(0);
        assertThat(reviewMessages.size()).isEqualTo(0);
    }

    @Test
    void createCompositeProduct2() {
        ProductAggregate composite = new ProductAggregate(1, "name", 1,
            List.of(new RecommendationSummary(1, "a", 1, "c")),
            List.of(new ReviewSummary(1, "a", "s", "c")), null);
        postAndVerifyProduct(composite, ACCEPTED);

        final List<String> productMessages = getMessages("products");
        final List<String> recommendationMessages = getMessages("recommendations");
        final List<String> reviewMessages = getMessages("reviews");

        assertThat(productMessages.size()).isEqualTo(1);

        Event<Integer, Product> expectedEvent = new Event(CREATE, composite.productId(),
            new Product(composite.productId(), composite.name(), composite.weight(), null));

        org.hamcrest.MatcherAssert.assertThat(productMessages.get(0),
            is(sameEventExceptCreatedAt(expectedEvent)));

        assertThat(recommendationMessages.size()).isEqualTo(1);

        RecommendationSummary rec = composite.recommendations().get(0);
        Event<Integer, Product> expectedRecommendationEvent =
            new Event(CREATE, composite.productId(),
                new Recommendation(composite.productId(), rec.getRecommendationId(),
                    rec.getAuthor(), rec.getRate(), rec.getContent(), null));
        org.hamcrest.MatcherAssert.assertThat(recommendationMessages.get(0),
            is(sameEventExceptCreatedAt(expectedRecommendationEvent)));

        assertThat(reviewMessages.size()).isEqualTo(1);

        ReviewSummary rev = composite.reviews().get(0);
        Event<Integer, Product> expectedReviewEvent =
            new Event(CREATE, composite.productId(),
                new Review(composite.productId(), rev.getReviewId(), rev.getAuthor(),
                    rev.getSubject(), rev.getContent(), null));
        org.hamcrest.MatcherAssert.assertThat(reviewMessages.get(0),
            is(sameEventExceptCreatedAt(expectedReviewEvent)));
    }

    @Test
    void deleteCompositeProduct() {
        deleteAndVerifyProduct(1, ACCEPTED);

        final List<String> productMessages = getMessages("products");
        final List<String> recommendationMessages = getMessages("recommendations");
        final List<String> reviewMessages = getMessages("reviews");

        // Assert one delete product event queued up
        assertThat(productMessages.size()).isEqualTo(1);

        Event<Integer, Product> expectedProductEvent = new Event(DELETE, 1, null);
        org.hamcrest.MatcherAssert.assertThat(productMessages.get(0),
            is(sameEventExceptCreatedAt(expectedProductEvent)));

        // Assert one delete recommendation event queued up
        assertThat(recommendationMessages.size()).isEqualTo(1);

        Event<Integer, Product> expectedRecommendationEvent = new Event(DELETE, 1, null);
        org.hamcrest.MatcherAssert.assertThat(recommendationMessages.get(0),
            is(sameEventExceptCreatedAt(expectedRecommendationEvent)));

        // Assert one delete review event queued up
        assertThat(reviewMessages.size()).isEqualTo(1);

        Event<Integer, Product> expectedReviewEvent = new Event(DELETE, 1, null);
        org.hamcrest.MatcherAssert.assertThat(reviewMessages.get(0),
            is(sameEventExceptCreatedAt(expectedReviewEvent)));
    }

    private void purgeMessage(String bindingName) {
        getMessages(bindingName);
    }

    private List<String> getMessages(String bindingName) {
        List<String> messages = new ArrayList<>();
        boolean anyMoreMessages = true;

        while (anyMoreMessages) {
            Message<byte[]> message = getMessage(bindingName);

            if (message == null) {
                anyMoreMessages = false;
            } else {
                messages.add(new String(message.getPayload()));
            }
        }
        return messages;
    }

    private Message<byte[]> getMessage(String bindingName) {
        try {
            return target.receive(0, bindingName);
        } catch (NullPointerException npe) {
            log.error("getMessage() received a NPE with binding = {}", bindingName);
            return null;
        }
    }

    private void postAndVerifyProduct(ProductAggregate compositeProduct,
        HttpStatus expectedStatus) {
        client.post()
            .uri("/product-composite")
            .body(Mono.just(compositeProduct), ProductAggregate.class)
            .exchange()
            .expectStatus().isEqualTo(expectedStatus);
    }

    private void deleteAndVerifyProduct(int productId, HttpStatus expectedStatus) {
        client.delete()
            .uri("/product-composite/" + productId)
            .exchange()
            .expectStatus().isEqualTo(expectedStatus);
    }
}
