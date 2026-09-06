package uz.backenddoctor.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import uz.backenddoctor.order.dto.OrderSummary;
import uz.backenddoctor.order.entity.Order;
import uz.backenddoctor.order.repository.OrderRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * ISSUE #001 -- N+1 QUERY (intentional, this is the "before" state).
     *
     * findAll() issues exactly one SELECT for orders. But because
     * Order.customer is FetchType.LAZY, calling order.getCustomer().getFullName()
     * for each order triggers a SEPARATE "SELECT * FROM customers WHERE id = ?"
     * per order.
     *
     * For 20,000 orders spread across ~5,000 customers, expect roughly
     * 1 + N queries where N is the number of distinct customers actually
     * touched in this page/result set -- watch the console with
     * show-sql: true and hibernate.generate_statistics: true.
     *
     * Audit note: this is exactly the OrderService.java:87-style issue
     * from the original audit template. Do not "fix" this method directly --
     * add findAllOrdersOptimized() alongside it (JOIN FETCH / @EntityGraph /
     * DTO projection) so you can benchmark before vs. after and keep both
     * for the case study.
     */
    public List<OrderSummary> findAllOrdersUnoptimized() {
        List<Order> orders = orderRepository.findAll();

        return orders.stream()
                .map(order -> new OrderSummary(
                        order.getId(),
                        order.getCustomer().getFullName(), // <-- triggers the extra SELECT
                        order.getStatus(),
                        order.getTotalAmount(),
                        order.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Fix #1 -- JOIN FETCH. One SELECT total: the join pulls customer
     * columns into the same result set, so accessing getCustomer() below
     * never triggers a second query.
     */
    public List<OrderSummary> findAllOrdersJoinFetch() {
        return orderRepository.findAllWithCustomerJoinFetch().stream()
                .map(order -> new OrderSummary(
                        order.getId(),
                        order.getCustomer().getFullName(),
                        order.getStatus(),
                        order.getTotalAmount(),
                        order.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Fix #2 -- @EntityGraph. Same one-SELECT result as JOIN FETCH, just
     * declared via annotation instead of a hand-written join.
     */
    public List<OrderSummary> findAllOrdersEntityGraph() {
        return orderRepository.findAllWithCustomerEntityGraph().stream()
                .map(order -> new OrderSummary(
                        order.getId(),
                        order.getCustomer().getFullName(),
                        order.getStatus(),
                        order.getTotalAmount(),
                        order.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Fix #3 -- DTO projection. One SELECT, and it's the cheapest of the
     * three: only the columns the API returns are read, no full Order or
     * Customer entities are materialized.
     */
    public List<OrderSummary> findAllOrdersDtoProjection() {
        return orderRepository.findAllOrderSummaries();
    }

    /**
     * ISSUE #003 -- OFFSET PAGINATION (intentional, this is the "before" state).
     * See OrderRepository.findAllOrderSummariesOffset() for why this gets
     * slower the deeper the page.
     */
    public Page<OrderSummary> findOrdersPageOffset(int page, int size) {
        return orderRepository.findAllOrderSummariesOffset(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    }

    /**
     * Fix #003 -- keyset pagination. See OrderRepository.findOrderSummariesKeyset().
     */
    public List<OrderSummary> findOrdersKeyset(Long cursorId, int size) {
        return orderRepository.findOrderSummariesKeyset(cursorId, PageRequest.of(0, size));
    }
}
