package uz.backenddoctor.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.backenddoctor.customer.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
