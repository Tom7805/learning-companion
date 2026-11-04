package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.TagRequest;
import com.learningcompanion.library.api.dto.TagResponse;
import com.learningcompanion.library.application.TagService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Nhãn có màu của kho tài liệu (NCL-05-CN-001). */
@RestController
@RequestMapping(ApiPaths.LIBRARY + "/tags")
public class TagController {

    private final TagService tags;

    public TagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    public List<TagResponse> list() {
        return tags.list().stream().map(TagResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse create(@Valid @RequestBody TagRequest body) {
        return TagResponse.of(tags.create(body.name(), body.color()), 0);
    }

    @PatchMapping("/{id}")
    public TagResponse update(@PathVariable UUID id, @Valid @RequestBody TagRequest body, HttpServletRequest request) {
        return TagResponse.of(tags.update(id, body.name(), body.color(), ClientInfo.from(request)), 0);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, HttpServletRequest request) {
        tags.delete(id, ClientInfo.from(request));
    }
}
