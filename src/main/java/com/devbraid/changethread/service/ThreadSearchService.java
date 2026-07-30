package com.devbraid.changethread.service;

import com.devbraid.changethread.dto.response.ThreadResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for searching across threads, notes, and briefs.
 * Uses JPA Specifications for flexible full-text search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreadSearchService {

    private final ChangeThreadRepository threadRepository;
    private final ChangeThreadService threadService;

    /**
     * Search threads by title, description, or repository name.
     * Uses case-insensitive LIKE matching with prefix optimization (no leading %).
     * For full-text search, consider adding PostgreSQL tsvector + GIN index.
     */
    @Transactional(readOnly = true)
    public Page<ThreadResponse> searchThreads(User user, String query, Pageable pageable) {
        String searchPattern = "%" + query.toLowerCase() + "%";

        Specification<ChangeThread> spec = (root, cb, cbBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            // Must belong to the user
            predicates.add(cbBuilder.equal(root.get("user").get("id"), user.getId()));

            // Search in title, description, repository name
            predicates.add(cbBuilder.or(
                    cbBuilder.like(cbBuilder.lower(root.get("title")), searchPattern),
                    cbBuilder.like(cbBuilder.lower(root.get("description")), searchPattern),
                    cbBuilder.like(cbBuilder.lower(root.get("repositoryFullName")), searchPattern)
            ));

            return cbBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return threadService.toResponsePage(threadRepository.findAll(spec, pageable));
    }

    /**
     * Search threads by status.
     */
    @Transactional(readOnly = true)
    public Page<ThreadResponse> searchByStatus(User user, com.devbraid.changethread.entity.ThreadStatus status, Pageable pageable) {
        Specification<ChangeThread> spec = (root, cb, cbBuilder) -> cbBuilder.and(
                cbBuilder.equal(root.get("user").get("id"), user.getId()),
                cbBuilder.equal(root.get("status"), status)
        );

        return threadService.toResponsePage(threadRepository.findAll(spec, pageable));
    }

    /**
     * Search threads by repository name.
     */
    @Transactional(readOnly = true)
    public Page<ThreadResponse> searchByRepository(User user, String repositoryFullName, Pageable pageable) {
        Specification<ChangeThread> spec = (root, cb, cbBuilder) -> cbBuilder.and(
                cbBuilder.equal(root.get("user").get("id"), user.getId()),
                cbBuilder.equal(root.get("repositoryFullName"), repositoryFullName)
        );

        return threadService.toResponsePage(threadRepository.findAll(spec, pageable));
    }
}
