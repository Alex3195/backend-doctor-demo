package uz.backenddoctor.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.backenddoctor.order.dto.CreateOrderRequest;
import uz.backenddoctor.order.dto.OrderSummary;
import uz.backenddoctor.order.service.OrderCreationService;
import uz.backenddoctor.order.service.OrderService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderCreationService orderCreationService;

    /**
     * Baseline endpoint -- deliberately unoptimized (see OrderService).
     * Use this to capture your "Before" benchmark numbers:
     *   - response time
     *   - SQL query count (from the console / actuator metrics)
     *
     * Phase 2 will add /api/orders/optimized next to this one so the
     * audit report can show a direct before/after comparison.
     */
    @GetMapping("/api/orders")
    public List<OrderSummary> getAllOrders() {
        return orderService.findAllOrdersUnoptimized();
    }

    /**
     * Phase 2 fix, JOIN FETCH variant -- see OrderService.findAllOrdersJoinFetch().
     */
    @GetMapping("/api/orders/optimized/join-fetch")
    public List<OrderSummary> getAllOrdersJoinFetch() {
        return orderService.findAllOrdersJoinFetch();
    }

    /**
     * Phase 2 fix, @EntityGraph variant -- see OrderService.findAllOrdersEntityGraph().
     */
    @GetMapping("/api/orders/optimized/entity-graph")
    public List<OrderSummary> getAllOrdersEntityGraph() {
        return orderService.findAllOrdersEntityGraph();
    }

    /**
     * Phase 2 fix, DTO projection variant -- see OrderService.findAllOrdersDtoProjection().
     */
    @GetMapping("/api/orders/optimized/dto-projection")
    public List<OrderSummary> getAllOrdersDtoProjection() {
        return orderService.findAllOrdersDtoProjection();
    }

    /**
     * ISSUE #003 -- deliberately unoptimized OFFSET pagination.
     * Use this to capture "Before" numbers: response time at page 0 vs.
     * a page deep into the table (see OrderService.findOrdersPageOffset()).
     */
    @GetMapping("/api/orders/page")
    public Page<OrderSummary> getOrdersPageOffset(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.findOrdersPageOffset(page, size);
    }

    /**
     * Fix #003 -- keyset pagination. Pass the last "orderId" seen as
     * "cursor" to get the next page; omit it for the first page.
     */
    @GetMapping("/api/orders/keyset")
    public List<OrderSummary> getOrdersKeyset(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.findOrdersKeyset(cursor, size);
    }

    /**
     * ISSUE #004 -- deliberately holds the DB transaction open across a
     * slow external payment call. See OrderCreationService.createOrderLongTransaction().
     */
    @PostMapping("/api/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderSummary createOrder(@RequestBody CreateOrderRequest request) {
        var order = orderCreationService.createOrderLongTransaction(request);
        return new OrderSummary(
                order.getId(),
                order.getCustomer().getFullName(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
