package com.webbank.customer.internal;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.webbank.customer.CustomerStatus;
import com.webbank.customer.CustomerView;

@Entity
@Table(name = "customer")
public class Customer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cif_no", nullable = false, updatable = false)
	private String cifNo;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	private String phone;

	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CustomerStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Customer() {
	}

	Customer(String cifNo, String fullName, String phone, String email, Instant createdAt) {
		this.cifNo = cifNo;
		this.fullName = fullName;
		this.phone = phone;
		this.email = email;
		this.status = CustomerStatus.ACTIVE;
		this.createdAt = createdAt;
	}

	CustomerView toView() {
		return new CustomerView(id, cifNo, fullName, status);
	}

}
