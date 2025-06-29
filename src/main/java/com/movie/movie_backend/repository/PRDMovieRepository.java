package com.movie.movie_backend.repository;

import com.movie.movie_backend.entity.MovieDetail;
import com.movie.movie_backend.constant.MovieStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PRDMovieRepository extends JpaRepository<MovieDetail, String> {
    Optional<MovieDetail> findByMovieCd(String movieCd);
    Optional<MovieDetail> findByMovieNmContaining(String movieNm);
    List<MovieDetail> findByStatus(MovieStatus status);
    List<MovieDetail> findByMovieNmContainingIgnoreCase(String movieNm);
    
    // 장르별 조회
    List<MovieDetail> findByGenreNmContaining(String genreNm);
    
    // 장르 중복 확인을 위한 쿼리들
    @Query("SELECT genreNm, COUNT(*) as count FROM MovieDetail WHERE genreNm IS NOT NULL GROUP BY genreNm HAVING COUNT(*) > 1 ORDER BY count DESC")
    List<Object[]> findDuplicateGenres();
    
    @Query("SELECT m FROM MovieDetail m WHERE m.genreNm = :genreNm")
    List<MovieDetail> findByExactGenreNm(@Param("genreNm") String genreNm);
    
    // 상품 관련 쿼리 메소드 추가 가능
} 
