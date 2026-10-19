
package com.trustvault.trustvault;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/documents")
public class AdminDocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository shareRepository;
    private final UserDetailsService userDetailsService;

    public AdminDocumentController(
            DocumentRepository documentRepository,
            DocumentShareRepository shareRepository,
            UserDetailsService userDetailsService) {
        this.documentRepository = documentRepository;
        this.shareRepository = shareRepository;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping
    public String adminDocuments(Model model) {
        model.addAttribute("documents", documentRepository.findAll());
        return "admin-documents";
    }

    @PostMapping("/{id}/share")
    public String shareDocument(
            @PathVariable Long id,
            @RequestParam String sharedWithUsername,
            RedirectAttributes redirectAttributes) {

        if (!documentRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute(
                    "message", "Document not found.");
            return "redirect:/admin/documents";
        }

        String username = sharedWithUsername.trim();

        try {
            UserDetails user =
                    userDetailsService.loadUserByUsername(username);

            boolean isOwner = user.getAuthorities().stream()
                    .anyMatch(a ->
                            a.getAuthority().equals("ROLE_OWNER"));

            if (!isOwner) {
                redirectAttributes.addFlashAttribute(
                        "message", "Only an OWNER account can receive access.");
                return "redirect:/admin/documents";
            }

            if (shareRepository
                    .findByDocumentIdAndSharedWithUsername(id, username)
                    .isPresent()) {
                redirectAttributes.addFlashAttribute(
                        "message", "Access has already been granted.");
                return "redirect:/admin/documents";
            }

            DocumentShare share = new DocumentShare();
            share.setDocumentId(id);
            share.setSharedWithUsername(username);
            shareRepository.save(share);

            redirectAttributes.addFlashAttribute(
                    "message", "Read access granted to " + username + ".");

        } catch (UsernameNotFoundException e) {
            redirectAttributes.addFlashAttribute(
                    "message", "That username does not exist.");
        }

        return "redirect:/admin/documents";
    }

    @PostMapping("/{id}/revoke")
    public String revokeAccess(
            @PathVariable Long id,
            @RequestParam String sharedWithUsername,
            RedirectAttributes redirectAttributes) {

        String username = sharedWithUsername.trim();

        if (!documentRepository.existsById(id)) {
            redirectAttributes.addFlashAttribute(
                    "message", "Document not found.");
            return "redirect:/admin/documents";
        }

        var share = shareRepository
                .findByDocumentIdAndSharedWithUsername(id, username);

        if (share.isPresent()) {
            shareRepository.delete(share.get());
            redirectAttributes.addFlashAttribute(
                    "message", "Access revoked for " + username + ".");
        } else {
            redirectAttributes.addFlashAttribute(
                    "message", "No matching share was found.");
        }

        return "redirect:/admin/documents";
    }
}
