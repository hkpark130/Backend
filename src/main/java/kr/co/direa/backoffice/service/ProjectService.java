package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.dto.ProjectDto;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
