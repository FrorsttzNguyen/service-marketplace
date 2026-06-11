package com.hien.marketplace.infrastructure.persistence;

import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    List<ServiceEntity> findByVendorId(Long vendorId);

    List<ServiceEntity> findByCategoryId(Long categoryId);

    List<ServiceEntity> findByStatus(ServiceStatus status);

    List<ServiceEntity> findByCategoryIdAndStatus(Long categoryId, ServiceStatus status);

    List<ServiceEntity> findByVendorIdAndStatus(Long vendorId, ServiceStatus status);
}
