package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.DepartmentDto;
import kr.co.direa.backoffice.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/departments")
public class DepartmentsController {
    private final DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<DepartmentDto> create(@RequestBody DepartmentDto dto) {
        return ResponseEntity.ok(departmentService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDto> update(@PathVariable Long id, @RequestBody DepartmentDto dto) {
        return ResponseEntity.ok(departmentService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            departmentService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("연관된 데이터가 있어 삭제할 수 없습니다.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
