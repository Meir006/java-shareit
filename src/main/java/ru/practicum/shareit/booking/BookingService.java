package ru.practicum.shareit.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.dto.BookingCreateDto;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.mapper.BookingMapper;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "start");

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    @Transactional
    public BookingDto addBooking(Long userId, BookingCreateDto dto) {
        log.info("Add booking by user {}: {}", userId, dto);
        User booker = getUserOrThrow(userId);
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new NotFoundException("Вещь с id " + dto.getItemId() + " не найдена"));
        if (!Boolean.TRUE.equals(item.getAvailable())) {
            throw new ValidationException("Вещь недоступна для бронирования");
        }
        if (item.getOwner().getId().equals(userId)) {
            throw new NotFoundException("Владелец не может бронировать свою вещь");
        }
        if (!dto.getEnd().isAfter(dto.getStart())) {
            throw new ValidationException("Дата окончания должна быть позже даты начала");
        }
        Booking booking = bookingRepository.save(BookingMapper.toBooking(dto, item, booker));
        return BookingMapper.toBookingDto(booking);
    }

    @Transactional
    public BookingDto approve(Long userId, Long bookingId, boolean approved) {
        log.info("Approve booking {} by user {}: {}", bookingId, userId, approved);
        Booking booking = getBookingOrThrow(bookingId);
        if (!booking.getItem().getOwner().getId().equals(userId)) {
            throw new NotFoundException("Подтвердить бронирование может только владелец вещи");
        }
        if (booking.getStatus() != Status.WAITING) {
            throw new ValidationException("Статус бронирования уже изменён");
        }
        booking.setStatus(approved ? Status.APPROVED : Status.REJECTED);
        return BookingMapper.toBookingDto(bookingRepository.save(booking));
    }

    public BookingDto getById(Long userId, Long bookingId) {
        log.info("Get booking {} by user {}", bookingId, userId);
        getUserOrThrow(userId);
        Booking booking = getBookingOrThrow(bookingId);
        boolean isBooker = booking.getBooker().getId().equals(userId);
        boolean isOwner = booking.getItem().getOwner().getId().equals(userId);
        if (!isBooker && !isOwner) {
            throw new NotFoundException("Бронирование доступно только автору и владельцу вещи");
        }
        return BookingMapper.toBookingDto(booking);
    }

    public List<BookingDto> getByBooker(Long userId, String state) {
        log.info("Get bookings of booker {}, state {}", userId, state);
        BookingState bookingState = BookingState.from(state);
        getUserOrThrow(userId);
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookings = switch (bookingState) {
            case ALL -> bookingRepository.findByBooker_Id(userId, NEWEST_FIRST);
            case CURRENT -> bookingRepository
                    .findByBooker_IdAndStartIsBeforeAndEndIsAfter(userId, now, now, NEWEST_FIRST);
            case PAST -> bookingRepository.findByBooker_IdAndEndIsBefore(userId, now, NEWEST_FIRST);
            case FUTURE -> bookingRepository.findByBooker_IdAndStartIsAfter(userId, now, NEWEST_FIRST);
            case WAITING -> bookingRepository.findByBooker_IdAndStatus(userId, Status.WAITING, NEWEST_FIRST);
            case REJECTED -> bookingRepository.findByBooker_IdAndStatus(userId, Status.REJECTED, NEWEST_FIRST);
        };
        return bookings.stream().map(BookingMapper::toBookingDto).toList();
    }

    public List<BookingDto> getByOwner(Long userId, String state) {
        log.info("Get bookings of owner {}, state {}", userId, state);
        BookingState bookingState = BookingState.from(state);
        getUserOrThrow(userId);
        LocalDateTime now = LocalDateTime.now();
        List<Booking> bookings = switch (bookingState) {
            case ALL -> bookingRepository.findByItem_Owner_Id(userId, NEWEST_FIRST);
            case CURRENT -> bookingRepository
                    .findByItem_Owner_IdAndStartIsBeforeAndEndIsAfter(userId, now, now, NEWEST_FIRST);
            case PAST -> bookingRepository.findByItem_Owner_IdAndEndIsBefore(userId, now, NEWEST_FIRST);
            case FUTURE -> bookingRepository.findByItem_Owner_IdAndStartIsAfter(userId, now, NEWEST_FIRST);
            case WAITING -> bookingRepository.findByItem_Owner_IdAndStatus(userId, Status.WAITING, NEWEST_FIRST);
            case REJECTED -> bookingRepository.findByItem_Owner_IdAndStatus(userId, Status.REJECTED, NEWEST_FIRST);
        };
        return bookings.stream().map(BookingMapper::toBookingDto).toList();
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id " + userId + " не найден"));
    }

    private Booking getBookingOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Бронирование с id " + bookingId + " не найдено"));
    }
}
