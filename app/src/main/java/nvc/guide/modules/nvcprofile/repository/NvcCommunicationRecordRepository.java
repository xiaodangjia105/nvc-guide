package nvc.guide.modules.nvcprofile.repository;

import nvc.guide.modules.nvcprofile.model.NvcCommunicationRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NvcCommunicationRecordRepository extends JpaRepository<NvcCommunicationRecordEntity, Long> {

    List<NvcCommunicationRecordEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
