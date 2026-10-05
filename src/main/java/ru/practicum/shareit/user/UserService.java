
package ru.practicum.shareit.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.mapper.UserMapper;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public UserDto addUser(UserDto dto) {
        log.info("Add user: {}", dto);
        if (userRepository.isEmailTaken(dto.getEmail(), null)) {
            throw new ConflictException("Email уже используется");
        }
        User user = userRepository.add(UserMapper.toUser(dto));
        return UserMapper.toUserDto(user);
    }

    public UserDto updateUser(Long id, UserDto dto) {
        log.info("Update user {}: {}", id, dto);
        User user = userRepository.getById(id);
        if (user == null) {
            throw new NotFoundException("Пользователь с id " + id + " не найден");
        }
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        if (dto.getEmail() != null) {
            if (userRepository.isEmailTaken(dto.getEmail(), id)) {
                throw new ConflictException("Email уже используется");
            }
            user.setEmail(dto.getEmail());
        }
        return UserMapper.toUserDto(userRepository.update(user));
    }

    public void deleteUser(Long id) {
        log.info("Delete user: {}", id);
        userRepository.delete(id);
    }

    public UserDto getUserById(Long id) {
        log.info("Get user by id: {}", id);
        User user = userRepository.getById(id);
        if (user == null) {
            throw new NotFoundException("Пользователь с id " + id + " не найден");
        }
        return UserMapper.toUserDto(user);
    }

    public List<UserDto> getAllUsers() {
        log.info("Get all users");
        return userRepository.getAll().stream()
                .map(UserMapper::toUserDto)
                .toList();
    }
}