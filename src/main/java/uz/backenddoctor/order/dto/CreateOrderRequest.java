package uz.backenddoctor.order.dto;

public record CreateOrderRequest(
        Long customerId,
        Long productId,
        Integer quantity
) {
}
