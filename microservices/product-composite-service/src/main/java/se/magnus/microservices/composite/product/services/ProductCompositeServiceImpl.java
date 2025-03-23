package se.magnus.microservices.composite.product.services;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
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

    private final ServiceUtil serviceUtil;
    private final ProductCompositeIntegration integration;
    private final RestTemplate restTemplate;

    public ProductCompositeServiceImpl(ServiceUtil serviceUtil,
        ProductCompositeIntegration integration, RestTemplate restTemplate) {
        this.serviceUtil = serviceUtil;
        this.integration = integration;
        this.restTemplate = restTemplate;
    }

    @Override
    public ProductAggregate getProduct(int productId) {

        Product product = integration.getProduct(productId);
        List<Recommendation> recommendations = integration.getRecommendations(productId);
        List<Review> reviews = integration.getReviews(productId);

        return createProductAggregate(product, recommendations, reviews,
            serviceUtil.getServiceAddress());
    }

    @Override
    public void createProduct(ProductAggregate body) {
        try {
            Product product = new Product(body.productId(), body.name(), body.weight(), null);
            integration.createProduct(product);

            if (body.recommendations() != null) {
                body.recommendations().forEach(r -> {
                    Recommendation recommendation = new Recommendation(body.productId(),
                        r.getRecommendationId(), r.getAuthor(),
                        r.getRate(), r.getContent(), null);
                    integration.createRecommendation(recommendation);
                });
            }

            if (body.reviews() != null) {
                body.reviews().forEach(r -> {
                    Review review = new Review(body.productId(), r.getReviewId(), r.getAuthor(),
                        r.getSubject(),
                        r.getContent(), null);
                    integration.createReview(review);
                });
            }
        } catch (RuntimeException re) {
            log.warn("createCompositeProduct failed", re);
            throw re;
        }


    }

    @Override
    public void deleteProduct(int productId) {
        integration.deleteProduct(productId);
        integration.deleteRecommendations(productId);
        integration.deleteReviews(productId);
    }

    private ProductAggregate createProductAggregate(Product product,
        List<Recommendation> recommendations, List<Review> reviews, String serviceAddress) {
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
}
