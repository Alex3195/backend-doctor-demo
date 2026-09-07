package uz.backenddoctor.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenue(
        LocalDate day,
        BigDecimal totalRevenue,
        long orderCount
) {
}
