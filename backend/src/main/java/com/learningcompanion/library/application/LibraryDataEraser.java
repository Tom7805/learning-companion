package com.learningcompanion.library.application;

import com.learningcompanion.library.domain.Document;
import com.learningcompanion.library.domain.DocumentRepository;
import com.learningcompanion.shared.erasure.PersonalDataEraser;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Thư viện khi xóa tài khoản: tệp gốc của mọi tài liệu bị xóa khỏi kho; dòng tài liệu (và sau này đoạn văn, chỉ mục
 * của tài liệu) mất theo khóa ngoại tới tài khoản.
 */
@Component
public class LibraryDataEraser implements PersonalDataEraser {

    private final DocumentRepository documents;

    public LibraryDataEraser(DocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public String section() {
        return "library";
    }

    @Override
    public int erase(Subject subject, List<String> filesToDelete) {
        List<Document> mine = documents.findAllByOwnerIdOrderByCreatedAtDesc(subject.accountId());
        mine.forEach(document -> filesToDelete.add(document.getStorageKey()));
        return mine.size();
    }
}
