package edu.cmu.cs214.lab10.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.cmu.cs214.lab10.domain.Room;
import edu.cmu.cs214.lab10.service.BookingService;
import edu.cmu.cs214.lab10.store.InMemoryBookingStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class RaceTest {

    private static final String ROOM = "WEH-5409";
    private static final List<Room> ROOMS = List.of(
            new Room(ROOM, "Wean 5409", 12),
            new Room("GHC-4301", "Gates 4301", 30));

    @Test
    void twoConcurrentBookingsForSameSlotProduceOnlyOneConfirmed() throws InterruptedException {
        InMemoryBookingStore real = new InMemoryBookingStore();
        PausingStore gated = new PausingStore(real, "hasOverlap", 1);
        BookingService service = new BookingService(gated, ROOMS);

        Thread threadA = new Thread(() -> service.book(ROOM, 540, 600), "thread-A");
        threadA.start();

        gated.awaitPaused();

        Thread threadB = new Thread(() -> service.book(ROOM, 540, 600), "thread-B");
        threadB.start();
        threadB.join(PausingStore.TIMEOUT_MILLIS);

        gated.release();
        threadA.join(PausingStore.TIMEOUT_MILLIS);

        assertEquals(1, real.bookingsForRoom(ROOM).size());
    }

    @Test
    void twoConcurrentBookingsBreaksTeammatePatch() throws InterruptedException {
        InMemoryBookingStore real = new InMemoryBookingStore();
        PausingStore gated = new PausingStore(real, "hasOverlap", 2);
        BookingService service = new BookingService(gated, ROOMS);

        Thread threadA = new Thread(() -> service.book(ROOM, 540, 600), "thread-A");
        threadA.start();

        gated.awaitPaused();

        Thread threadB = new Thread(() -> service.book(ROOM, 540, 600), "thread-B");
        threadB.start();
        threadB.join(PausingStore.TIMEOUT_MILLIS);

        gated.release();
        threadA.join(PausingStore.TIMEOUT_MILLIS);

        assertEquals(1, real.bookingsForRoom(ROOM).size());
    }
}
