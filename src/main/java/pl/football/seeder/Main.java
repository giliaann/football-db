package pl.football.seeder;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import jakarta.persistence.metamodel.EntityType;

import java.util.Comparator;
import java.util.Map;

/** Inserts the test data set into an empty database, in a single transaction. */
public final class Main {

    private static final String RANDOM = "random";
    private static final long DEFAULT_SEED = 42L;

    private Main() {
    }

    private static long seedFrom(String[] args) {
        return args.length > 1 ? Long.parseLong(args[1]) : DEFAULT_SEED;
    }

    public static void main(String[] args) {
        Map<String, String> connection;
        try {
            connection = connectionProperties();
        } catch (IllegalStateException e) {
            fail(e.getMessage());
            return;
        }

        boolean seeded;
        try (EntityManagerFactory emf = Persistence.createEntityManagerFactory("football", connection);
             EntityManager em = emf.createEntityManager()) {
            seeded = isDatabaseEmpty(em);
            if (seeded) {
                seed(em, args);
                printRowCounts(em, emf);
            }
        }

        if (!seeded) {
            fail("The Editor table already contains data, so nothing was changed. "
                    + "To start over, reset the database with: docker compose down -v");
        }
    }

    /** Environment variables take precedence over the .env file in the working directory. */
    private static Map<String, String> connectionProperties() {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();
        String url = "jdbc:postgresql://%s:%s/%s".formatted(
                env.get("DB_HOST", "localhost"), env.get("DB_PORT", "5432"), required(env, "POSTGRES_DB"));
        return Map.of(
                "jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", env.get("DB_USER", "postgres"),
                "jakarta.persistence.jdbc.password", required(env, "POSTGRES_PASSWORD"));
    }

    private static String required(Dotenv env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set. Add it to .env or the environment.");
        }
        return value;
    }

    private static boolean isDatabaseEmpty(EntityManager em) {
        return em.createQuery("select count(e) from Editor e", Long.class).getSingleResult() == 0;
    }

    private static void seed(EntityManager em, String[] args) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            if (args.length > 0 && args[0].equals(RANDOM)) {
                new DataSeeder(em, seedFrom(args)).seedAll();
            } else {
                new TestDataSeeder(em).seedAll();
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private static void printRowCounts(EntityManager em, EntityManagerFactory emf) {
        System.out.println("Seeded rows per table:");
        emf.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .sorted(Comparator.naturalOrder())
                .forEach(table -> {
                    // Native, because JPQL count(e) is not portable for composite keys.
                    Number rows = (Number) em.createNativeQuery("select count(*) from " + table).getSingleResult();
                    System.out.printf("  %-22s %d%n", table, rows.longValue());
                });
    }

    private static void fail(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
