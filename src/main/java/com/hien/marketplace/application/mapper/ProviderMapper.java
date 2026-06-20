package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.provider.Provider;
import com.hien.marketplace.interfaces.dto.response.ProviderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper for Provider entity → DTO conversion.
 *
 * WHY: Provider has composition relationship with User (has-a User, not extends).
 * Response flattens user info for API consumers.
 *
 * Note: Creation/Update handled in service layer via domain methods
 * because Provider uses Address value object and immutable-style construction.
 */
@Mapper(componentModel = "spring")
public interface ProviderMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "ratingAvg", target = "averageRating")
    @Mapping(target = "address", expression = "java(formatAddress(provider.getAddress()))")
    @Mapping(target = "totalReviews", constant = "0")
    @Mapping(target = "completedBookings", constant = "0")
    ProviderResponse toResponse(Provider provider);

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