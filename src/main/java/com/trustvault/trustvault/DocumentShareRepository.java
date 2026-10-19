
package com.trustvault.trustvault;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocumentShareRepository
        extends JpaRepository<DocumentShare, Long> {

    List<DocumentShare> findBySharedWithUsername(
            String username);

    Optional<DocumentShare> findByDocumentIdAndSharedWithUsername(
            Long documentId, String username);

    void deleteByDocumentIdAndSharedWithUsername(
            Long documentId, String username);
}

