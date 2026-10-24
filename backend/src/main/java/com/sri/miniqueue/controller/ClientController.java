package com.sri.miniqueue.controller;

import com.sri.miniqueue.dto.ClientResponse;
import com.sri.miniqueue.dto.CreateClientRequest;
import com.sri.miniqueue.dto.Response;
import com.sri.miniqueue.mapper.ResponseBuilder;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.utils.APIConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Registering the applications that use the broker. Admin only (see SecurityConfig). */
@RestController
@RequiredArgsConstructor
@RequestMapping(APIConstants.CLIENTS)
public class ClientController {

    private final ClientService clientService;

    // POST /api/v1/clients   { "name": "email-service", "scopes": ["consume:email-queue"] }
    // The response contains the api_key. It is shown this once and cannot be retrieved again.
    @PostMapping
    public Response<ClientResponse> createClient(@RequestBody CreateClientRequest request) {
        return ResponseBuilder.createSuccessData(clientService.createClient(request.getName(), request.getScopes()));
    }

    // GET /api/v1/clients   (active clients; prefixes only, never keys)
    @GetMapping
    public Response<List<ClientResponse>> getAllClients() {
        return ResponseBuilder.createSuccessData(clientService.getAllClients());
    }

    // POST /api/v1/clients/{name}/rotate   new key; the old one stops working immediately
    @PostMapping("/{name}/rotate")
    public Response<ClientResponse> rotateKey(@PathVariable String name) {
        return ResponseBuilder.createSuccessData(clientService.rotateKey(name));
    }

    // DELETE /api/v1/clients/{name}
    @DeleteMapping("/{name}")
    public Response<String> revokeClient(@PathVariable String name) {
        return ResponseBuilder.createSuccessData(clientService.revokeClient(name));
    }
}