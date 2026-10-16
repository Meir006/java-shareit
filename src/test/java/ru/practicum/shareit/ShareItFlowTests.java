package ru.practicum.shareit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.Status;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareItFlowTests {
    private static final String H = "X-Sharer-User-Id";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;

    private ResultActions send(MockHttpServletRequestBuilder b, Long userId, String body) throws Exception {
        if (userId != null) {
            b.header(H, userId);
        }
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(b);
    }

    private long id(ResultActions r) throws Exception {
        JsonNode n = om.readTree(r.andReturn().getResponse().getContentAsString());
        return n.get("id").asLong();
    }

    private long createUser(String name, String email) throws Exception {
        return id(send(post("/users"), null,
                "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}").andExpect(status().isOk()));
    }

    private long createItem(long owner, String name, String desc) throws Exception {
        return id(send(post("/items"), owner,
                "{\"name\":\"" + name + "\",\"description\":\"" + desc + "\",\"available\":true}")
                .andExpect(status().isOk()));
    }

    private String bookingJson(long itemId, LocalDateTime start, LocalDateTime end) {
        return "{\"itemId\":" + itemId + ",\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
    }

    @Test
    void usersAndItemsCrudAndSearch() throws Exception {
        long u1 = createUser("Ann", "ann@x.com");
        send(post("/users"), null, "{\"name\":\"Bob\",\"email\":\"ann@x.com\"}")
                .andExpect(status().isConflict());
        send(patch("/users/" + u1), null, "{\"email\":\"ann@x.com\",\"name\":\"Anna\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", is("Anna")));
        send(post("/users"), null, "{\"name\":\"\",\"email\":\"bad\"}").andExpect(status().isBadRequest());

        long item = createItem(u1, "Дрель", "Аккумуляторная дрель");
        send(post("/items"), u1, "{\"name\":\"\",\"description\":\"d\",\"available\":true}")
                .andExpect(status().isBadRequest());
        send(get("/items/search").param("text", "ДРЕЛ"), u1, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        send(patch("/items/" + item), u1, "{\"available\":false}").andExpect(status().isOk());
        send(get("/items/search").param("text", "дрель"), u1, null)
                .andExpect(jsonPath("$", hasSize(0)));
        send(get("/items/search").param("text", " "), u1, null).andExpect(jsonPath("$", hasSize(0)));
        long u2 = createUser("Bob", "bob@x.com");
        send(patch("/items/" + item), u2, "{\"name\":\"x\"}").andExpect(status().isNotFound());
        send(delete("/users/" + u1), null, null).andExpect(status().isOk());
        send(get("/users/" + u1), null, null).andExpect(status().isNotFound());
    }

    @Test
    void bookingLifecycleAndStates() throws Exception {
        long owner = createUser("Owner", "o@x.com");
        long booker = createUser("Booker", "b@x.com");
        long item = createItem(owner, "Лодка", "Надувная лодка");
        LocalDateTime now = LocalDateTime.now();

        send(post("/bookings"), booker, bookingJson(item, now.minusDays(1), now.plusDays(1)))
                .andExpect(status().isBadRequest());
        send(post("/bookings"), booker, bookingJson(item, now.plusDays(2), now.plusDays(1)))
                .andExpect(status().isBadRequest());
        send(post("/bookings"), owner, bookingJson(item, now.plusDays(1), now.plusDays(2)))
                .andExpect(status().isNotFound());
        send(post("/bookings"), 999L, bookingJson(item, now.plusDays(1), now.plusDays(2)))
                .andExpect(status().isNotFound());

        long b1 = id(send(post("/bookings"), booker, bookingJson(item, now.plusDays(1), now.plusDays(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("WAITING")))
                .andExpect(jsonPath("$.item.id", is((int) item)))
                .andExpect(jsonPath("$.booker.id", is((int) booker))));
        long b2 = id(send(post("/bookings"), booker, bookingJson(item, now.plusDays(3), now.plusDays(4)))
                .andExpect(status().isOk()));

        send(patch("/bookings/" + b1).param("approved", "true"), booker, null).andExpect(status().isForbidden());
        send(patch("/bookings/" + b1).param("approved", "true"), owner, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("APPROVED")));
        send(patch("/bookings/" + b1).param("approved", "false"), owner, null)
                .andExpect(status().isBadRequest());
        send(patch("/bookings/" + b2).param("approved", "false"), owner, null)
                .andExpect(jsonPath("$.status", is("REJECTED")));

        long stranger = createUser("Eve", "e@x.com");
        send(get("/bookings/" + b1), stranger, null).andExpect(status().isNotFound());
        send(get("/bookings/" + b1), owner, null).andExpect(status().isOk());
        send(get("/bookings/" + b1), booker, null).andExpect(status().isOk());

        send(get("/bookings"), booker, null).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is((int) b2)));
        send(get("/bookings").param("state", "FUTURE"), booker, null).andExpect(jsonPath("$", hasSize(2)));
        send(get("/bookings").param("state", "REJECTED"), booker, null).andExpect(jsonPath("$", hasSize(1)));
        send(get("/bookings").param("state", "WAITING"), booker, null).andExpect(jsonPath("$", hasSize(0)));
        send(get("/bookings").param("state", "PAST"), booker, null).andExpect(jsonPath("$", hasSize(0)));
        send(get("/bookings").param("state", "CURRENT"), booker, null).andExpect(jsonPath("$", hasSize(0)));
        send(get("/bookings").param("state", "UNSUPPORTED_STATUS"), booker, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Unknown state: UNSUPPORTED_STATUS")));
        send(get("/bookings/owner"), owner, null).andExpect(jsonPath("$", hasSize(2)));
        send(get("/bookings/owner").param("state", "REJECTED"), owner, null)
                .andExpect(jsonPath("$", hasSize(1)));
        send(get("/bookings/owner"), booker, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void lastNextBookingsAndComments() throws Exception {
        long owner = createUser("Owner", "o2@x.com");
        long booker = createUser("Booker", "b2@x.com");
        long item = createItem(owner, "Палатка", "Туристическая палатка");
        var itemEntity = itemRepository.findById(item).orElseThrow();
        var bookerEntity = userRepository.findById(booker).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        send(post("/items/" + item + "/comment"), booker, "{\"text\":\"Супер\"}")
                .andExpect(status().isBadRequest());

        Booking past = new Booking(null, now.minusDays(3), now.minusDays(2), itemEntity, bookerEntity,
                Status.APPROVED);
        bookingRepository.save(past);
        long next = id(send(post("/bookings"), booker, bookingJson(item, now.plusDays(1), now.plusDays(2)))
                .andExpect(status().isOk()));
        send(get("/items/" + item), owner, null)
                .andExpect(jsonPath("$.lastBooking.id", is(past.getId().intValue())))
                .andExpect(jsonPath("$.lastBooking.bookerId", is((int) booker)))
                .andExpect(jsonPath("$.nextBooking", nullValue()));
        send(patch("/bookings/" + next).param("approved", "true"), owner, null).andExpect(status().isOk());
        send(get("/items"), owner, null)
                .andExpect(jsonPath("$[0].nextBooking.id", is((int) next)))
                .andExpect(jsonPath("$[0].lastBooking.id", is(past.getId().intValue())));
        send(get("/items/" + item), booker, null)
                .andExpect(jsonPath("$.lastBooking", nullValue()))
                .andExpect(jsonPath("$.nextBooking", nullValue()));

        send(post("/items/" + item + "/comment"), booker, "{\"text\":\"\"}").andExpect(status().isBadRequest());
        send(post("/items/" + item + "/comment"), owner, "{\"text\":\"Сам\"}").andExpect(status().isBadRequest());
        send(post("/items/" + item + "/comment"), booker, "{\"text\":\"Супер\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName", is("Booker")))
                .andExpect(jsonPath("$.created", notNullValue()));
        send(get("/items/" + item), owner, null).andExpect(jsonPath("$.comments", hasSize(1)));
        send(get("/items"), owner, null).andExpect(jsonPath("$[0].comments", hasSize(1)));
    }
}
