package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.model.Friend;
import org.example.service.FriendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService service;

    @GetMapping
    public ResponseEntity<List<Friend>> list(Principal principal) {
        return ResponseEntity.ok(service.listFriends(principal.getName()));
    }

    // ✅ envoyer une invitation
    @PostMapping("/add")
    public ResponseEntity<?> add(@RequestBody Map<String, String> body, Principal principal) {
        String friendEmail = body.get("email");
        return ResponseEntity.ok(service.sendRequest(principal.getName(), friendEmail));
    }

    // ✅ voir mes invitations reçues
    @GetMapping("/requests")
    public ResponseEntity<?> requests(Principal principal) {
        return ResponseEntity.ok(service.getRequests(principal.getName()));
    }

    // ✅ accepter
    @PostMapping("/accept/{id}")
    public ResponseEntity<?> accept(@PathVariable Long id, Principal principal) {
        service.accept(id, principal.getName());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ✅ refuser
    @PostMapping("/refuse/{id}")
    public ResponseEntity<?> refuse(@PathVariable Long id, Principal principal) {
        service.refuse(id, principal.getName());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ✅ status online
    @PostMapping("/status")
    public ResponseEntity<?> setOnline(@RequestBody Map<String, Boolean> body, Principal principal) {
        if (principal != null && body.containsKey("online")) {
            service.setOnline(principal.getName(), body.get("online"));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }


    // ✅ crédits
    @PostMapping("/send-credits")
    public ResponseEntity<?> sendCredits(@RequestBody Map<String, Object> body, Principal principal) {
        String toEmail = (String) body.get("to");
        long amount = ((Number) body.get("amount")).longValue();
        service.sendCredits(principal.getName(), toEmail, amount);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
