package kr.co.direa.backoffice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.co.direa.backoffice.domain.Tags;

public interface TagsRepository extends JpaRepository<Tags, Long> {
	Optional<Tags> findByNameIgnoreCase(String name);
}
