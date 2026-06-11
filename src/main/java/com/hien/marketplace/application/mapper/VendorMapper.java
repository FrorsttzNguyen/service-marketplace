package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.vendor.Vendor;
import com.hien.marketplace.interfaces.dto.response.VendorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper for Vendor entity → DTO conversion.
 *
 * WHY: Vendor has composition relationship with User (has-a User, not extends).
 * Response flattens user info for API consumers.
 *
 * Note: Creation/Update handled in service layer via domain methods
 * because Vendor uses Address value object and immutable-style construction.
 */
@Mapper(componentModel = "spring")
public interface VendorMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "ratingAvg", target = "averageRating")
    @Mapping(target = "address", expression = "java(formatAddress(vendor.getAddress()))")
    @Mapping(target = "totalReviews", constant = "0")
    @Mapping(target = "completedBookings", constant = "0")
    VendorResponse toResponse(Vendor vendor);

    @Named("formatAddress")
    default String formatAddress(com.hien.marketplace.domain.common.Address address) {
        if (address == null) return null;
        String result = address.getStreet();
        if (address.getCity() != null) {
            result = (result == null) ? address.getCity() : result + ", " + address.getCity();
        }
        return result;
    }
}