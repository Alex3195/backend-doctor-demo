package uz.backenddoctor.product.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uz.backenddoctor.product.dto.ProductSummary;
import uz.backenddoctor.product.service.ProductService;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * ISSUE #006 -- deliberately uncached hot read path.
     * See ProductService.getProductById().
     */
    @GetMapping("/api/products/{id}")
    public ProductSummary getProduct(@PathVariable Long id) {
        return productService.getProductById(id);
    }
}
