package se.magnus.api.core.review;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReviewService {

    @GetMapping(value = "/review", produces = "application/json")
    Flux<Review> getReviews(@RequestParam("productId") int productId);

    @PostMapping(value = "/review", consumes = "application/json", produces = "application/json")
    Mono<Review> createReview(@RequestBody Review body);

    @DeleteMapping("/review")
    Mono<Void> deleteReviews(@RequestParam(value = "productId", required = true) int productId);
}
