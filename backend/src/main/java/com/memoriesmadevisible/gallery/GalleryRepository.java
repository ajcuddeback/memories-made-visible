package com.memoriesmadevisible.gallery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GalleryRepository extends JpaRepository<Gallery, Long> {
    List<Gallery> findAllByOrderByIdAsc();
    boolean existsByBookingId(Long bookingId);
    void deleteByBookingId(Long bookingId);
}
