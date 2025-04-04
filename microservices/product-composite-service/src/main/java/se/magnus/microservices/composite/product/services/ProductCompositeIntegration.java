package se.magnus.microservices.composite.product.services;

import static java.util.logging.Level.*;
import static org.springframework.http.HttpStatus.*;
import static reactor.core.publisher.Flux.*;
import static se.magnus.api.event.Event.Type.*;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
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

@Component
@Slf4j
public class ProductCompositeIntegration implements ProductService, RecommendationService,
	ReviewService {

	private final WebClient webClient;
	private final ObjectMapper mapper;

	private final String productServiceUrl;
	private final String recommendationServiceUrl;
	private final String reviewServiceUrl;

	private final StreamBridge streamBridge;

	private final Scheduler publishEventScheduler;

	@Autowired
	public ProductCompositeIntegration(
		@Qualifier("publishEventScheduler") Scheduler publishEventScheduler,

		WebClient.Builder webClient,
		ObjectMapper mapper,
		StreamBridge streamBridge,

		@Value("${app.product-service.host}") String productServiceHost,
		@Value("${app.product-service.port}") int productServicePort,

		@Value("${app.recommendation-service.host}") String recommendationServiceHost,
		@Value("${app.recommendation-service.port}") int recommendationServicePort,

		@Value("${app.review-service.host}") String reviewServiceHost,
		@Value("${app.review-service.port}") int reviewServicePort
	) {

		this.publishEventScheduler = publishEventScheduler;
		this.webClient = webClient.build();
		this.mapper = mapper;
		this.streamBridge = streamBridge;

		productServiceUrl = "http://" + productServiceHost + ":" + productServicePort;
		recommendationServiceUrl =
			"http://" + recommendationServiceHost + ":" + recommendationServicePort;
		reviewServiceUrl = "http://" + reviewServiceHost + ":" + reviewServicePort;
	}

	@Override
	public Mono<Product> getProduct(int productId) {
		String url = productServiceUrl + "/product/" + productId;
		log.debug("Will call the getProduct API on URL: {}", url);

		return webClient.get().uri(url).retrieve().bodyToMono(Product.class)
			.log(log.getName(), FINE)
			.onErrorMap(WebClientException.class, ex -> handleException(ex));
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
		String url = recommendationServiceUrl + "/recommendation?productId=" + productId;

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
		String url = reviewServiceUrl + "/review?productId=" + productId;

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
		return getHealth(productServiceUrl);
	}

	public Mono<Health> getRecommendationHealth() {
		return getHealth(recommendationServiceUrl);
	}

	public Mono<Health> getReviewHealth() {
		return getHealth(reviewServiceUrl);
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
