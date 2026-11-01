package pl.football.seeder.random;

import net.datafaker.Faker;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a name culture from national_teams.csv into a person's name, always through Faker.
 *
 * <p>Datafaker's data needs help in three ways, all solved with the yml files in seed/names:
 * <ul>
 *   <li>many locales have no male list, and firstName() mixes genders (pl: "Anna") while
 *       malefirstName() silently falls back to an English list that itself contains "Patricia",
 *       so they get a male list of their own;</li>
 *   <li>many locales are not in the Latin alphabet or do not exist (Japan, Morocco, Senegal), so
 *       those cultures get complete first and last name lists;</li>
 *   <li>adding a file to an existing locale hides that locale's own data, so custom files always
 *       use a tag of their own ("xpl"), and surnames still come from the original locale.</li>
 * </ul>
 * A culture missing from {@link #CULTURES} stops the seeder rather than quietly giving English names.
 */
final class NameFactory {

    record Person(String firstName, String lastName) {
    }

    private record Source(String firstLocale, String lastLocale, String lastKey) {
    }

    private static final String MALE_FIRST = "name.male_first_name";
    private static final String LAST = "name.last_name";

    private static final Map<String, Source> CULTURES = Map.ofEntries(
            Map.entry("pl", new Source("xpl", "pl", LAST)),
            Map.entry("fr", new Source("xfr", "fr", LAST)),
            Map.entry("it", new Source("xit", "it", LAST)),
            Map.entry("nl", new Source("xnl", "nl", LAST)),
            Map.entry("hr", new Source("xhr", "hr", LAST)),
            Map.entry("es", new Source("es", "es", LAST)),
            Map.entry("pt", new Source("pt-BR", "pt-BR", LAST)),
            Map.entry("de", new Source("de", "de", LAST)),
            Map.entry("en", new Source("xen", "en", LAST)),
            Map.entry("hu", new Source("hu", "hu", LAST)),
            Map.entry("sv", new Source("sv", "sv", LAST)),
            Map.entry("da", new Source("da-DK", "da-DK", LAST)),
            Map.entry("no", new Source("nb-NO", "nb-NO", LAST)),
            Map.entry("fi", new Source("fi-FI", "fi-FI", LAST)),
            Map.entry("et", new Source("et", "et", LAST)),
            Map.entry("tr", new Source("tr", "tr", LAST)),
            Map.entry("sq", new Source("sq", "sq", LAST)),
            Map.entry("cs", new Source("cs-CZ", "cs-CZ", "name.male_last_name")),
            Map.entry("sk", new Source("sk", "sk", "name.man_last_name")),
            Map.entry("ro", new Source("xro", "xro", LAST)),
            Map.entry("xeu", new Source("xeu", "xeu", LAST)),
            Map.entry("xge", new Source("xge", "xge", LAST)),
            Map.entry("xgr", new Source("xgr", "xgr", LAST)),
            Map.entry("xil", new Source("xil", "xil", LAST)),
            Map.entry("xjp", new Source("xjp", "xjp", LAST)),
            Map.entry("xkr", new Source("xkr", "xkr", LAST)),
            Map.entry("xar", new Source("xar", "xar", LAST)),
            Map.entry("xir", new Source("xir", "xir", LAST)),
            Map.entry("xca", new Source("xca", "xca", LAST)),
            Map.entry("xcn", new Source("xcn", "xcn", LAST)),
            Map.entry("xin", new Source("xin", "xin", LAST)),
            Map.entry("xsa", new Source("xsa", "xsa", LAST)),
            Map.entry("xwa", new Source("xwa", "xwa", LAST)),
            Map.entry("xea", new Source("xea", "xea", LAST)));

    private static final List<String> CUSTOM_TAGS = List.of("xen", "xpl", "xfr", "xit", "xnl", "xhr", "xro", "xeu", "xge",
            "xgr", "xil", "xjp", "xkr", "xar", "xir", "xca", "xcn", "xin", "xsa", "xwa", "xea");

    /** Editors are not tied to a country and may be of any gender. */
    private static final List<String> EDITOR_LOCALES = List.of("de", "es", "fr", "it", "en", "nl", "pt-BR", "cs-CZ");

    /** Filed under men in Datafaker's own list for a locale we otherwise use as it is. */
    private static final Set<String> WOMEN_IN_MALE_LISTS = Set.of("Júlia");

    private final Rng rng;
    private final Map<String, Faker> fakers = new HashMap<>();

    NameFactory(Rng rng) {
        this.rng = rng;
    }

    Person male(String culture) {
        Source source = CULTURES.get(culture);
        if (source == null) {
            throw new IllegalArgumentException("No name source for culture " + culture);
        }
        String first;
        do {
            first = clean(faker(source.firstLocale()).resolve(MALE_FIRST));
        } while (WOMEN_IN_MALE_LISTS.contains(first));
        String last = clean(faker(source.lastLocale()).resolve(source.lastKey()));
        return new Person(first, last);
    }

    Person editor() {
        Faker faker = faker(rng.chance(0.7) ? "pl" : rng.pick(EDITOR_LOCALES));
        return new Person(clean(faker.name().firstName()), clean(faker.name().lastName()));
    }

    private Faker faker(String tag) {
        return fakers.computeIfAbsent(tag, this::newFaker);
    }

    private Faker newFaker(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        Faker faker = new Faker(locale, rng.random);
        if (CUSTOM_TAGS.contains(tag)) {
            URL url = NameFactory.class.getResource("/seed/names/" + tag + ".yml");
            if (url == null) {
                throw new IllegalStateException("Missing name file for " + tag);
            }
            faker.addUrl(locale, url);
        }
        return faker;
    }

    /** Some Datafaker entries carry invisible characters, e.g. a zero-width space in the hr surnames. */
    private static String clean(String name) {
        return name.replaceAll("\\p{Cf}", "").trim();
    }
}
