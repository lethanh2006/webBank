package com.webbank.customer;

public record CustomerView(long id, String cifNo, String fullName, CustomerStatus status) {

}
