package se.magnus.microservices.composite.product.services;

import java.util.List;
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
    public ProductAggregate getProduct(int producteId) {

        Product product = integration.getProduct(producteId);
        List<Recommendation> recommendations = integration.getRecommendations(producteId);
        List<Review> reviews = integration.getReviews(producteId);

        return createProductAggregate(product, recommendations, reviews,
            serviceUtil.getServiceAddress());
    }

    @Override
    public Product createProduct(ProductAggregate product) {
        return null;
    }

    @Override
    public void deleteProduct(ProductAggregate product) {

    }

    private ProductAggregate createProductAggregate(Product product,
        List<Recommendation> recommendations, List<Review> reviews, String serviceAddress) {
        int productId = product.getProductId();
        String name = product.getName();
        int weight = product.getWeight();

        List<RecommendationSummary> recommendationSummaries = recommendations == null ? null
            : recommendations.stream().map(
                    r -> new RecommendationSummary(r.getRecommendationId(), r.getAuthor(), r.getRate()))
                .toList();

        List<ReviewSummary> reviewSummaries = reviews == null ? null : reviews.stream()
            .map(r -> new ReviewSummary(r.getReviewId(), r.getAuthor(), r.getSubject())).toList();

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
