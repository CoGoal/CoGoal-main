package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Charity;
import edu.stankin.cogoalmain.db.repo.CharityRepository;
import edu.stankin.cogoalmain.db.repo.PactRepository;
import edu.stankin.cogoalmain.exception.BusinessRuleException;
import edu.stankin.cogoalmain.exception.NotFoundException;
import edu.stankin.cogoalmain.web.dto.catalog.CharityRequest;
import edu.stankin.cogoalmain.web.dto.catalog.CharityResponse;
import edu.stankin.cogoalmain.web.dto.catalog.DeletionResponse;
import edu.stankin.cogoalmain.web.mapper.CatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CharityService {

    private final CharityRepository charityRepository;
    private final PactRepository pactRepository;
    private final CatalogMapper catalogMapper;

    @Transactional(readOnly = true)
    public Page<CharityResponse> listActive(Pageable pageable) {
        return charityRepository.findByActiveTrue(pageable).map(catalogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CharityResponse> listAll(Pageable pageable) {
        return charityRepository.findAll(pageable).map(catalogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CharityResponse get(UUID id) {
        return catalogMapper.toResponse(find(id));
    }

    @Transactional
    public CharityResponse create(CharityRequest request) {
        return catalogMapper.toResponse(charityRepository.save(catalogMapper.toEntity(request)));
    }

    @Transactional
    public CharityResponse update(UUID id, CharityRequest request) {
        Charity charity = find(id);
        catalogMapper.update(request, charity);
        return catalogMapper.toResponse(charity);
    }

    /** Deletes the charity, or only deactivates it if pacts refer to it. */
    @Transactional
    public DeletionResponse delete(UUID id) {
        Charity charity = find(id);
        if (pactRepository.existsByCharityId(id)) {
            charity.setActive(false);
            return DeletionResponse.deactivated();
        }
        charityRepository.delete(charity);
        return DeletionResponse.deleted();
    }

    /**
     * Active charity for a new pact.
     *
     * @throws NotFoundException     if it does not exist
     * @throws BusinessRuleException if it is deactivated
     */
    @Transactional(readOnly = true)
    public Charity requireActive(UUID id) {
        Charity charity = find(id);
        if (!charity.isActive()) {
            throw new BusinessRuleException("Charity is not active");
        }
        return charity;
    }

    private Charity find(UUID id) {
        return charityRepository.findById(id).orElseThrow(() -> NotFoundException.of("Charity", id));
    }
}
