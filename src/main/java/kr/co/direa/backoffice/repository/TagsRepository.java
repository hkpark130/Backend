package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Tags;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagsRepository extends JpaRepository<Tags, Long> {
}
