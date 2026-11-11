package com.webbank.customer;

/**
 * The authenticated customer making the request. Declare it as a controller method parameter;
 * it is resolved by the authentication mechanism, never taken from the request body or path.
 */
public record CurrentCustomer(long id) {

}
