package se.magnus.microservices.core.product.services;

import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.magnus.api.core.product.Product;
import se.magnus.api.core.product.ProductService;
import se.magnus.api.event.Event;
import se.magnus.api.exceptions.EventProcessingException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MessageProcessorConfig {

    private final ProductService productService;

    @Bean
    Consumer<Event<Integer, Product>> messageProcessor() {
        return event -> {
            log.debug("Process message created at {}...", event.getEventCreatedAt());

            switch (event.getEventType()) {
                case CREATE -> {
                    Product product = event.getData();
                    log.info("Create product with ID: {}", product.getProductId());
                    productService.createProduct(product).block();
                }
                case DELETE -> {
                    int productId = event.getKey();
                    log.info("Delete product with ProductID: {}", productId);
                    productService.deleteProduct(productId).block();
                }
                default -> {
                    String errorMessage = """
                        Incorrect event type: %s, expected a CREATE or DELETE event
                        """.formatted(event.getEventType());
                    throw new EventProcessingException(errorMessage);
                }
            }
            log.info("Message processing done!");
        };
    }
}
