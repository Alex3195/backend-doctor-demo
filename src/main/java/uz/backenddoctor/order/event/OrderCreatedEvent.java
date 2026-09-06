package uz.backenddoctor.order.event;

import java.math.BigDecimal;

public record OrderCreatedEvent(
        Long orderId,
        Long customerId,
        BigDecimal totalAmount
) {
}
