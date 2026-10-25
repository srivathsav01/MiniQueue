package com.sri.miniqueue.service;

import com.sri.miniqueue.dto.ClientResponse;
import com.sri.miniqueue.security.ClientPrincipal;

import java.util.List;
import java.util.Optional;

public interface ClientService {

    ClientResponse createClient(String name, List<String> scopes);

    List<ClientResponse> getAllClients();

    ClientResponse rotateKey(String name);

    String revokeClient(String name);

    Optional<ClientPrincipal> authenticate(String apiKey);

}