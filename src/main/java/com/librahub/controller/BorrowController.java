
package com.librahub.controller;

import com.librahub.entity.AppUser;
import com.librahub.entity.BorrowRecord;
import com.librahub.repository.AppUserRepository;
import com.librahub.service.BorrowService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/borrow")
public class BorrowController {

    private final BorrowService borrowService;
    private final AppUserRepository userRepository;

    public BorrowController(
            BorrowService borrowService,
            AppUserRepository userRepository) {
        this.borrowService = borrowService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<BorrowRecord> getAllRecords() {
        return borrowService.getAllRecords();
    }

    @GetMapping("/{id}")
    public BorrowRecord getRecordById(@PathVariable Long id) {
        return borrowService.getRecordById(id);
    }

    @PostMapping("/member/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public BorrowRecord issueBookForMember(
            @RequestBody Map<String, Long> request,
            Authentication authentication) {

        if (authentication == null) {
            throw new AccessDeniedException("Please log in first.");
        }

        AppUser user = userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("User account not found."));

        if (!"MEMBER".equals(user.getRole()) || user.getMember() == null) {
            throw new AccessDeniedException(
                    "Only members can use this borrowing endpoint.");
        }

        Long bookId = request.get("bookId");

        if (bookId == null) {
            throw new IllegalArgumentException("Book ID is required.");
        }

        Long memberId = user.getMember().getId();

        return borrowService.issueBook(bookId, memberId);
    }


    @PostMapping("/return/{recordId}")
    public BorrowRecord returnBook(@PathVariable Long recordId) {
        return borrowService.returnBook(recordId);
    }

    @GetMapping("/overdue")
    public List<BorrowRecord> getOverdueRecords() {
        return borrowService.getOverdueRecords();
    }

    @GetMapping("/member/{memberId}")
    public List<BorrowRecord> getMemberRecords(
            @PathVariable Long memberId,
            Authentication authentication) {

        if (authentication == null) {
            throw new AccessDeniedException("Login required");
        }

        String email = authentication.getName();

        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new AccessDeniedException("User account not found"));

        if ("ADMIN".equals(user.getRole())) {
            return borrowService.getMemberRecords(memberId);
        }

        if (user.getMember() == null
                || !user.getMember().getId().equals(memberId)) {
            throw new AccessDeniedException(
                    "You can view only your own borrowing records");
        }

        return borrowService.getMemberRecords(memberId);
    }
}
