package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.ProjectDto;
import kr.co.direa.backoffice.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectsController {
    private final ProjectService projectService;

    @GetMapping("/code/{code}")
    public ResponseEntity<ProjectDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(projectService.findByCode(code));
    }

    @PostMapping
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<ProjectDto> create(@RequestBody ProjectDto dto) {
        return ResponseEntity.ok(projectService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<ProjectDto> update(@PathVariable Long id, @RequestBody ProjectDto dto) {
        return ResponseEntity.ok(projectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
