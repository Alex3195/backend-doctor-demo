package uz.backenddoctor.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.backenddoctor.order.dto.OrderSummary;
import uz.backenddoctor.order.service.OrderService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

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
}
