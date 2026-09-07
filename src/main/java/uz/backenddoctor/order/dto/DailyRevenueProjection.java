package uz.backenddoctor.order.dto;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Spring Data interface projection for the native "GROUP BY date_trunc(...)"
 * query -- native queries can't use JPQL constructor expressions.
 */
public interface DailyRevenueProjection {
    Timestamp getDay();
    BigDecimal getTotal();
    Long getCnt();
}
