package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.review.Review;
import com.hien.marketplace.interfaces.dto.response.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Review entity → DTO conversion.
 *
 * Note: Creation handled in service layer because Review requires
 * booking, customer, vendor lookups and validation.
 */
@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(source = "booking.id", target = "bookingId")
    @Mapping(source = "booking.service.id", target = "serviceId")
    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "booking.service.vendor.id", target = "vendorId")
    @Mapping(target = "serviceTitle", ignore = true)
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "vendorName", ignore = true)
    ReviewResponse toResponse(Review review);
}