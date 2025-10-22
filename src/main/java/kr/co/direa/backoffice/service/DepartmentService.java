package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.dto.DepartmentDto;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentsRepository departmentsRepository;

    public List<DepartmentDto> findAll() {
        return departmentsRepository.findAll()
                .stream()
                .map(DepartmentDto::new)
                .toList();
    }

    @Transactional
    public DepartmentDto create(DepartmentDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Department name is required");
        }
        departmentsRepository.findByName(dto.getName()).ifPresent(d -> {
            throw new IllegalArgumentException("Duplicate department name: " + dto.getName());
        });
        var entity = kr.co.direa.backoffice.domain.Departments.builder()
                .name(dto.getName())
                .build();
        var saved = departmentsRepository.save(entity);
        return new DepartmentDto(saved);
    }

    @Transactional
    public DepartmentDto update(Long id, DepartmentDto dto) {
        var entity = departmentsRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Department not found: id=" + id));
        if (dto.getName() != null) {
            Long currentId = entity.getId();
            departmentsRepository.findByName(dto.getName()).ifPresent(existing -> {
                if (!existing.getId().equals(currentId)) {
                    throw new IllegalArgumentException("Duplicate department name: " + dto.getName());
                }
            });
            entity = kr.co.direa.backoffice.domain.Departments.builder()
                    .id(entity.getId())
                    .name(dto.getName())
                    .build();
        }
        var saved = departmentsRepository.save(entity);
        return new DepartmentDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!departmentsRepository.existsById(id)) {
            throw new NoSuchElementException("Department not found: id=" + id);
        }
        departmentsRepository.deleteById(id);
    }
}
