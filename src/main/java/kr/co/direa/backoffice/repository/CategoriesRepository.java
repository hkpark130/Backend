package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Categories;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriesRepository extends JpaRepository<Categories, Long> {

    Categories findByName(String name);
}
