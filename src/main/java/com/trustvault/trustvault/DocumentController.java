
package com.trustvault.trustvault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository shareRepository;
    private final AuditService auditService;

    public DocumentController(
            DocumentRepository documentRepository,
            DocumentShareRepository shareRepository,
            AuditService auditService) {

        this.documentRepository = documentRepository;
        this.shareRepository = shareRepository;
        this.auditService = auditService;
    }

    @GetMapping("/documents")
    public String listDocuments(
            Authentication authentication,
            Model model) {

        String username = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            model.addAttribute("documents", documentRepository.findAll());
        } else {
            model.addAttribute(
                    "documents",
                    documentRepository.findAll().stream()
                            .filter(doc ->
                                    username.equals(doc.getOwnerUsername())
                                            || shareRepository
                                            .findByDocumentIdAndSharedWithUsername(
                                                    doc.getId(), username)
                                            .isPresent())
                            .toList());
        }

        return "documents";
    }

    @GetMapping("/documents/upload")
    public String uploadPage() {
        return "upload";
    }

    @PostMapping("/documents/upload")
    public String uploadDocument(
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            Model model) throws IOException {

        if (name == null || name.isBlank()
                || category == null || category.isBlank()
                || file.isEmpty()) {

            model.addAttribute(
                    "error",
                    "Please fill all fields and select a file.");
            return "upload";
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            model.addAttribute(
                    "error",
                    "File size must not exceed 10 MB.");
            return "upload";
        }

        Path uploadDir = Paths.get("trustvault-uploads")
                .toAbsolutePath().normalize();

        Files.createDirectories(uploadDir);

        String originalFilename = Path.of(file.getOriginalFilename())
                .getFileName().toString();

        String storedFilename = UUID.randomUUID()
                + getExtension(originalFilename);

        Path destination = uploadDir.resolve(storedFilename).normalize();

        if (!destination.startsWith(uploadDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        Files.copy(
                file.getInputStream(),
                destination,
                StandardCopyOption.REPLACE_EXISTING);

        Document document = new Document();
        document.setName(name);
        document.setCategory(category);
        document.setOriginalFilename(originalFilename);
        document.setStoredFilename(storedFilename);
        document.setContentType(file.getContentType());
        document.setOwnerUsername(authentication.getName());
        document.setUploadedAt(LocalDateTime.now());

        documentRepository.save(document);

        // Audit Trail: successful document upload
        auditService.logAction(
                authentication.getName(),
                "DOCUMENT_UPLOAD",
                document.getName());

        return "redirect:/documents";
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private Document getAuthorizedDocument(
            Long id,
            Authentication authentication) {

        Document document = documentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        String username = authentication.getName();

        boolean isOwner = username.equals(document.getOwnerUsername());

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        boolean isShared = shareRepository
                .findByDocumentIdAndSharedWithUsername(id, username)
                .isPresent();

        if (!isOwner && !isAdmin && !isShared) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return document;
    }

    private Path getFilePath(Document document) {
        Path uploadDir = Paths.get("trustvault-uploads")
                .toAbsolutePath().normalize();

        Path filePath = uploadDir
                .resolve(document.getStoredFilename()).normalize();

        if (!filePath.startsWith(uploadDir) || !Files.exists(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return filePath;
    }

    @GetMapping("/documents/view/{id}")
    public ResponseEntity<byte[]> viewDocument(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        Document document = getAuthorizedDocument(id, authentication);
        Path filePath = getFilePath(document);

        String contentType = document.getContentType();

        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String filename = document.getOriginalFilename().replace("\"", "");

        // Audit Trail: authorized document view
        auditService.logAction(
                authentication.getName(),
                "DOCUMENT_VIEW",
                document.getName());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(Files.readAllBytes(filePath));
    }

    @GetMapping("/documents/download/{id}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        Document document = getAuthorizedDocument(id, authentication);
        Path filePath = getFilePath(document);

        String filename = document.getOriginalFilename().replace("\"", "");

        // Audit Trail: authorized document download
        auditService.logAction(
                authentication.getName(),
                "DOCUMENT_DOWNLOAD",
                document.getName());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(Files.readAllBytes(filePath));
    }
}
