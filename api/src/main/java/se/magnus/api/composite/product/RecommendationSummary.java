package se.magnus.api.composite.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class RecommendationSummary {

    private final int recommendationId;
    private final String author;
    private final int rate;
    private final String content;
}
