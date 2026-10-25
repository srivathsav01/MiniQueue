package com.sri.miniqueue.serviceImpl;

import com.sri.miniqueue.dto.ClientResponse;
import com.sri.miniqueue.entity.Client;
import com.sri.miniqueue.exception.ConflictException;
import com.sri.miniqueue.exception.ResourceNotFoundException;
import com.sri.miniqueue.repository.ClientRepository;
import com.sri.miniqueue.security.ClientPrincipal;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.utils.ApiKeys;
import com.sri.miniqueue.utils.Names;
import com.sri.miniqueue.utils.Scopes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {

    // last_used_at answers "is this key still in use?", so minute precision is plenty,
    // and it avoids a database write on every single request.
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final ClientRepository clientRepository;

    @Override
    @Transactional
    public ClientResponse createClient(String rawName, List<String> rawScopes) {
        String name = Names.normalize(rawName, "Client");
        Set<String> scopes = Scopes.normalize(rawScopes);
        String apiKey = ApiKeys.generate();

        Client client = new Client();
        client.setName(name);
        client.setScopes(String.join(" ", scopes));
        client.setCreatedAt(LocalDateTime.now());
        setKey(client, apiKey);
        try {
            client = clientRepository.saveAndFlush(client);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An active client already exists with the name " + name);
        }
        log.info("Client {} created with scopes [{}]", name, client.getScopes());
        return toResponse(client, apiKey);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientResponse> getAllClients() {
        return clientRepository.findByRevokedAtIsNullOrderByCreatedAtAsc().stream()
                .map(client -> toResponse(client, null))
                .toList();
    }

    @Override
    @Transactional
    public ClientResponse rotateKey(String rawName) {
        Client client = findActive(rawName);
        String apiKey = ApiKeys.generate();
        setKey(client, apiKey); // managed entity: the UPDATE runs at commit; the old key dies with it
        log.info("Client {} key rotated", client.getName());
        return toResponse(client, apiKey);
    }

    @Override
    @Transactional
    public String revokeClient(String rawName) {
        Client client = findActive(rawName);
        client.setRevokedAt(LocalDateTime.now());
        log.info("Client {} revoked", client.getName());
        return "Client " + client.getName() + " revoked";
    }

    @Override
    @Transactional
    public Optional<ClientPrincipal> authenticate(String apiKey) {
        if (!ApiKeys.looksLikeKey(apiKey)) {
            return Optional.empty();
        }
        return clientRepository.findByKeyHashAndRevokedAtIsNull(ApiKeys.hash(apiKey)).map(client -> {
            LocalDateTime now = LocalDateTime.now();
            if (client.getLastUsedAt() == null || client.getLastUsedAt().isBefore(now.minus(TOUCH_INTERVAL))) {
                clientRepository.touch(client.getId(), now);
            }
            return new ClientPrincipal(client.getId(), client.getName(), Set.of(client.getScopes().split(" ")));
        });
    }

    private Client findActive(String rawName) {
        String name = Names.normalize(rawName, "Client");
        return clientRepository.findByNameAndRevokedAtIsNull(name)
                .orElseThrow(() -> new ResourceNotFoundException("No active client found with the name " + name));
    }

    private static void setKey(Client client, String apiKey) {
        client.setKeyHash(ApiKeys.hash(apiKey));
        client.setKeyPrefix(ApiKeys.displayPrefix(apiKey));
    }

    private static ClientResponse toResponse(Client client, String apiKey) {
        return ClientResponse.builder()
                .id(client.getId())
                .name(client.getName())
                .scopes(List.of(client.getScopes().split(" ")))
                .keyPrefix(client.getKeyPrefix())
                .apiKey(apiKey)
                .createdAt(client.getCreatedAt())
                .lastUsedAt(client.getLastUsedAt())
                .build();
    }
}