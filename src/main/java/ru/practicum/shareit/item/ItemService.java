package ru.practicum.shareit.item;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.Status;
import ru.practicum.shareit.booking.dto.BookingShortDto;
import ru.practicum.shareit.booking.mapper.BookingMapper;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemWithBookingsDto;
import ru.practicum.shareit.item.mapper.CommentMapper;
import ru.practicum.shareit.item.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public ItemDto addItem(Long userId, ItemDto itemDto) {
        log.info("Add item by user {}: {}", userId, itemDto);
        User owner = getUserOrThrow(userId);
        Item item = ItemMapper.toItem(itemDto);
        item.setOwner(owner);
        return ItemMapper.toItemDto(itemRepository.save(item));
    }

    @Transactional
    public ItemDto updateItem(Long userId, Long itemId, ItemDto itemDto) {
        log.info("Update item {} by user {}: {}", itemId, userId, itemDto);
        getUserOrThrow(userId);
        Item item = getItemOrThrow(itemId);
        if (!item.getOwner().getId().equals(userId)) {
            throw new NotFoundException("Редактировать вещь может только её владелец");
        }
        if (itemDto.getName() != null && !itemDto.getName().isBlank()) {
            item.setName(itemDto.getName());
        }
        if (itemDto.getDescription() != null && !itemDto.getDescription().isBlank()) {
            item.setDescription(itemDto.getDescription());
        }
        if (itemDto.getAvailable() != null) {
            item.setAvailable(itemDto.getAvailable());
        }
        return ItemMapper.toItemDto(itemRepository.save(item));
    }

    public ItemWithBookingsDto getItemById(Long userId, Long itemId) {
        log.info("Get item {} by user {}", itemId, userId);
        Item item = getItemOrThrow(itemId);
        List<CommentDto> comments = commentRepository.findByItem_IdOrderByCreatedDesc(itemId).stream()
                .map(CommentMapper::toCommentDto)
                .toList();
        boolean isOwner = userId != null && item.getOwner().getId().equals(userId);
        if (!isOwner) {
            return ItemMapper.toItemWithBookingsDto(item, null, null, comments);
        }
        List<Booking> bookings = bookingRepository.findByItem_IdInAndStatus(Set.of(itemId), Status.APPROVED);
        LocalDateTime now = LocalDateTime.now();
        return ItemMapper.toItemWithBookingsDto(item, findLast(bookings, now), findNext(bookings, now), comments);
    }

    public List<ItemWithBookingsDto> getItemsByOwner(Long userId) {
        log.info("Get items of owner {}", userId);
        getUserOrThrow(userId);
        List<Item> items = itemRepository.findByOwner_IdOrderByIdAsc(userId);
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> itemIds = items.stream().map(Item::getId).collect(Collectors.toSet());

        Map<Long, List<Booking>> bookingsByItem = bookingRepository
                .findByItem_IdInAndStatus(itemIds, Status.APPROVED).stream()
                .collect(Collectors.groupingBy(b -> b.getItem().getId()));
        Map<Long, List<CommentDto>> commentsByItem = commentRepository
                .findByItem_IdInOrderByCreatedDesc(itemIds).stream()
                .collect(Collectors.groupingBy(c -> c.getItem().getId(),
                        Collectors.mapping(CommentMapper::toCommentDto, Collectors.toList())));

        LocalDateTime now = LocalDateTime.now();
        return items.stream()
                .map(item -> {
                    List<Booking> bookings = bookingsByItem.getOrDefault(item.getId(), List.of());
                    return ItemMapper.toItemWithBookingsDto(item,
                            findLast(bookings, now),
                            findNext(bookings, now),
                            commentsByItem.getOrDefault(item.getId(), List.of()));
                })
                .toList();
    }

    public List<ItemDto> searchItems(String text) {
        log.info("Search items by text: {}", text);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return itemRepository.search(text).stream()
                .map(ItemMapper::toItemDto)
                .toList();
    }

    @Transactional
    public CommentDto addComment(Long userId, Long itemId, CommentDto commentDto) {
        log.info("Add comment to item {} by user {}", itemId, userId);
        User author = getUserOrThrow(userId);
        Item item = getItemOrThrow(itemId);
        LocalDateTime now = LocalDateTime.now();
        boolean hasFinishedBooking = bookingRepository
                .existsByItem_IdAndBooker_IdAndStatusAndEndIsBefore(itemId, userId, Status.APPROVED, now);
        if (!hasFinishedBooking) {
            throw new ValidationException("Оставить отзыв можно только после завершённой аренды этой вещи");
        }
        Comment comment = new Comment();
        comment.setText(commentDto.getText());
        comment.setItem(item);
        comment.setAuthor(author);
        comment.setCreated(now);
        return CommentMapper.toCommentDto(commentRepository.save(comment));
    }

    private BookingShortDto findLast(List<Booking> bookings, LocalDateTime now) {
        return bookings.stream()
                .filter(b -> b.getStart().isBefore(now))
                .max(Comparator.comparing(Booking::getStart))
                .map(BookingMapper::toBookingShortDto)
                .orElse(null);
    }

    private BookingShortDto findNext(List<Booking> bookings, LocalDateTime now) {
        return bookings.stream()
                .filter(b -> b.getStart().isAfter(now))
                .min(Comparator.comparing(Booking::getStart))
                .map(BookingMapper::toBookingShortDto)
                .orElse(null);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь с id " + userId + " не найден"));
    }

    private Item getItemOrThrow(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Вещь с id " + itemId + " не найдена"));
    }
}
