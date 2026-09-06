package uz.backenddoctor.order.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.backenddoctor.order.dto.OrderSummary;
import uz.backenddoctor.order.entity.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fix #1 -- JOIN FETCH: one SELECT, customer columns pulled in via the join.
    @Query("SELECT o FROM Order o JOIN FETCH o.customer")
    List<Order> findAllWithCustomerJoinFetch();

    // Fix #2 -- @EntityGraph: same result as JOIN FETCH, declared instead of hand-written.
    @EntityGraph(attributePaths = "customer")
    @Query("SELECT o FROM Order o")
    List<Order> findAllWithCustomerEntityGraph();

    // Fix #3 -- DTO projection: one SELECT, and it never loads full Order/Customer
    // entities -- only the columns the API actually returns.
    @Query("SELECT new uz.backenddoctor.order.dto.OrderSummary(o.id, c.fullName, o.status, o.totalAmount, o.createdAt) " +
            "FROM Order o JOIN o.customer c")
    List<OrderSummary> findAllOrderSummaries();
}
