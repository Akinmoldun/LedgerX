package com.ledgerx.transfer;

import com.ledgerx.transfer.dto.CreateTransferRequest;
import com.ledgerx.transfer.dto.TransferPostResult;
import com.ledgerx.transfer.dto.TransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Transfer endpoints. Header extraction, validation, and status mapping only,
 * keeping business logic and transaction boundaries in the service layer.
 */
@RestController
@Tag(name = "Transfers", description = "Idempotent double-entry transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/api/v1/transfers")
    @Operation(summary = "Post a transfer (Idempotency-Key required)")
    public ResponseEntity<TransferResponse> post(
            @RequestHeader("Idempotency-Key")
            @Parameter(description = "Unique key; replays return the original result", required = true)
            String idempotencyKey,
            @RequestHeader("X-Tenant-Id")
            @Parameter(description = "Tenant id", required = true) String tenantId,
            @Valid @RequestBody CreateTransferRequest request) {
        TransferPostResult result = transfers.post(
                parseUuid(tenantId, "X-Tenant-Id"), request, idempotencyKey);
        if (result.created()) {
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}").buildAndExpand(result.transfer().id()).toUri();
            return ResponseEntity.created(location).body(result.transfer());
        }
        return ResponseEntity.ok(result.transfer()); // idempotent replay
    }

    @GetMapping("/api/v1/transfers/{id}")
    @Operation(summary = "Get one transfer")
    public TransferResponse get(
            @RequestHeader("X-Tenant-Id")
            @Parameter(description = "Tenant id", required = true) String tenantId,
            @PathVariable UUID id) {
        return transfers.get(parseUuid(tenantId, "X-Tenant-Id"), id);
    }

    private UUID parseUuid(String value, String header) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, header + " must be a UUID");
        }
    }
}
