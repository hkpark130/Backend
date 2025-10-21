package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.dto.CategoryDto;
import kr.co.direa.backoffice.repository.CategoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoriesRepository categoriesRepository;

    public List<CategoryDto> findAll() {
        return categoriesRepository.findAll()
                .stream()
                .map(CategoryDto::new)
                .toList();
    }
}
