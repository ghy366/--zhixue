package com.zhixue.repository;

import com.zhixue.entity.AiChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiChannelRepository extends JpaRepository<AiChannel, Long> {
    List<AiChannel> findByEnabledTrueOrderBySortNoAscIdAsc();
    List<AiChannel> findAllByOrderBySortNoAscIdAsc();
}
