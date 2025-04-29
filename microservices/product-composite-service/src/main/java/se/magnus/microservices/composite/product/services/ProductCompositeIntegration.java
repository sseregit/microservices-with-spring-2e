package se.magnus.microservices.composite.product.services;

import static java.util.logging.Level.FINE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static reactor.core.publisher.Flux.empty;
import static se.magnus.api.event.Event.Type.CREATE;
import static se.magnus.api.event.Event.Type.DELETE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.io.IOException;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.product.ProductService;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.recommendation.RecommendationService;
import se.magnus.api.core.review.Review;
import se.magnus.api.core.review.ReviewService;
import se.magnus.api.event.Event;
import se.magnus.api.exceptions.InvalidInputException;
import se.magnus.api.exceptions.NotFoundException;
import se.magnus.util.http.HttpErrorInfo;
import se.magnus.util.http.ServiceUtil;

@Component
@Slf4j
public class ProductCompositeIntegration implements ProductService, RecommendationService,
    ReviewService {

    private static final String PRODUCT_SERVICE_URL = "http://product";
    private static final String RECOMMENDATION_SERVICE_URL = "http://recommendation";
    private static final String REVIEW_SERVICE_URL = "http://review";
    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final StreamBridge streamBridge;

    private final Scheduler publishEventScheduler;
    private final ServiceUtil serviceUtil;

    @Autowired
    public ProductCompositeIntegration(
        @Qualifier("publishEventScheduler") Scheduler publishEventScheduler,

        WebClient.Builder webClient,
        ObjectMapper mapper,
        StreamBridge streamBridge,
        ServiceUtil serviceUtil) {

        this.publishEventScheduler = publishEventScheduler;
        this.webClient = webClient.build();
        this.mapper = mapper;
        this.streamBridge = streamBridge;
        this.serviceUtil = serviceUtil;
    }

    @Retry(name = "product")
    @TimeLimiter(name = "product")
    @CircuitBreaker(name = "product", fallbackMethod = "getProductFallbackValue")
    @Override
    public Mono<Product> getProduct(int productId, int delay, int faultPercent) {
        URI url = UriComponentsBuilder.fromUriString(
                PRODUCT_SERVICE_URL + "/product/{productId}?delay={delay}&faultPercent={faultPercent}")
            .build(productId, delay, faultPercent);
        log.debug("Will call the getProduct API on URL: {}", url);

        return webClient.get().uri(url).retrieve().bodyToMono(Product.class)
            .log(log.getName(), FINE)
            .onErrorMap(WebClientException.class, ex -> handleException(ex));
    }

    public Mono<Product> getProductFallbackValue(int productId, int delay, int faultPercent,
        CallNotPermittedException ex) {

        if (productId == 13) {
            String errMsg = """ 
                Product Id: %s not found in fallback cache!
                """.formatted(productId);
            throw new NotFoundException(errMsg);
        }

        return Mono.just(new Product(productId, "Fallback product" + productId, productId,
            serviceUtil.getServiceAddress()));
    }


    private Throwable handleException(Throwable ex) {

        if (!(ex instanceof WebClientResponseException wcre)) {
            log.warn("Got a unexpected error: {}, will rethrow it", ex.toString());
            return ex;
        }

        return switch (wcre.getStatusCode()) {
            case NOT_FOUND -> new NotFoundException(getErrorMessage(wcre));
            case UNPROCESSABLE_ENTITY -> new InvalidInputException(getErrorMessage(wcre));
            default -> {
                log.warn("Got an unexpected HTTP error: {}, will rethrow it", wcre.getStatusCode());
                log.warn("Error body: {}", wcre.getResponseBodyAsString());
                yield ex;
            }
        };
    }

    @Override
    public Mono<Product> createProduct(Product body) {
        return Mono.fromCallable(() -> {
            sendMessage("products-out-0", new Event<>(CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        return Mono.fromRunnable(() -> {
            sendMessage("products-out-0", new Event(DELETE, productId, null));
        }).subscribeOn(publishEventScheduler).then();
    }

    private String getErrorMessage(WebClientResponseException ex) {
        try {
            return mapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException ioex) {
            return ex.getMessage();
        }
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {
        String url = RECOMMENDATION_SERVICE_URL + "/recommendation?productId=" + productId;

        log.debug("Will call the getRecommendations API on URL: {}", url);

        return webClient.get().uri(url).retrieve().bodyToFlux(Recommendation.class)
            .log(log.getName(), FINE).onErrorResume(error -> empty());
    }

    @Override
    public Mono<Recommendation> createRecommendation(Recommendation body) {
        return Mono.fromCallable(() -> {
            sendMessage("recommendations-out-0",
                new Event(CREATE, body.getRecommendationId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteRecommendations(int productId) {
        return Mono.fromRunnable(() -> {
            sendMessage("recommendations-out-0", new Event(DELETE, productId, null));
        }).subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Flux<Review> getReviews(int productId) {
        String url = REVIEW_SERVICE_URL + "/review?productId=" + productId;

        log.debug("Will call the getReviews API on URL: {}", url);

        return webClient.get().uri(url).retrieve().bodyToFlux(Review.class)
            .log(log.getName(), FINE).onErrorResume(error -> empty());

    }

    @Override
    public Mono<Review> createReview(Review body) {
        return Mono.fromCallable(() -> {
            sendMessage("reviews-out-0", new Event(CREATE, body.getReviewId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteReviews(int productId) {
        return Mono.fromRunnable(() -> {
            sendMessage("reviews-out-0", new Event(DELETE, productId, null));
        }).subscribeOn(publishEventScheduler).then();
    }

    public Mono<Health> getProductHealth() {
        return getHealth(PRODUCT_SERVICE_URL);
    }

    public Mono<Health> getRecommendationHealth() {
        return getHealth(RECOMMENDATION_SERVICE_URL);
    }

    public Mono<Health> getReviewHealth() {
        return getHealth(REVIEW_SERVICE_URL);
    }

    private Mono<Health> getHealth(String url) {
        url += "/actuator/health";
        log.debug("Will call the Health API on URL: {}", url);
        return webClient.get().uri(url).retrieve().bodyToMono(String.class)
            .map(s -> new Health.Builder().up().build())
            .onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
            .log(log.getName(), FINE);
    }

    private HttpClientErrorException getHttpClientErrorException(HttpClientErrorException ex) {
        log.warn("Got an unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
        log.warn("Error body: {}", ex.getResponseBodyAsString());
        return ex;
    }

    private void sendMessage(String bindingName, Event event) {
        log.debug("Sending a {} message to {}", event.getEventType(), bindingName);
        Message message = MessageBuilder.withPayload(event)
            .setHeader("partitionKey", event.getKey())
            .build();
        streamBridge.send(bindingName, message);
    }

}
