package pl.football.seeder.random;

import net.datafaker.Faker;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/** The only source of randomness. Every Faker shares {@link #random}, so one seed decides everything. */
final class Rng {

    final Random random;
    final Faker faker;

    Rng(long seed) {
        random = new Random(seed);
        faker = new Faker(Locale.ENGLISH, random);
    }

    /** Inclusive on both ends. */
    int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    <T> List<T> shuffled(Collection<T> items) {
        List<T> copy = new ArrayList<>(items);
        Collections.shuffle(copy, random);
        return copy;
    }

    /** Up to {@code count} different items in random order. */
    <T> List<T> sample(List<T> items, int count) {
        return new ArrayList<>(shuffled(items).subList(0, Math.min(count, items.size())));
    }

    LocalDate dateBetween(LocalDate from, LocalDate to) {
        return from.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(from, to) + 1));
    }

    double gaussian(double mean, double deviation) {
        return mean + random.nextGaussian() * deviation;
    }

    /** Knuth's method; fine for the small averages used here. */
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

    /** Picks an item with probability proportional to its weight. */
    <T> T weighted(List<T> items, ToDoubleFunction<T> weight) {
        double roll = random.nextDouble() * items.stream().mapToDouble(weight).sum();
        for (T item : items) {
            roll -= weight.applyAsDouble(item);
            if (roll <= 0) {
                return item;
            }
        }
        return items.getLast();
    }
}
