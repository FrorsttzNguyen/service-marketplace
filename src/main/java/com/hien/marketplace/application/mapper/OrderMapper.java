package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.order.Order;
import com.hien.marketplace.interfaces.dto.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Order entity → DTO conversion.
 *
 * Note: Creation handled in service layer via Order constructor
 * because Order has specific business logic for subtotal, commission, total calculation.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "booking.id", target = "bookingId")
    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "booking.service.vendor.id", target = "vendorId")
    @Mapping(target = "totalAmount", expression = "java(java.math.BigDecimal.valueOf(order.getTotal().getAmountCents(), 2))")
    @Mapping(target = "currency", constant = "VND")
    @Mapping(target = "paymentMethod", constant = "STRIPE")
    @Mapping(target = "paymentId", ignore = true)
    OrderResponse toResponse(Order order);
}