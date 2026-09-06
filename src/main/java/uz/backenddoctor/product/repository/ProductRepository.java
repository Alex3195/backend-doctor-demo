package uz.backenddoctor.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.backenddoctor.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Fix #005 -- atomic, conditional decrement. Postgres evaluates
     * "stock >= :qty" against the row's CURRENT committed value at
     * UPDATE time, and the assignment is relative ("stock - :qty"),
     * not a blind overwrite of a value read earlier in Java -- so two
     * concurrent callers can't both succeed against the same units of
     * stock. Returns the number of rows updated: 1 if there was enough
     * stock, 0 if not.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);
}
