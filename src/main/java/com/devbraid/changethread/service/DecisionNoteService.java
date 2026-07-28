package com.devbraid.changethread.service;

import com.devbraid.changethread.dto.request.CreateNoteRequest;
import com.devbraid.changethread.dto.request.UpdateNoteRequest;
import com.devbraid.changethread.dto.response.NoteResponse;
import com.devbraid.changethread.entity.ChangeThread;
import com.devbraid.changethread.entity.DecisionNote;
import com.devbraid.changethread.exception.NoteNotFoundException;
import com.devbraid.changethread.exception.ThreadNotFoundException;
import com.devbraid.changethread.repository.ChangeThreadRepository;
import com.devbraid.changethread.repository.DecisionNoteRepository;
import com.devbraid.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionNoteService {

    private final DecisionNoteRepository noteRepository;
    private final ChangeThreadRepository threadRepository;

    @Transactional
    public NoteResponse createNote(User user, UUID threadId, CreateNoteRequest req) {
        ChangeThread thread = threadRepository
                .findByIdAndUserId(threadId, user.getId())
                .orElseThrow(() -> new ThreadNotFoundException("Thread not found"));

        DecisionNote note = DecisionNote.builder()
                .thread(thread)
                .author(user)
                .context(req.getContext())
                .contextRef(req.getContextRef())
                .decision(req.getDecision())
                .rationale(req.getRationale())
                .alternatives(req.getAlternatives())
                .impact(req.getImpact())
                .build();

        note = noteRepository.save(note);
        log.info("Created note {} on thread {} by user {}", note.getId(), threadId, user.getId());
        return toResponse(note);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listNotes(UUID threadId) {
        return noteRepository.findByThreadIdOrderByCreatedAtDesc(threadId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NoteResponse updateNote(User user, UUID noteId, UpdateNoteRequest req) {
        DecisionNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new NoteNotFoundException("Decision note not found"));

        // Only the author can update
        if (!note.getAuthor().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to update this note");
        }

        if (req.getDecision() != null) {
            note.setDecision(req.getDecision());
        }
        if (req.getRationale() != null) {
            note.setRationale(req.getRationale());
        }
        if (req.getAlternatives() != null) {
            note.setAlternatives(req.getAlternatives());
        }
        if (req.getImpact() != null) {
            note.setImpact(req.getImpact());
        }

        note = noteRepository.save(note);
        return toResponse(note);
    }

    @Transactional
    public void deleteNote(User user, UUID noteId) {
        DecisionNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new NoteNotFoundException("Decision note not found"));

        // Only the author can delete
        if (!note.getAuthor().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to delete this note");
        }

        noteRepository.delete(note);
        log.info("Deleted note {} by user {}", noteId, user.getId());
    }

    private NoteResponse toResponse(DecisionNote note) {
        return NoteResponse.builder()
                .id(note.getId())
                .threadId(note.getThread().getId())
                .authorId(note.getAuthor().getId())
                .context(note.getContext())
                .contextRef(note.getContextRef())
                .decision(note.getDecision())
                .rationale(note.getRationale())
                .alternatives(note.getAlternatives())
                .impact(note.getImpact())
                .status(note.getStatus())
                .createdAt(note.getCreatedAt())
                .build();
    }
}
