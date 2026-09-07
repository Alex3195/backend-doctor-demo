package uz.backenddoctor.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.backenddoctor.order.dto.DailyRevenue;
import uz.backenddoctor.order.dto.RevenueByStatus;
import uz.backenddoctor.order.service.ReportService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * ISSUE #009 -- deliberately loads the full orders table into
     * memory to compute this. See ReportService.getRevenueByStatusInMemory().
     */
    @GetMapping("/api/reports/revenue-by-status")
    public List<RevenueByStatus> getRevenueByStatus() {
        return reportService.getRevenueByStatusInMemory();
    }

    /**
     * Fix #009 -- see ReportService.getRevenueByStatusAggregated().
     */
    @GetMapping("/api/reports/revenue-by-status/aggregated")
    public List<RevenueByStatus> getRevenueByStatusAggregated() {
        return reportService.getRevenueByStatusAggregated();
    }

    /**
     * ISSUE #010 -- see OrderRepository.findDailyRevenueSince(); no index
     * on orders.created_at yet, so this forces a sequential scan of the
     * whole table.
     */
    @GetMapping("/api/reports/daily-revenue")
    public List<DailyRevenue> getDailyRevenue(@RequestParam(defaultValue = "7") int days) {
        return reportService.getDailyRevenue(days);
    }
}
