
package com.librahub.controller;
import com.librahub.entity.Book;
import java.util.TreeMap;
import com.librahub.entity.AppUser;
import com.librahub.entity.BorrowRecord;
import com.librahub.entity.BorrowRecord.LoanStatus;
import com.librahub.repository.AppUserRepository;
import com.librahub.repository.BookRepository;
import com.librahub.repository.BorrowRecordRepository;
import com.librahub.repository.MemberRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final BorrowRecordRepository borrowRepository;
    private final AppUserRepository userRepository;

    public DashboardController(
            BookRepository bookRepository,
            MemberRepository memberRepository,
            BorrowRecordRepository borrowRepository,
            AppUserRepository userRepository) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.borrowRepository = borrowRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/stats")
    public Map<String, Object> getAdminDashboard() {
        Map<String, Object> result = new HashMap<>();

        result.put("totalBookTitles", bookRepository.count());
        result.put("totalMembers", memberRepository.count());
        result.put("currentlyIssued",
                borrowRepository.countByStatus(LoanStatus.ISSUED));
        result.put("overdueLoans",
                borrowRepository.countByStatus(LoanStatus.OVERDUE));
        result.put("returnedLoans",
                borrowRepository.countByStatus(LoanStatus.RETURNED));

        double totalFines = borrowRepository.findAll().stream()
                .mapToDouble(record ->
                        record.getFineAmount() == null
                                ? 0.0 : record.getFineAmount())
                .sum();

        result.put("totalFines", totalFines);
        result.put("monthlyBorrowings", getMonthlyBorrowings(
                borrowRepository.findAll()));
        result.put("booksByCategory", getBooksByCategory());
        return result;
    }

    @GetMapping("/member")
    public Map<String, Object> getMemberDashboard(
            Authentication authentication) {

        if (authentication == null) {
            throw new AccessDeniedException("Login required");
        }

        AppUser user = userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() ->
                        new AccessDeniedException("Account not found"));

        if (!"MEMBER".equals(user.getRole())
                || user.getMember() == null) {
            throw new AccessDeniedException(
                    "This dashboard is for members only");
        }

        Long memberId = user.getMember().getId();

        List<BorrowRecord> records =
                borrowRepository.findByMemberIdOrderByIssueDateAsc(memberId);

        Map<String, Object> result = new HashMap<>();
        result.put("memberName", user.getMember().getName());
        result.put("totalBorrowings", records.size());
        result.put("currentlyIssued",
                borrowRepository.countByMemberIdAndStatus(
                        memberId, LoanStatus.ISSUED));
        result.put("overdueLoans",
                borrowRepository.countByMemberIdAndStatus(
                        memberId, LoanStatus.OVERDUE));
        result.put("returnedLoans",
                borrowRepository.countByMemberIdAndStatus(
                        memberId, LoanStatus.RETURNED));
        result.put("totalFines", records.stream()
                .mapToDouble(record ->
                        record.getFineAmount() == null
                                ? 0.0 : record.getFineAmount())
                .sum());
        result.put("monthlyBorrowings", getMonthlyBorrowings(records));
        result.put("borrowings", records);

        return result;
    }

    private Map<String, Long> getMonthlyBorrowings(
            List<BorrowRecord> records) {
        Map<String, Long> monthly = new TreeMap<>();

        for (BorrowRecord record : records) {
            if (record.getIssueDate() != null) {
                String month = YearMonth.from(record.getIssueDate())
                        .toString();
                monthly.merge(month, 1L, Long::sum);
            }
        }

        return monthly;
    }

    private Map<String, Long> getBooksByCategory() {
        Map<String, Long> categories = new TreeMap<>();

        for (Book book : bookRepository.findAll()) {
            String category = book.getCategory();

            if (category != null && !category.isBlank()) {
                categories.merge(category.trim(), 1L, Long::sum);
            }
        }

        return categories;
    }

}
