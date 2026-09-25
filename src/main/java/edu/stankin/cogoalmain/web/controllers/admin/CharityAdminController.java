package edu.stankin.cogoalmain.web.controllers.admin;

import edu.stankin.cogoalmain.service.CharityService;
import edu.stankin.cogoalmain.web.dto.catalog.CharityRequest;
import edu.stankin.cogoalmain.web.dto.catalog.CharityResponse;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/charities")
@RequiredArgsConstructor
@Tag(name = "Admin: charities")
public class CharityAdminController {

    private final CharityService charityService;

    @GetMapping
    @Operation(summary = "All charities, including inactive")
    public Page<CharityResponse> list(@ParameterObject @SortDefault(sort = "name") Pageable pageable) {
        return charityService.listAll(pageable);
    }

    @GetMapping("/{id}")
    public CharityResponse get(@PathVariable UUID id) {
        return charityService.get(id);
    }

    @PostMapping
    public ResponseEntity<CharityResponse> create(@Valid @RequestBody CharityRequest request) {
        CharityResponse created = charityService.create(request);
        return ResponseEntity.created(AdminLocations.of(created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace the charity; a missing 'active' keeps the current value")
    public CharityResponse update(@PathVariable UUID id, @Valid @RequestBody CharityRequest request) {
        return charityService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete the charity, or deactivate it if pacts refer to it")
    public DeletionResponse delete(@PathVariable UUID id) {
        return charityService.delete(id);
    }
}
