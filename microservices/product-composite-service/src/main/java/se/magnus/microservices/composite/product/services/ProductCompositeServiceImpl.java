package se.magnus.microservices.composite.product.services;

import static java.util.logging.Level.FINE;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import se.magnus.api.composite.product.ProductAggregate;
import se.magnus.api.composite.product.ProductCompositeService;
import se.magnus.api.composite.product.RecommendationSummary;
import se.magnus.api.composite.product.ReviewSummary;
import se.magnus.api.composite.product.ServiceAddresses;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.recommendation.Recommendation;
import se.magnus.api.core.review.Review;
import se.magnus.util.http.ServiceUtil;

@RestController
@Slf4j
public class ProductCompositeServiceImpl implements ProductCompositeService {

    private final SecurityContext nullSecCtx = new SecurityContextImpl();

    private final ServiceUtil serviceUtil;
    private final ProductCompositeIntegration integration;

    public ProductCompositeServiceImpl(ServiceUtil serviceUtil,
        ProductCompositeIntegration integration) {
        this.serviceUtil = serviceUtil;
        this.integration = integration;
    }

    @Override
    public Mono<ProductAggregate> getProduct(int productId) {
        log.info("Will get composite product info for product.id={}", productId);
        return Mono.zip(
                values -> createProductAggregate(
                    (SecurityContext) values[0],
                    (Product) values[1],
                    (List<Recommendation>) values[2],
                    (List<Review>) values[3],
                    serviceUtil.getServiceAddress()),
                getSecurityContextMono(),
                integration.getProduct(productId),
                integration.getRecommendations(productId).collectList(),
                integration.getReviews(productId).collectList())

            .doOnError(ex ->
                log.warn("getCompositeProduct failed: {}", ex.toString()))
            .log(log.getName(), FINE);
    }

    @Override
    public Mono<Void> createProduct(ProductAggregate body) {
        try {
            List<Mono> monoList = new ArrayList<>();

            monoList.add(getLogAuthorizationInfoMono());

            log.debug("createCompositeProduct: creates a new composite entity for productId: {}",
                body.productId());

            Product product = new Product(body.productId(), body.name(), body.weight(), null);
            monoList.add(integration.createProduct(product));

            if (body.recommendations() != null) {
                body.recommendations().forEach(r -> {
                    Recommendation recommendation = new Recommendation(body.productId(),
                        r.getRecommendationId(), r.getAuthor(),
                        r.getRate(), r.getContent(), null);
                    monoList.add(integration.createRecommendation(recommendation));
                });
            }

            if (body.reviews() != null) {
                body.reviews().forEach(r -> {
                    Review review = new Review(body.productId(), r.getReviewId(), r.getAuthor(),
                        r.getSubject(),
                        r.getContent(), null);
                    monoList.add(integration.createReview(review));
                });
            }

            log.debug("createCompositeProduct: composite entities created for productId: {}",
                body.productId());

            return Mono.zip(r -> "", monoList.toArray(new Mono[0]))
                .doOnError(ex -> log.warn("createCompositeProduct failed: {}", ex.toString()))
                .then();
        } catch (RuntimeException re) {
            log.warn("createCompositeProduct failed: {}", re.toString());
            throw re;
        }
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        try {
            log.debug("deleteCompositeProduct: Deletes a product aggregate for productId: {}",
                productId);
            return Mono.zip(
                    r -> "",
                    getLogAuthorizationInfoMono(),
                    integration.deleteProduct(productId),
                    integration.deleteRecommendations(productId),
                    integration.deleteReviews(productId))
                .doOnError(ex -> log.warn("delete failed: {}", ex.toString()))
                .log(log.getName(), FINE)
                .then();
        } catch (RuntimeException re) {
            log.warn("deleteCompositeProduct failed: {}", re.toString());
            throw re;
        }
    }

    private ProductAggregate createProductAggregate(
        SecurityContext sc,
        Product product,
        List<Recommendation> recommendations, List<Review> reviews, String serviceAddress) {

        logAuthorizationInfo(sc);

        int productId = product.getProductId();
        String name = product.getName();
        int weight = product.getWeight();

        List<RecommendationSummary> recommendationSummaries = recommendations == null ? null
            : recommendations.stream().map(
                    r -> new RecommendationSummary(r.getRecommendationId(), r.getAuthor(), r.getRate(),
                        r.getContent()))
                .toList();

        List<ReviewSummary> reviewSummaries = reviews == null ? null : reviews.stream()
            .map(r -> new ReviewSummary(r.getReviewId(), r.getAuthor(), r.getSubject(),
                r.getContent())).toList();

        String productAddress = product.getServiceAddress();
        String reviewAddress =
            (reviews != null && reviews.size() > 0) ? reviews.get(0).getServiceAddress() : "";
        String recommendationAddress =
            (recommendations != null && recommendations.size() > 0) ? recommendations.get(0)
                .getServiceAddress() : "";
        ServiceAddresses serviceAddresses = new ServiceAddresses(serviceAddress, productAddress,
            reviewAddress, recommendationAddress);

        return new ProductAggregate(productId, name, weight, recommendationSummaries,
            reviewSummaries, serviceAddresses);
    }

    private Mono<SecurityContext> getLogAuthorizationInfoMono() {
        return getSecurityContextMono().doOnNext(sc -> logAuthorizationInfo(sc));
    }

    private Mono<SecurityContext> getSecurityContextMono() {
        return ReactiveSecurityContextHolder.getContext().defaultIfEmpty(nullSecCtx);
    }

    private void logAuthorizationInfo(SecurityContext sc) {
        if (sc != null && sc.getAuthentication() != null
            && sc.getAuthentication() instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwtToken = jwtAuthenticationToken.getToken();
            logAuthorizationInfo(jwtToken);
        } else {
            log.warn("No JWT based Authentication supplied, running tests are we?");
        }
    }

    private void logAuthorizationInfo(Jwt jwt) {
        if (jwt == null) {
            log.warn("No JWT supplied, running tests are we?");
        } else {
            URL issuer = jwt.getIssuer();
            List<String> audience = jwt.getAudience();
            Object subject = jwt.getClaims().get("sub");
            Object scopes = jwt.getClaims().get("scope");
            Object expires = jwt.getClaims().get("exp");

            log.debug(
                "Authorization info: Subject: {}, scopes: {}, expires {}: issuer: {}, audience: {}",
                subject, scopes, expires, issuer, audience);
        }
    }
}
