package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.dto.CategoryDto;
import kr.co.direa.backoffice.exception.CustomException;
import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import kr.co.direa.backoffice.repository.CategoriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoriesRepository categoriesRepository;
    @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public List<CategoryDto> findAll() {
        return categoriesRepository.findAll()
                .stream()
                .map(CategoryDto::new)
                .toList();
    }

    public CategoryDto create(String name, MultipartFile image) throws IOException {
        if (name == null || name.isBlank()) {
            throw new CustomException(CustomErrorCode.CATEGORY_NAME_REQUIRED);
        }

        String filenameOnly = null; // DB에는 파일명만 저장
        if (image != null && !image.isEmpty()) {
            // 업로드 디렉토리: {uploadDir}/categories
            Path base = Paths.get(uploadDir).toAbsolutePath();
            Path categoryDir = base.resolve("categories");
            Files.createDirectories(categoryDir);

            String ext = OptionalExt.getExtensionSafe(image.getOriginalFilename());
            String filename = UUID.randomUUID() + (ext.isEmpty() ? "" : ("." + ext));
            Path target = categoryDir.resolve(filename);
            Files.copy(image.getInputStream(), target);

            // 정적 제공은 /uploads/categories/{filename}로 이루어지고,
            // DB에는 파일명만 저장한다.
            filenameOnly = filename;
        }

        var saved = categoriesRepository.save(
                kr.co.direa.backoffice.domain.Categories.builder()
                        .name(name)
                        .img(filenameOnly) // null이면 프런트에서 기본 이미지로 대체
                        .build()
        );
        return new CategoryDto(saved);
    }
}

// 내부 유틸: 파일 확장자 안전 추출
class OptionalExt {
    static String getExtensionSafe(String filename) {
        if (filename == null) return "";
        int idx = filename.lastIndexOf('.')
;        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).replaceAll("[^A-Za-z0-9]", "");
    }
}
