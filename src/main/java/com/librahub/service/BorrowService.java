
package com.librahub.service;

import com.librahub.entity.Book;
import com.librahub.entity.BorrowRecord;
import com.librahub.entity.BorrowRecord.LoanStatus;
import com.librahub.entity.Member;
import com.librahub.repository.BookRepository;
import com.librahub.repository.BorrowRecordRepository;
import com.librahub.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BorrowService {

    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public BorrowService(
            BookRepository bookRepository,
            MemberRepository memberRepository,
            BorrowRecordRepository borrowRecordRepository) {
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    public List<BorrowRecord> getAllRecords() {
        updateOverdueStatuses();

        List<BorrowRecord> records = borrowRecordRepository.findAll();

        for (BorrowRecord record : records) {
            record.setFineAmount(calculateFine(record));
        }

        return borrowRecordRepository.saveAll(records);
    }

    public BorrowRecord getRecordById(Long id) {
        return borrowRecordRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Borrow record not found: " + id));
    }

    @Transactional
    public BorrowRecord issueBook(Long bookId, Long memberId) {
        updateOverdueStatuses();
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() ->
                        new RuntimeException("Book not found: " + bookId));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new RuntimeException("Member not found: " + memberId));
        long activeLoans = borrowRecordRepository
                .findByMemberId(memberId)
                .stream()
                .filter(record ->
                        record.getStatus() == LoanStatus.ISSUED
                                || record.getStatus() == LoanStatus.OVERDUE)
                .count();

        if (activeLoans >= 3) {
            throw new IllegalStateException(
                    "Member cannot borrow more than 3 books at a time");
        }
        if (book.getAvailableCopies() <= 0) {
            throw new IllegalStateException("No copies available");
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);

        BorrowRecord record = new BorrowRecord();
        record.setBook(book);
        record.setMember(member);
        record.setIssueDate(LocalDate.now());
        record.setDueDate(LocalDate.now().plusDays(14));
        record.setStatus(LoanStatus.ISSUED);

        return borrowRecordRepository.save(record);
    }

    @Transactional
    public BorrowRecord returnBook(Long recordId) {
        BorrowRecord record = getRecordById(recordId);

        if (record.getStatus() == LoanStatus.RETURNED) {
            throw new IllegalStateException("Book has already been returned");
        }

        Book book = record.getBook();
        book.setAvailableCopies(
                Math.min(book.getAvailableCopies() + 1, book.getTotalCopies()));
        bookRepository.save(book);

        record.setReturnDate(LocalDate.now());
        record.setFineAmount(calculateFine(record));
        record.setStatus(LoanStatus.RETURNED);

        return borrowRecordRepository.save(record);
    }

    @Transactional
    public void updateOverdueStatuses() {
        LocalDate today = LocalDate.now();

        List<BorrowRecord> overdueRecords =
                borrowRecordRepository.findByStatusAndDueDateBefore(
                        LoanStatus.ISSUED, today);

        for (BorrowRecord record : overdueRecords) {
            record.setStatus(LoanStatus.OVERDUE);
        }

        borrowRecordRepository.saveAll(overdueRecords);
    }

    public List<BorrowRecord> getOverdueRecords() {
        updateOverdueStatuses();

        List<BorrowRecord> records =
                borrowRecordRepository.findByStatus(LoanStatus.OVERDUE);

        for (BorrowRecord record : records) {
            record.setFineAmount(calculateFine(record));
        }

        return borrowRecordRepository.saveAll(records);
    }

    public List<BorrowRecord> getMemberRecords(Long memberId) {
        return borrowRecordRepository.findByMemberId(memberId);
    }
    public double calculateFine(BorrowRecord record) {
        LocalDate endDate = record.getReturnDate() != null
                ? record.getReturnDate()
                : LocalDate.now();

        long overdueDays = java.time.temporal.ChronoUnit.DAYS
                .between(record.getDueDate(), endDate);

        return Math.max(0, overdueDays) * 5.0;
    }
}
