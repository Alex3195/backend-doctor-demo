package uz.backenddoctor.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fills the database with realistic-shaped fake data on startup, using
 * plain batched JDBC inserts (fast, and avoids loading the whole dataset
 * into the Hibernate persistence context).
 *
 * Volumes are controlled by application.yml (seed.*) so you can start
 * small locally (thousands of rows) and scale up to millions later when
 * you want production-like benchmark numbers for the audit report.
 *
 * Safe to re-run: it only seeds when the customers table is empty.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DataSeedProperties.class)
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final DataSeedProperties properties;

    @Override
    public void run(String... args) {
        if (!properties.isEnabled()) {
            log.info("Data seeding disabled (seed.enabled=false)");
            return;
        }

        Integer existingCustomers = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers", Integer.class);
        if (existingCustomers != null && existingCustomers > 0) {
            log.info("Database already has {} customers, skipping seed.", existingCustomers);
            return;
        }

        Faker faker = new Faker();

        log.info("Seeding {} customers...", properties.getCustomers());
        seedCustomers(faker, properties.getCustomers());

        log.info("Seeding {} products...", properties.getProducts());
        seedProducts(faker, properties.getProducts());

        log.info("Seeding {} orders (with items and payments)...", properties.getOrders());
        seedOrders(properties.getOrders(), properties.getCustomers(), properties.getProducts(),
                properties.getMaxItemsPerOrder());

        log.info("Seeding complete.");
    }

    @Transactional
    public void seedCustomers(Faker faker, int count) {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= count; i++) {
            batch.add(new Object[]{
                    faker.name().fullName(),
                    faker.internet().emailAddress() + "." + i // keep unique
            });
            if (batch.size() == BATCH_SIZE || i == count) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO customers (full_name, email) VALUES (?, ?)", batch);
                batch.clear();
            }
        }
    }

    @Transactional
    public void seedProducts(Faker faker, int count) {
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 1; i <= count; i++) {
            batch.add(new Object[]{
                    faker.commerce().productName(),
                    BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(5, 500))
                            .setScale(2, java.math.RoundingMode.HALF_UP),
                    ThreadLocalRandom.current().nextInt(0, 1000)
            });
            if (batch.size() == BATCH_SIZE || i == count) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO products (name, price, stock) VALUES (?, ?, ?)", batch);
                batch.clear();
            }
        }
    }

    @Transactional
    public void seedOrders(int orderCount, int customerCount, int productCount, int maxItemsPerOrder) {
        List<Object[]> orderBatch = new ArrayList<>(BATCH_SIZE);

        for (int i = 1; i <= orderCount; i++) {
            long customerId = ThreadLocalRandom.current().nextLong(1, customerCount + 1);
            BigDecimal total = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(10, 2000))
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            orderBatch.add(new Object[]{customerId, "CREATED", total});

            if (orderBatch.size() == BATCH_SIZE || i == orderCount) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO orders (customer_id, status, total_amount) VALUES (?, ?, ?)",
                        orderBatch);
                orderBatch.clear();
            }
        }

        // Order items and payments are seeded separately, keyed off however
        // many orders now exist. Kept intentionally simple for Phase 1 --
        // this is a demo dataset, not a referential-integrity showcase.
        seedOrderItemsAndPayments(orderCount, productCount, maxItemsPerOrder);
    }

    private void seedOrderItemsAndPayments(int orderCount, int productCount, int maxItemsPerOrder) {
        List<Object[]> itemBatch = new ArrayList<>(BATCH_SIZE);
        List<Object[]> paymentBatch = new ArrayList<>(BATCH_SIZE);

        for (long orderId = 1; orderId <= orderCount; orderId++) {
            int itemsForThisOrder = ThreadLocalRandom.current().nextInt(1, maxItemsPerOrder + 1);
            for (int j = 0; j < itemsForThisOrder; j++) {
                long productId = ThreadLocalRandom.current().nextLong(1, productCount + 1);
                int quantity = ThreadLocalRandom.current().nextInt(1, 4);
                BigDecimal unitPrice = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(5, 300))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                itemBatch.add(new Object[]{orderId, productId, quantity, unitPrice});
            }

            paymentBatch.add(new Object[]{
                    orderId,
                    BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(10, 2000))
                            .setScale(2, java.math.RoundingMode.HALF_UP),
                    "PENDING"
            });

            if (itemBatch.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                        itemBatch);
                itemBatch.clear();
            }
            if (paymentBatch.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO payments (order_id, amount, status) VALUES (?, ?, ?)",
                        paymentBatch);
                paymentBatch.clear();
            }
        }

        if (!itemBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                    itemBatch);
        }
        if (!paymentBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO payments (order_id, amount, status) VALUES (?, ?, ?)",
                    paymentBatch);
        }
    }
}
