package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.CategoryDto;
import kr.co.direa.backoffice.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CategoryController {
    private final CategoryService categoryService;

    @PostMapping(value = "/categories", consumes = {"multipart/form-data"})
    @PreAuthorize("@adminAuthorization.hasAdminAccess()")
    public ResponseEntity<CategoryDto> create(@RequestParam("name") String name,
                                              @RequestPart(value = "image", required = false) MultipartFile image) throws IOException {
        return ResponseEntity.ok(categoryService.create(name, image));
    }
}
