package pl.football.seeder.random;

import net.datafaker.Faker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * The single source of randomness. Every Faker in the seeder shares {@link #random}, so one seed
 * decides everything. Plain picks and numbers go through Faker; only what Faker has no generator
 * for (Poisson, Gaussian, weighted choice) uses the Random directly.
 */
final class Rng {

    final Random random;
    final Faker faker;

    Rng(long seed) {
        random = new Random(seed);
        faker = new Faker(Locale.ENGLISH, random);
    }

    /** Inclusive on both ends. */
    int between(int min, int max) {
        return min == max ? min : faker.number().numberBetween(min, max + 1);
    }

    boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    <T> T pick(List<T> items) {
        return faker.options().nextElement(items);
    }

    <T> List<T> shuffled(List<T> items) {
        List<T> copy = new ArrayList<>(items);
        Collections.shuffle(copy, random);
        return copy;
    }

    <T> List<T> sample(List<T> items, int count) {
        return new ArrayList<>(shuffled(items).subList(0, Math.min(count, items.size())));
    }

    double gaussian(double mean, double deviation) {
        return mean + random.nextGaussian() * deviation;
    }

    int poisson(double lambda) {
        double limit = Math.exp(-lambda);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            product *= random.nextDouble();
            count++;
        }
        return count;
    }

    <T> T weighted(List<T> items, ToDoubleFunction<T> weight) {
        double total = items.stream().mapToDouble(weight).sum();
        double roll = random.nextDouble() * total;
        for (T item : items) {
            roll -= weight.applyAsDouble(item);
            if (roll <= 0) {
                return item;
            }
        }
        return items.getLast();
    }

    LocalDate dateBetween(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return LocalDate.ofInstant(faker.timeAndDate().between(start, end), ZoneOffset.UTC);
    }

    /** A birth date making the person between the given ages on the given day. */
    LocalDate birthDate(LocalDate on, int minAge, int maxAge) {
        return dateBetween(on.minusYears(maxAge + 1L).plusDays(1), on.minusYears(minAge));
    }
}
