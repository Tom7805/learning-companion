package com.learningcompanion.library.api.dto;

import com.learningcompanion.library.application.TagService;
import com.learningcompanion.library.domain.Tag;
import java.util.UUID;

public record TagResponse(UUID id, String name, String color, long documentCount) {

    public static TagResponse of(Tag tag, long documentCount) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getColor().name(), documentCount);
    }

    public static TagResponse of(TagService.Counted counted) {
        return of(counted.tag(), counted.documentCount());
    }
}
