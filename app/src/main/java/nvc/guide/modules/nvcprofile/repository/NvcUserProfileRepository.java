package nvc.guide.modules.nvcprofile.repository;

import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NvcUserProfileRepository extends JpaRepository<NvcUserProfileEntity, Long> {

    Optional<NvcUserProfileEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
