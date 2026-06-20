package com.hien.marketplace.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Value Object cho địa chỉ.
 *
 * @Embeddable = object này không có table riêng, mà được "nhúng" vào table của entity chứa nó.
 * Ví dụ: Provider có Address → các field (street, city, zipCode) được lưu thẳng vào bảng providers.
 *
 * Tại sao dùng thay vì 3 field riêng?
 * - Nhóm logic: street + city + zip luôn đi cùng nhau
 * - Tái sử dụng: Provider, Customer đều có thể dùng Address
 * - Type safety: không truyền nhầm "city" vào "street"
 */
@Embeddable
public class Address {

    @Column(name = "street")
    private String street;

    @Column(name = "city")
    private String city;

    @Column(name = "zip_code")
    private String zipCode;

    // No-arg constructor cho JPA
    protected Address() {
    }

    public Address(String street, String city, String zipCode) {
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
    }

    public String getStreet() { return street; }
    public String getCity() { return city; }
    public String getZipCode() { return zipCode; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Address address)) return false;
        return Objects.equals(street, address.street)
                && Objects.equals(city, address.city)
                && Objects.equals(zipCode, address.zipCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, city, zipCode);
    }

    @Override
    public String toString() {
        return "Address{" + street + ", " + city + " " + zipCode + "}";
    }
}
