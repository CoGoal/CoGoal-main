package edu.stankin.cogoalmain.service;

import edu.stankin.cogoalmain.db.entity.Message;
import edu.stankin.cogoalmain.db.entity.PactParticipant;
import edu.stankin.cogoalmain.db.repo.MessageRepository;
import edu.stankin.cogoalmain.web.dto.pact.MessagePageResponse;
import edu.stankin.cogoalmain.web.dto.pact.MessageResponse;
import edu.stankin.cogoalmain.web.mapper.PactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pact chat, open to participants who have not left.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final PactService pactService;
    private final PactMapper pactMapper;

    /**
     * Newest messages first, {@code limit} at a time. Pass the returned {@code nextBefore} as {@code before}
     * to get older ones.
     */
    @Transactional(readOnly = true)
    public MessagePageResponse list(UUID userId, UUID pactId, Instant before, int limit) {
        pactService.requireMember(pactId, userId);

        PageRequest page = PageRequest.of(0, limit);
        List<Message> messages = before == null
                ? messageRepository.findLatest(pactId, page)
                : messageRepository.findBefore(pactId, before, page);

        Instant nextBefore = messages.size() == limit ? messages.get(messages.size() - 1).getCreatedAt() : null;
        return new MessagePageResponse(messages.stream().map(pactMapper::toResponse).toList(), nextBefore);
    }

    @Transactional
    public MessageResponse send(UUID userId, UUID pactId, String text) {
        PactParticipant member = pactService.requireMember(pactId, userId);

        Message message = new Message();
        message.setPact(member.getPact());
        message.setSender(member.getUser());
        message.setText(text);
        return pactMapper.toResponse(messageRepository.save(message));
    }
}
