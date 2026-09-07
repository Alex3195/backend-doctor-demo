package uz.backenddoctor.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
