package com.project.catxi.member.repository;

import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

  List<MatchHistory> findTop2ByUserOrderByCreatedAtDesc(Member user);

  Slice<MatchHistory> findAllByUserOrderByCreatedAtDesc(Member user, Pageable pageable);

  @Query("""
    SELECT DISTINCT m FROM MatchHistory m
    LEFT JOIN m.fellas f
    WHERE m.user.email = :email
       OR f = :nickname
    ORDER BY m.createdAt DESC
""")
  Slice<MatchHistory> findHistoriesByUserOrFella(
      @Param("email") String email,
      @Param("nickname") String nickname,
      Pageable pageable
  );
}

