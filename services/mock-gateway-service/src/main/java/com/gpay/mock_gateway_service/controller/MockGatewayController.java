package com.gpay.mock_gateway_service.controller;

import com.gpay.mock_gateway_service.dto.MockGatewayTopUpRequest;
import com.gpay.mock_gateway_service.dto.MockGatewayTopUpResponse;
import com.gpay.mock_gateway_service.service.MockGatewayTopUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-gateway")
@RequiredArgsConstructor
public class MockGatewayController {

	private final MockGatewayTopUpService mockGatewayTopUpService;

	@PostMapping("/top-up")
	public ResponseEntity<MockGatewayTopUpResponse> topUp(
			@RequestHeader(value = "X-Trace-Id", required = false) String traceId,
			@Valid @RequestBody MockGatewayTopUpRequest request) {
		return ResponseEntity.ok(mockGatewayTopUpService.topUp(request, traceId));
	}
}
