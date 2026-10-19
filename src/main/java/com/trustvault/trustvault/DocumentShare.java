
package com.trustvault.trustvault;

import jakarta.persistence.*;

@Entity
@Table(
        name = "document_shares",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"document_id", "shared_with_username"}
        )
)
public class DocumentShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "shared_with_username", nullable = false)
    private String sharedWithUsername;

    public DocumentShare() {
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getSharedWithUsername() {
        return sharedWithUsername;
    }

    public void setSharedWithUsername(String sharedWithUsername) {
        this.sharedWithUsername = sharedWithUsername;
    }
}

