package ru.practicum.shareit.user;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Repository
public class UserRepository {
    private final Map<Long, User> users = new HashMap<>();
    private Long idCounter = 0L;

    public User add(User user) {
        user.setId(++idCounter);
        users.put(user.getId(), user);
        return user;
    }


    public User update(User user) {
        users.put(user.getId(), user);
        return user;
    }

    public User delete(Long id) {
        return users.remove(id);
    }

    public User getById(Long id) {
        return users.get(id);
    }

    public Collection<User> getAll() {
        return users.values();
    }

    public boolean isEmailTaken(String email, Long excludedUserId) {
        return users.values().stream().anyMatch(u -> u.getEmail().equals(email) && !u.getId().equals(excludedUserId));
    }
}
