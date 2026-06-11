package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.user.User;
import com.hien.marketplace.interfaces.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for User entity ↔ DTO conversion.
 *
 * WHY MapStruct:
 * - Generates type-safe mapping code at compile time
 * - No reflection overhead at runtime (unlike ModelMapper)
 * - Catches mapping errors during compilation, not at runtime
 *
 * HOW IT WORKS:
 * 1. Annotate interface with @Mapper
 * 2. Define abstract methods for each conversion
 * 3. MapStruct generates implementation class at compile time
 * 4. Spring injects the generated implementation
 *
 * componentModel = "spring": Makes generated class a Spring bean
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Convert User entity to UserResponse DTO.
     *
     * WHY @Mapping: Field names match automatically, but we document
     * the mapping explicitly for clarity and future maintenance.
     *
     * Note: Password hash is NOT mapped (security — never expose in API)
     */
    UserResponse toResponse(User user);
}