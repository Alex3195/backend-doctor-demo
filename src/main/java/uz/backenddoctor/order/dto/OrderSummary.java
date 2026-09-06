package uz.backenddoctor.order.dto;

import uz.backenddoctor.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the API actually needs to return for a list of orders.
 * Notice it does NOT need the full Customer entity -- just the name.
 * This is the shape we'll use for the DTO-projection fix in Phase 2.
 */
public record OrderSummary(
        Long orderId,
        String customerName,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
}
