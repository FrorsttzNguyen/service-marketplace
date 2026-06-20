package com.hien.marketplace.application.mapper;

import com.hien.marketplace.domain.booking.Booking;
import com.hien.marketplace.domain.common.Address;
import com.hien.marketplace.interfaces.dto.response.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * MapStruct mapper for Booking entity → DTO conversion.
 *
 * WHY: Booking has many relationships (customer, service, provider).
 * We flatten these for the response DTO.
 *
 * Note: Creation handled in service layer via domain methods
 * because Booking uses TimeSlot value object (LocalTime) but API uses LocalDateTime.
 */
@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "service.id", target = "serviceId")
    @Mapping(source = "provider.id", target = "providerId")
    @Mapping(target = "startTime", expression = "java(combineDateTime(booking.getBookingDate(), booking.getTimeSlot().getStartTime()))")
    @Mapping(target = "endTime", expression = "java(combineDateTime(booking.getBookingDate(), booking.getTimeSlot().getEndTime()))")
    @Mapping(target = "totalPrice", expression = "java(toDecimal(booking.getSubtotal()))")
    @Mapping(target = "commission", expression = "java(toDecimal(booking.getCommission()))")
    @Mapping(target = "total", expression = "java(toDecimal(booking.getTotal()))")
    @Mapping(target = "currency", constant = "VND")
    @Mapping(target = "serviceStreet", expression = "java(addressStreet(booking.getServiceAddress()))")
    @Mapping(target = "serviceCity", expression = "java(addressCity(booking.getServiceAddress()))")
    @Mapping(target = "serviceZipCode", expression = "java(addressZipCode(booking.getServiceAddress()))")
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "serviceTitle", ignore = true)
    @Mapping(target = "providerName", ignore = true)
    BookingResponse toResponse(Booking booking);

    @Named("toDecimal")
    default java.math.BigDecimal toDecimal(com.hien.marketplace.domain.common.Money money) {
        if (money == null) return null;
        return java.math.BigDecimal.valueOf(money.getAmountCents(), 2);
    }

    @Named("combineDateTime")
    default LocalDateTime combineDateTime(LocalDate date, LocalTime time) {
        if (date == null || time == null) return null;
        return LocalDateTime.of(date, time);
    }

    default String addressStreet(Address address) {
        return address == null ? null : address.getStreet();
    }

    default String addressCity(Address address) {
        return address == null ? null : address.getCity();
    }

    default String addressZipCode(Address address) {
        return address == null ? null : address.getZipCode();
    }
}
