package com.webhookplatform.webhook.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applicationRepository, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.clock = clock;
    }

    @Transactional
    public ApplicationResponse create(UUID ownerUserId, CreateApplicationRequest request) {
        Instant now = clock.instant();
        Application application = Application.create(
                ownerUserId,
                request.name(),
                request.slug(),
                request.environment(),
                now);
        try {
            return ApplicationResponse.from(applicationRepository.saveAndFlush(application));
        } catch (DataIntegrityViolationException exception) {
            String detail = exception.getMostSpecificCause().getMessage();
            if (detail != null && detail.contains("uq_applications_owner_slug")) {
                throw new ApplicationSlugConflictException();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(UUID ownerUserId) {
        return applicationRepository.findAllByOwnerUserIdOrderByCreatedAtDescIdDesc(ownerUserId)
                .stream()
                .map(ApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID applicationId, UUID ownerUserId) {
        return ApplicationResponse.from(requireOwnedApplication(applicationId, ownerUserId));
    }

    @Transactional
    public ApplicationResponse update(
            UUID applicationId,
            UUID ownerUserId,
            UpdateApplicationRequest request) {
        Application application = requireOwnedApplication(applicationId, ownerUserId);
        application.update(request.name(), request.status(), clock.instant());
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public Application requireOwnedApplication(UUID applicationId, UUID ownerUserId) {
        return applicationRepository.findByIdAndOwnerUserId(applicationId, ownerUserId)
                .orElseThrow(ApplicationNotFoundException::new);
    }
}
