package ru.practicum.shareit.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.mapper.UserMapper;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public UserDto addUser(UserDto dto) {
        log.info("Add user: {}", dto);
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new ConflictException("Email уже используется");
        }
        User user = userRepository.save(UserMapper.toUser(dto));
        return UserMapper.toUserDto(user);
    }

    @Transactional
    public UserDto updateUser(Long id, UserDto dto) {
        log.info("Update user {}: {}", id, dto);
        User user = getUserOrThrow(id);
        if (dto.getName() != null && !dto.getName().isBlank()) {
            user.setName(dto.getName());
        }
        if (dto.getEmail() != null) {
            userRepository.findByEmail(dto.getEmail())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ConflictException("Email уже используется");
                    });
            user.setEmail(dto.getEmail());
        }
        return UserMapper.toUserDto(userRepository.saveAndFlush(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        log.info("Delete user: {}", id);
        userRepository.deleteById(id);
    }

    public UserDto getUserById(Long id) {
        log.info("Get user by id: {}", id);
        return UserMapper.toUserDto(getUserOrThrow(id));
    }

    public List<UserDto> getAllUsers() {
        log.info("Get all users");
        return userRepository.findAll().stream().map(UserMapper::toUserDto).toList();
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id " + id + " не найден"));
    }
}
