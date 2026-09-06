package uz.backenddoctor.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.backenddoctor.order.entity.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
