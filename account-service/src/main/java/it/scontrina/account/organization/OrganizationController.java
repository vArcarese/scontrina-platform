package it.scontrina.account.organization;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService service;

    @PostMapping
    public ResponseEntity<OrganizationResponse> register(@Valid @RequestBody RegisterOrganizationRequest request) {
        Organization org = service.register(request.vatNumber(), request.legalName());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(org.getId()).toUri();
        return ResponseEntity.created(location).body(OrganizationResponse.from(org));
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable String id) {
        return OrganizationResponse.from(service.get(id));
    }

    @GetMapping
    public List<OrganizationResponse> list() {
        return service.list().stream().map(OrganizationResponse::from).toList();
    }

    @PutMapping("/{id}/status")
    public OrganizationResponse changeStatus(@PathVariable String id,
                                             @Valid @RequestBody ChangeStatusRequest request) {
        return OrganizationResponse.from(service.changeStatus(id, request.status()));
    }

    public record RegisterOrganizationRequest(
            @NotBlank @Pattern(regexp = "\\d{11}", message = "deve essere di 11 cifre") String vatNumber,
            @NotBlank @Size(max = 255) String legalName) {
    }

    public record ChangeStatusRequest(@NotNull OrganizationStatus status) {
    }

    public record OrganizationResponse(String id, String vatNumber, String legalName,
                                       OrganizationStatus status, Instant createdAt, Instant updatedAt) {
        static OrganizationResponse from(Organization o) {
            return new OrganizationResponse(o.getId(), o.getVatNumber(), o.getLegalName(),
                    o.getStatus(), o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
