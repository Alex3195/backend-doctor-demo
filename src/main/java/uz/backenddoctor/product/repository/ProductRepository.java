package uz.backenddoctor.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.backenddoctor.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
