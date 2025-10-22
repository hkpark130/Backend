package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.ProjectDto;
import kr.co.direa.backoffice.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

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
    public ResponseEntity<ProjectDto> create(@RequestBody ProjectDto dto) {
        return ResponseEntity.ok(projectService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectDto> update(@PathVariable Long id, @RequestBody ProjectDto dto) {
        return ResponseEntity.ok(projectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            projectService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("연관된 데이터가 있어 삭제할 수 없습니다.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
