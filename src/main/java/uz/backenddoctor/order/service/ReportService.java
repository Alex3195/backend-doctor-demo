package uz.backenddoctor.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.backenddoctor.order.dto.DailyRevenue;
import uz.backenddoctor.order.dto.RevenueByStatus;
import uz.backenddoctor.order.entity.Order;
import uz.backenddoctor.order.repository.OrderRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;

    /**
     * ISSUE #009 -- LOADING A FULL RESULT SET INTO MEMORY (intentional,
     * this is the "before" state).
     *
     * findAll() materializes every single Order row in the table as a
     * full JPA entity, all at once, in the JVM heap -- just to add up a
     * BigDecimal and count rows per status. The database already knows
     * how to do this (GROUP BY / SUM) without ever sending the rows to
     * the application at all.
     */
    public List<RevenueByStatus> getRevenueByStatusInMemory() {
        List<Order> allOrders = orderRepository.findAll();

        Map<uz.backenddoctor.order.entity.OrderStatus, List<Order>> byStatus =
                allOrders.stream().collect(Collectors.groupingBy(Order::getStatus));

        return byStatus.entrySet().stream()
                .map(entry -> new RevenueByStatus(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(Order::getTotalAmount)
                                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(RevenueByStatus::status))
                .toList();
    }

    /**
     * Fix #009 -- database-side aggregation. See
     * OrderRepository.findRevenueByStatus().
     */
    public List<RevenueByStatus> getRevenueByStatusAggregated() {
        return orderRepository.findRevenueByStatus();
    }

    /**
     * ISSUE #010 -- see OrderRepository.findDailyRevenueSince().
     */
    public List<DailyRevenue> getDailyRevenue(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return orderRepository.findDailyRevenueSince(since).stream()
                .map(p -> new DailyRevenue(
                        p.getDay().toLocalDateTime().toLocalDate(),
                        p.getTotal(),
                        p.getCnt()
                ))
                .toList();
    }
}
