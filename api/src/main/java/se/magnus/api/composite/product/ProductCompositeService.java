package se.magnus.api.composite.product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

@Tag(name = "ProductComposite", description = "REST API for composite product information.")
public interface ProductCompositeService {

    @Operation(
        summary = "${api.product-composite.get-composite-product.description}",
        description = "${api.product-composite.get-composite-product.notes}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "${api.responseCodes.ok.description}"),
        @ApiResponse(responseCode = "400", description = "${api.responseCodes.badRequest.description}"),
        @ApiResponse(responseCode = "404", description = "${api.responseCodes.notFound.description}"),
        @ApiResponse(responseCode = "422", description = "${api.responseCodes.unprocessableEntity.description}")
    })
    @GetMapping(value = "/product-composite/{productId}", produces = "application/json")
    Mono<ProductAggregate> getProduct(
        @PathVariable int productId,
        @RequestParam(value = "delay", required = false, defaultValue = "0") int delay,
        @RequestParam(value = "faultPercent", required = false, defaultValue = "0") int faultPercent
    );

    @Operation(
        summary = "${api.product-composite.create-composite-product.description}",
        description = "${api.product-composite.create-composite-product.notes}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "400", description = "${api.responseCodes.badRequest.description}"),
        @ApiResponse(responseCode = "422", description = "${api.responseCodes.unprocessableEntity.description}")
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping(value = "/product-composite", consumes = "application/json")
    Mono<Void> createProduct(@RequestBody ProductAggregate body);

    @Operation(
        summary = "${api.product-composite.delete-composite-product.description}",
        description = "${api.product-composite.delete-composite-product.notes}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "400", description = "${api.responseCodes.badRequest.description}"),
        @ApiResponse(responseCode = "422", description = "${api.responseCodes.unprocessableEntity.description}")
    })
    @ResponseStatus(HttpStatus.ACCEPTED)
    @DeleteMapping(value = "/product-composite/{productId}")
    Mono<Void> deleteProduct(@PathVariable int productId);
}
