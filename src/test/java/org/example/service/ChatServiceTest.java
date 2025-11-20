package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ChatMessage;
import org.example.model.ChatAggregate;
import org.example.repo.ChatAggregateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatAggregateRepository repo;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ChatService chatService;

    private ChatAggregate buildAggregate(String json) {
        ChatAggregate ag = new ChatAggregate();
        ag.setId(1L);
        ag.setEntriesJson(json);
        ag.setUpdatedAt(LocalDateTime.now());
        return ag;
    }

    private ChatService.Entry entry(Long id, String pseudo, String contenu, LocalDateTime date) {
        ChatService.Entry e = new ChatService.Entry();
        e.id = id;
        e.pseudo = pseudo;
        e.contenu = contenu;
        e.date = date;
        return e;
    }

    // --- getAll() ---

    @Test
    void getAll_shouldReturnMessagesSortedByDateAscending() throws Exception {
        ChatAggregate ag = buildAggregate("json");
        when(repo.findById(1L)).thenReturn(Optional.of(ag));

        List<ChatService.Entry> entries = new ArrayList<>();
        entries.add(entry(2L, "Bob", "yo", LocalDateTime.now().plusMinutes(1)));
        entries.add(entry(1L, "Alice", "salut", LocalDateTime.now()));

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(entries);

        List<ChatMessage> result = chatService.getAll();

        assertThat(result).hasSize(2);
        // tri par date croissante => Alice puis Bob
        assertThat(result.get(0).getPseudo()).isEqualTo("Alice");
        assertThat(result.get(1).getPseudo()).isEqualTo("Bob");
    }

    // --- save() validation ---

    @Test
    void save_shouldThrowWhenMessageIsNullEmptyOrTooLong() {
        assertThrows(IllegalArgumentException.class,
                () -> chatService.save("User", null));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.save("User", "   "));

        String longMsg = "a".repeat(151);
        assertThrows(IllegalArgumentException.class,
                () -> chatService.save("User", longMsg));

        verifyNoInteractions(repo);
    }

    @Test
    void save_shouldCreateAggregateIfNotExists_andSanitizeAndTruncate() throws Exception {
        // Aucun aggregate -> getOrCreateAggregate crée et save un nouveau
        when(repo.findById(1L)).thenReturn(Optional.empty());
        when(repo.save(any(ChatAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // On simule une liste déjà pleine de 500 messages
        List<ChatService.Entry> existing = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            existing.add(entry((long) i, "P" + i, "msg" + i, LocalDateTime.now().minusMinutes(500 - i)));
        }

        // 1er appel de readValue (dans save) -> renvoie existing
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(existing);

        // On capture la liste écrite en JSON pour vérifier le contenu
        AtomicReference<List<ChatService.Entry>> lastListRef = new AtomicReference<>();
        when(objectMapper.writeValueAsString(any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<ChatService.Entry> list = invocation.getArgument(0);
                    lastListRef.set(list);
                    return "json-saved";
                });

        String contenu = "Salut fdp ça va ?";
        ChatMessage savedMsg = chatService.save("User", contenu);

        // On a bien sauvegardé un aggregate
        verify(repo, atLeastOnce()).save(any(ChatAggregate.class));

        List<ChatService.Entry> finalList = lastListRef.get();
        assertThat(finalList).isNotNull();
        // tronqué à MAX_MESSAGES (500)
        assertThat(finalList).hasSize(500);

        ChatService.Entry first = finalList.get(0);
        // le nouveau message est en tête
        assertThat(first.pseudo).isEqualTo("User");
        // insulte censurée
        assertThat(first.contenu).doesNotContain("fdp");
        assertThat(first.contenu).contains("***");

        // l'objet retourné correspond à l'entrée
        assertThat(savedMsg.getId()).isEqualTo(first.id);
        assertThat(savedMsg.getPseudo()).isEqualTo(first.pseudo);
        assertThat(savedMsg.getContenu()).isEqualTo(first.contenu);
    }

    // --- clear() ---

    @Test
    void clear_shouldEmptyAggregateAndSave() {
        ChatAggregate ag = buildAggregate("[{\"foo\":\"bar\"}]");
        when(repo.findById(1L)).thenReturn(Optional.of(ag));
        when(repo.save(any(ChatAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatService.clear();

        assertThat(ag.getEntriesJson()).isEqualTo("[]");
        assertThat(ag.getUpdatedAt()).isNotNull();
        verify(repo).save(ag);
    }

    // --- autoClear() ---

    @Test
    void autoClear_shouldEmptyAggregateAndSave() {
        ChatAggregate ag = buildAggregate("[{\"foo\":\"bar\"}]");
        when(repo.findById(1L)).thenReturn(Optional.of(ag));
        when(repo.save(any(ChatAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        chatService.autoClear();

        assertThat(ag.getEntriesJson()).isEqualTo("[]");
        verify(repo).save(ag);
    }

    // --- deleteById() ---

    @Test
    void deleteById_shouldRemoveEntryAndSave_whenIdExists() throws Exception {
        ChatAggregate ag = buildAggregate("json");
        when(repo.findById(1L)).thenReturn(Optional.of(ag));
        when(repo.save(any(ChatAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatService.Entry e1 = entry(1L, "Alice", "hey", LocalDateTime.now());
        ChatService.Entry e2 = entry(2L, "Bob", "yo", LocalDateTime.now());

        List<ChatService.Entry> entries = new ArrayList<>(List.of(e1, e2));
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(entries);

        AtomicReference<List<ChatService.Entry>> lastListRef = new AtomicReference<>();
        when(objectMapper.writeValueAsString(any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<ChatService.Entry> list = invocation.getArgument(0);
                    lastListRef.set(list);
                    return "json-new";
                });

        chatService.deleteById(1L);

        List<ChatService.Entry> finalList = lastListRef.get();
        assertThat(finalList).hasSize(1);
        assertThat(finalList.get(0).id).isEqualTo(2L);
        verify(repo).save(ag);
    }

    @Test
    void deleteById_shouldDoNothing_whenIdNotFound() throws Exception {
        ChatAggregate ag = buildAggregate("json");
        when(repo.findById(1L)).thenReturn(Optional.of(ag));

        ChatService.Entry e1 = entry(1L, "Alice", "hey", LocalDateTime.now());
        List<ChatService.Entry> entries = new ArrayList<>(List.of(e1));
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(entries);

        chatService.deleteById(999L);

        // pas de save si rien n’a changé
        verify(repo, never()).save(any(ChatAggregate.class));
        verify(objectMapper, never()).writeValueAsString(any());
    }
}
