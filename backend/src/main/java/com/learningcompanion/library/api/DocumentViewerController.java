package com.learningcompanion.library.api;

import com.learningcompanion.library.api.dto.ViewerContentResponse;
import com.learningcompanion.library.application.ViewerService;
import com.learningcompanion.shared.web.ApiPaths;
import com.learningcompanion.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.LIBRARY + "/documents")
public class DocumentViewerController {
    private final ViewerService viewer;

    public DocumentViewerController(ViewerService viewer) {
        this.viewer = viewer;
    }

    @GetMapping("/{id}/viewer")
    public ViewerContentResponse open(@PathVariable UUID id, HttpServletRequest request) {
        return viewer.open(id, ClientInfo.from(request));
    }
}
