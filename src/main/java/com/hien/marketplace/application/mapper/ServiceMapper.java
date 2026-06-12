package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.service.ServiceEntity;
import com.hien.marketplace.interfaces.dto.response.ServiceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper for Service entity → DTO conversion.
 *
 * WHY: Service has complex mappings:
 * - vendorId → vendor.id (nested object access)
 * - Money value object → BigDecimal (flattening)
 * - vendorName, categoryName not in entity (require joins)
 *
 * Note: Creation/Update handled in service layer via domain methods
 * because ServiceEntity uses immutable-style construction (no setters for many fields).
 */
@Mapper(componentModel = "spring")
public interface ServiceMapper {

    @Mapping(source = "vendor.id", target = "vendorId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "name", target = "title")
    @Mapping(target = "basePrice", expression = "java(toDecimal(service.getBasePrice()))")
    @Mapping(source = "durationMinutes", target = "durationHours")
    @Mapping(source = "vendor.address.street", target = "address")
    @Mapping(source = "city", target = "city")
    @Mapping(target = "vendorName", ignore = true)
    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "imageUrl", ignore = true)
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(target = "totalReviews", constant = "0")
    @Mapping(target = "totalBookings", constant = "0")
    ServiceResponse toResponse(ServiceEntity service);

    @Named("toDecimal")
    default java.math.BigDecimal toDecimal(com.hien.marketplace.domain.common.Money money) {
        if (money == null) return null;
        return java.math.BigDecimal.valueOf(money.getAmountCents(), 2);
    }
}