package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.CategoryDto;
import kr.co.direa.backoffice.dto.DepartmentDto;
import kr.co.direa.backoffice.dto.ProjectDto;
import kr.co.direa.backoffice.service.CategoryService;
import kr.co.direa.backoffice.service.DepartmentService;
import kr.co.direa.backoffice.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MetaController {
    private final CategoryService categoryService;
    private final DepartmentService departmentService;
    private final ProjectService projectService;

    @GetMapping("/categories")
    // TODO 인증 연동 시: 로그인 사용자에게만 코드성 데이터 공개 예정 (공개 범위 재검토 필요)
    public ResponseEntity<List<CategoryDto>> categories() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @GetMapping("/departments")
    // TODO 인증 연동 시: 사내 사용자에게만 부서 목록 제공하도록 제한 필요
    public ResponseEntity<List<DepartmentDto>> departments() {
        return ResponseEntity.ok(departmentService.findAll());
    }

    @GetMapping("/projects")
    // TODO 인증 연동 시: 프로젝트 정보 역시 내부 사용자 전용으로 보호 필요
    public ResponseEntity<List<ProjectDto>> projects() {
        return ResponseEntity.ok(projectService.findAll());
    }
}
