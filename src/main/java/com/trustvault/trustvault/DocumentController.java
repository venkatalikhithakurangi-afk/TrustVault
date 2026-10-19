
package com.trustvault.trustvault;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;

@Controller
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository shareRepository;

    public DocumentController(
            DocumentRepository documentRepository,
            DocumentShareRepository shareRepository) {
        this.documentRepository = documentRepository;
        this.shareRepository = shareRepository;
    }

    // 1. Open upload page
    @GetMapping("/documents/upload")
    public String showUploadPage() {
        return "upload";
    }

    // 2. Upload a document
    @PostMapping("/documents/upload")
    public String uploadDocument(
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            Model model) {

        if (name == null || name.isBlank()
                || category == null || category.isBlank()
                || file == null || file.isEmpty()) {
            model.addAttribute("message",
                    "Please enter all details and select a file.");
            return "upload";
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            model.addAttribute("message",
                    "File must be 10 MB or less.");
            return "upload";
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            model.addAttribute("message", "Invalid filename.");
            return "upload";
        }

        String safeFilename =
                Paths.get(originalFilename).getFileName().toString();

        int dotIndex = safeFilename.lastIndexOf('.');

        if (dotIndex <= 0 || dotIndex == safeFilename.length() - 1) {
            model.addAttribute("message",
                    "File must have a valid extension.");
            return "upload";
        }

        String extension =
                safeFilename.substring(dotIndex + 1).toLowerCase();

        if (!Set.of("pdf", "txt", "png", "jpg", "jpeg")
                .contains(extension)) {
            model.addAttribute("message",
                    "Only PDF, TXT, PNG, JPG and JPEG files are allowed.");
            return "upload";
        }

        Path destination = null;

        try {
            Path uploadDir = Paths.get("trustvault-uploads")
                    .toAbsolutePath().normalize();

            Files.createDirectories(uploadDir);

            String storedFilename = UUID.randomUUID() + "." + extension;
            destination = uploadDir.resolve(storedFilename).normalize();

            if (!destination.startsWith(uploadDir)) {
                model.addAttribute("message", "Invalid file path.");
                return "upload";
            }

            Files.copy(file.getInputStream(), destination,
                    StandardCopyOption.REPLACE_EXISTING);

            Document document = new Document();
            document.setName(name.trim());
            document.setCategory(category);
            document.setOriginalFilename(safeFilename);
            document.setStoredFilename(storedFilename);
            document.setContentType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setOwnerUsername(authentication.getName());
            document.setUploadedAt(LocalDateTime.now());

            documentRepository.save(document);

            model.addAttribute("message",
                    "Document uploaded successfully!");

        } catch (IOException | RuntimeException e) {
            if (destination != null) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException ignored) {
                    // Cleanup failure should be checked separately.
                }
            }

            model.addAttribute("message",
                    "Upload failed. Check the server console.");
        }

        return "upload";
    }

    // 3. Show documents owned by the user and shared with the user
    @GetMapping("/documents")
    public String listDocuments(
            Authentication authentication,
            Model model) {

        String username = authentication.getName();

        Map<Long, Document> documents = new LinkedHashMap<>();

        // Documents owned by the logged-in user
        for (Document doc :
                documentRepository.findByOwnerUsername(username)) {
            documents.put(doc.getId(), doc);
        }

        // Documents shared with the logged-in user
        for (DocumentShare share :
                shareRepository.findBySharedWithUsername(username)) {

            documentRepository.findById(share.getDocumentId())
                    .ifPresent(doc ->
                            documents.put(doc.getId(), doc));
        }

        model.addAttribute(
                "documents",
                new ArrayList<>(documents.values()));

        return "documents";
    }

    // 4. Download a document only if authorized
    @GetMapping("/documents/download/{id}")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable Long id,
            Authentication authentication) throws IOException {

        Document document = documentRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND));

        String username = authentication.getName();

        boolean isOwner =
                document.getOwnerUsername().equals(username);

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN"));

        boolean isSharedWithUser = shareRepository
                .findByDocumentIdAndSharedWithUsername(id, username)
                .isPresent();

        if (!isOwner && !isAdmin && !isSharedWithUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Path uploadDir = Paths.get("trustvault-uploads")
                .toAbsolutePath().normalize();

        Path filePath = uploadDir
                .resolve(document.getStoredFilename()).normalize();

        if (!filePath.startsWith(uploadDir) || !Files.exists(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        byte[] fileBytes = Files.readAllBytes(filePath);

        String contentType = document.getContentType();

        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String downloadFilename =
                document.getOriginalFilename().replace("\"", "");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadFilename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(fileBytes);
    }
}
