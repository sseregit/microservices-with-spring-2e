package se.magnus.api.core.recommendation;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecommendationService {

    @GetMapping(value = "/recommendation", produces = "application/json")
    Flux<Recommendation> getRecommendations(@RequestParam("productId") int productId);

    @PostMapping(value = "/recommendation", consumes = "application/json", produces = "application/json")
    Mono<Recommendation> createRecommendation(@RequestBody Recommendation body);

    @DeleteMapping("/recommendation")
    Mono<Void> deleteRecommendations(
        @RequestParam(value = "productId", required = true) int productId);
}
