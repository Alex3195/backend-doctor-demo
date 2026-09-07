package uz.backenddoctor.order.dto;

import uz.backenddoctor.order.entity.OrderStatus;

import java.math.BigDecimal;

public record RevenueByStatus(
        OrderStatus status,
        BigDecimal totalRevenue,
        long orderCount
) {
}
