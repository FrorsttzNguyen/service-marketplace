package com.hien.marketplace.application.service;

import com.hien.marketplace.application.exception.BusinessRuleViolationException;
import com.hien.marketplace.application.exception.ResourceNotFoundException;
import com.hien.marketplace.application.mapper.ServiceMapper;
import com.hien.marketplace.domain.common.Money;
import com.hien.marketplace.domain.service.PricingType;
import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.domain.service.ServiceStatus;
import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.domain.user.UserRole;
import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.infrastructure.persistence.CategoryRepository;
import com.hien.marketplace.infrastructure.persistence.ServiceRepository;
import com.hien.marketplace.infrastructure.persistence.VendorRepository;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VendorServiceManagement#activateService} — the DRAFT → ACTIVE publish path.
 *
 * WHY this matters: a service is created DRAFT and the public catalog only lists ACTIVE services, so
 * activate() is what makes a service visible. We assert the happy path flips the status and the
 * ownership guard rejects a non-owner. Mocks mirror the PaymentServiceTest style.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorServiceActivateTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private VendorRepository vendorRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ServiceMapper serviceMapper;

    private VendorServiceManagement management;

    private static final Long USER_ID = 1L;
    private static final Long VENDOR_ID = 10L;
    private static final Long SERVICE_ID = 100L;

    private Vendor vendor;
    private ServiceEntity service;

    @BeforeEach
    void setUp() {
        management = new VendorServiceManagement(
                serviceRepository, vendorRepository, categoryRepository, serviceMapper);

        User vendorUser = new User("vendor@example.com", "hashed", "Vendor", UserRole.VENDOR);
        vendor = spy(new Vendor(vendorUser, "Vendor Biz"));
        when(vendor.getId()).thenReturn(VENDOR_ID);

        // A DRAFT service owned by VENDOR_ID.
        service = spy(new ServiceEntity(vendor, "Test Service", Money.of(10000), PricingType.FIXED, 60));

        when(vendorRepository.findByUserId(USER_ID)).thenReturn(Optional.of(vendor));
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(serviceRepository.save(any(ServiceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serviceMapper.toResponse(any(ServiceEntity.class)))
                .thenReturn(mock(ServiceResponse.class));
    }

    @Test
    @DisplayName("owner activates DRAFT service → status becomes ACTIVE")
    void shouldActivateOwnDraftService() {
        assertThat(service.getStatus()).isEqualTo(ServiceStatus.DRAFT);

        management.activateService(USER_ID, SERVICE_ID);

        assertThat(service.getStatus()).isEqualTo(ServiceStatus.ACTIVE);
        verify(serviceRepository).save(service);
    }

    @Test
    @DisplayName("non-owner → 422 BusinessRuleViolationException, status unchanged")
    void shouldRejectActivateByNonOwner() {
        Vendor otherVendor = spy(new Vendor(
                new User("other@example.com", "h", "Other", UserRole.VENDOR), "Other Biz"));
        when(otherVendor.getId()).thenReturn(999L);
        when(vendorRepository.findByUserId(USER_ID)).thenReturn(Optional.of(otherVendor));

        assertThatThrownBy(() -> management.activateService(USER_ID, SERVICE_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("your own services");

        assertThat(service.getStatus()).isEqualTo(ServiceStatus.DRAFT);
        verify(serviceRepository, never()).save(any(ServiceEntity.class));
    }

    @Test
    @DisplayName("service not found → 404 ResourceNotFoundException")
    void shouldThrowWhenServiceNotFound() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> management.activateService(USER_ID, SERVICE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
