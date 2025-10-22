package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.dto.ProjectDto;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectsRepository projectsRepository;

    public List<ProjectDto> findAll() {
        return projectsRepository.findAll()
                .stream()
                .map(ProjectDto::new)
                .toList();
    }

    public ProjectDto findByCode(String code) {
        var proj = projectsRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + code));
        return new ProjectDto(proj);
    }

    @Transactional
    public ProjectDto create(ProjectDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("Project code is required");
        }
        var entity = kr.co.direa.backoffice.domain.Projects.builder()
                .name(dto.getName())
                .code(dto.getCode())
                .build();
        var saved = projectsRepository.save(entity);
        return new ProjectDto(saved);
    }

    @Transactional
    public ProjectDto update(Long id, ProjectDto dto) {
        var entity = projectsRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Project not found: id=" + id));
        if (dto.getName() != null) entity = kr.co.direa.backoffice.domain.Projects.builder()
                .id(entity.getId())
                .name(dto.getName())
                .code(dto.getCode() != null ? dto.getCode() : entity.getCode())
                .build();
        else if (dto.getCode() != null) entity = kr.co.direa.backoffice.domain.Projects.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(dto.getCode())
                .build();
        // JPA 엔티티 교체 대신 편의상 빌더로 새로 생성 후 save (간단한 테이블이므로 충분)
        var saved = projectsRepository.save(entity);
        return new ProjectDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!projectsRepository.existsById(id)) {
            throw new NoSuchElementException("Project not found: id=" + id);
        }
        projectsRepository.deleteById(id);
    }
}
