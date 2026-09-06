package uz.backenddoctor.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uz.backenddoctor.product.dto.ProductSummary;
import uz.backenddoctor.product.entity.Product;
import uz.backenddoctor.product.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Fix #006 -- @Cacheable. First call for a given ID hits Postgres and
     * populates Redis (see CacheConfig for TTL/serialization); every
     * subsequent call for that ID is served from Redis with zero DB
     * queries, until the entry expires.
     */
    @Cacheable(value = "products", key = "#id")
    public ProductSummary getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + id));
        return new ProductSummary(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
