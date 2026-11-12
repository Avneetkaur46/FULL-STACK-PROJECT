
package com.librahub.service;

import com.librahub.entity.Member;
import com.librahub.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.librahub.entity.BorrowRecord;
import com.librahub.repository.BorrowRecordRepository;
import java.util.List;

@Service
public class MemberService {
    private final BorrowRecordRepository borrowRecordRepository;
    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository,
                         BorrowRecordRepository borrowRecordRepository) {
        this.memberRepository = memberRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    public Member getMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Member not found with ID: " + id));
    }

    public List<Member> searchByName(String name) {
        return memberRepository.findByNameContainingIgnoreCase(name);
    }

    @Transactional
    public Member addMember(Member member) {
        if (memberRepository.findByEmail(member.getEmail()).isPresent()) {
            throw new IllegalArgumentException("A member with this email already exists");
        }

        return memberRepository.save(member);
    }

    @Transactional
    public Member updateMember(Long id, Member updatedMember) {
        Member existingMember = getMemberById(id);

        memberRepository.findByEmail(updatedMember.getEmail())
                .filter(member -> !member.getId().equals(id))
                .ifPresent(member -> {
                    throw new IllegalArgumentException(
                            "A member with this email already exists");
                });

        existingMember.setName(updatedMember.getName());
        existingMember.setEmail(updatedMember.getEmail());
        existingMember.setPhone(updatedMember.getPhone());

        return memberRepository.save(existingMember);
    }

    @Transactional
    public void deleteMember(Long id) {
        Member member = getMemberById(id);

        boolean hasActiveLoans = borrowRecordRepository
                .findByMemberId(id)
                .stream()
                .anyMatch(record ->
                        record.getStatus() == BorrowRecord.LoanStatus.ISSUED
                                || record.getStatus() == BorrowRecord.LoanStatus.OVERDUE);

        if (hasActiveLoans) {
            throw new IllegalStateException(
                    "Cannot delete a member who has active or overdue loans");
        }

        memberRepository.delete(member);
    }
}
