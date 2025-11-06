package kr.co.direa.backoffice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.co.direa.backoffice.service.TagsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tags")
public class TagController {

    private final TagsService tagsService;

    @GetMapping
    public ResponseEntity<List<String>> getTags() {
        return ResponseEntity.ok(tagsService.findAllTagNames());
    }
}
