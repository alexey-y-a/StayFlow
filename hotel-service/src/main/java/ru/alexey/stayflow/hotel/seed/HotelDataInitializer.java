package ru.alexey.stayflow.hotel.seed;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.alexey.stayflow.hotel.model.Hotel;
import ru.alexey.stayflow.hotel.model.Room;
import ru.alexey.stayflow.hotel.model.RoomType;
import ru.alexey.stayflow.hotel.repository.HotelRepository;
import ru.alexey.stayflow.hotel.repository.RoomRepository;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stayflow.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HotelDataInitializer implements ApplicationRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hotelRepository.count() > 0) {
            return;
        }

        Hotel grandStay =
                hotelRepository.save(hotel("Grand Stay", "Москва", "ул. Тверская, 1", "Отель в центре Москвы", "4.8"));
        roomRepository.save(room(grandStay, "101", RoomType.DOUBLE, 2, "6500.00", 1, 4, true, "Стандартный номер"));
        roomRepository.save(
                room(grandStay, "102", RoomType.DOUBLE, 2, "6800.00", 1, 7, true, "Номер с видом на город"));
        roomRepository.save(room(grandStay, "201", RoomType.SUITE, 3, "12500.00", 2, 4, true, "Люкс"));
        roomRepository.save(room(grandStay, "202", RoomType.SINGLE, 1, "4200.00", 2, 0, true, "Одноместный номер"));

        Hotel seaBreeze =
                hotelRepository.save(hotel("Sea Breeze", "Сочи", "ул. Приморская, 10", "Отель у моря", "4.6"));
        roomRepository.save(room(seaBreeze, "301", RoomType.DOUBLE, 2, "7200.00", 3, 5, true, "Номер с балконом"));
        roomRepository.save(room(seaBreeze, "302", RoomType.FAMILY, 4, "9800.00", 3, 1, true, "Семейный номер"));
        roomRepository.save(room(seaBreeze, "401", RoomType.SUITE, 4, "15000.00", 4, 3, true, "Люкс с террасой"));
        roomRepository.save(room(seaBreeze, "402", RoomType.SINGLE, 1, "4800.00", 4, 0, false, "На ремонте"));

        Hotel alpineLodge = hotelRepository.save(
                hotel("Alpine Lodge", "Казань", "ул. Кремлевская, 5", "Отель в историческом центре", "4.9"));
        roomRepository.save(room(alpineLodge, "501", RoomType.DOUBLE, 2, "5900.00", 5, 6, true, "Стандартный номер"));
        roomRepository.save(
                room(alpineLodge, "502", RoomType.TWIN, 2, "6100.00", 5, 2, true, "Номер с двумя кроватями"));
        roomRepository.save(room(alpineLodge, "601", RoomType.FAMILY, 5, "11000.00", 6, 0, true, "Семейный люкс"));
        roomRepository.save(room(alpineLodge, "602", RoomType.SUITE, 4, "14000.00", 6, 1, true, "Люкс"));
    }

    private Hotel hotel(String name, String city, String address, String description, String rating) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setCity(city);
        hotel.setAddress(address);
        hotel.setDescription(description);
        hotel.setRating(new BigDecimal(rating));
        return hotel;
    }

    private Room room(
            Hotel hotel,
            String number,
            RoomType type,
            int capacity,
            String price,
            int floor,
            long timesBooked,
            boolean available,
            String description) {
        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(number);
        room.setType(type);
        room.setCapacity(capacity);
        room.setPricePerNight(new BigDecimal(price));
        room.setFloor(floor);
        room.setTimesBooked(timesBooked);
        room.setAvailable(available);
        room.setDescription(description);
        return room;
    }
}
