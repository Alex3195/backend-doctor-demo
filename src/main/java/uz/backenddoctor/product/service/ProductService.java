package uz.backenddoctor.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.backenddoctor.product.dto.ProductSummary;
import uz.backenddoctor.product.entity.Product;
import uz.backenddoctor.product.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * ISSUE #006 -- NO CACHING FOR A HOT READ PATH (intentional, this is
     * the "before" state).
     *
     * A product detail lookup is a classic hot path -- the same handful
     * of popular product IDs get requested over and over -- yet every
     * single call goes straight to Postgres. Fine at low traffic, wasteful
     * (and eventually a bottleneck) at real read volume.
     */
    public ProductSummary getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + id));
        return new ProductSummary(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
