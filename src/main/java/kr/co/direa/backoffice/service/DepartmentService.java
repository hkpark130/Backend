package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.dto.DepartmentDto;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
