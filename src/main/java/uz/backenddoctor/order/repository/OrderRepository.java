package uz.backenddoctor.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.backenddoctor.order.dto.OrderSummary;
import uz.backenddoctor.order.entity.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * ISSUE #003 -- OFFSET PAGINATION (intentional, this is the "before" state).
     *
     * Spring Data translates Pageable into "ORDER BY o.id DESC OFFSET ? LIMIT ?".
     * Postgres has to walk (and discard) every row up to the offset before it
     * can return a page, so cost grows with how deep the page is -- fine for
     * page 1, increasingly expensive for the last page of a large table.
     */
    @Query(value = "SELECT new uz.backenddoctor.order.dto.OrderSummary(o.id, c.fullName, o.status, o.totalAmount, o.createdAt) " +
            "FROM Order o JOIN o.customer c ORDER BY o.id DESC",
            countQuery = "SELECT count(o) FROM Order o")
    Page<OrderSummary> findAllOrderSummariesOffset(Pageable pageable);

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
