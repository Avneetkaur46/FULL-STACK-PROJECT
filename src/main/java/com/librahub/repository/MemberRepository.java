
package com.librahub.repository;

import com.librahub.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    List<Member> findByNameContainingIgnoreCase(String name);

    List<Member> findByPhoneContaining(String phone);
}
