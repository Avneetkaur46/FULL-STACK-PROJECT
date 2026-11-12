
package com.librahub.repository;

import com.librahub.entity.BorrowRecord;
import com.librahub.entity.BorrowRecord.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BorrowRecordRepository
        extends JpaRepository<BorrowRecord, Long> {

    List<BorrowRecord> findByMemberId(Long memberId);

    List<BorrowRecord> findByBookId(Long bookId);

    List<BorrowRecord> findByStatus(LoanStatus status);

    List<BorrowRecord> findByStatusAndDueDateBefore(
            LoanStatus status, LocalDate date);

    List<BorrowRecord> findByMemberIdAndStatus(
            Long memberId, LoanStatus status);
    long countByStatus(BorrowRecord.LoanStatus status);

    long countByStatusAndDueDateBefore(
            BorrowRecord.LoanStatus status,
            java.time.LocalDate date);
    long countByMemberId(Long memberId);

    long countByMemberIdAndStatus(
            Long memberId, LoanStatus status);

    List<BorrowRecord> findByMemberIdOrderByIssueDateAsc(
            Long memberId);
}
