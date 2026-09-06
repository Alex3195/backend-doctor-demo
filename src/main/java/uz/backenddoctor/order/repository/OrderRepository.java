package uz.backenddoctor.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.backenddoctor.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Deliberately just findAll() for now -- Phase 2 will add:
    //   - a JOIN FETCH version
    //   - an @EntityGraph version
    //   - a DTO projection version
    // so you can benchmark all three against this baseline.
}
