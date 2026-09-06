package uz.backenddoctor.product.dto;

import java.math.BigDecimal;

public record ProductSummary(
        Long id,
        String name,
        BigDecimal price,
        Integer stock
) {
}
