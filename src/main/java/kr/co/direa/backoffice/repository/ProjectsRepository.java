package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Projects;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectsRepository extends JpaRepository<Projects, Long> {

    Projects findByName(String name);

    Optional<Projects> findByCode(String code);

    List<Projects> findAllByOrderByCodeAsc();
}
