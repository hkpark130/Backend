package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Departments;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepartmentsRepository extends JpaRepository<Departments, Long> {

    Optional<Departments> findByName(String name);
}
