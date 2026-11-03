package pl.football.seeder.random;

import net.datafaker.Faker;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Names by culture (the last column of national_teams.csv), always through Datafaker.
 *
 * <p>Datafaker's data needs three fixes, all made with the yml files in seed/names:
 * <ul>
 *   <li>many locales have no list of male first names and silently fall back to an English list
 *       that also contains women's names, so they get a male list of their own;</li>
 *   <li>some cultures have no Latin-alphabet locale at all (Japan, Morocco, Senegal), so they get
 *       complete first and last name lists;</li>
 *   <li>a file added to an existing locale hides that locale's own data, so custom files use a tag
 *       of their own ("xpl") and surnames still come from the original locale ("pl").</li>
 * </ul>
 * An unknown culture stops the seeder instead of quietly giving English names.
 */
final class Names {

    record Person(String firstName, String lastName) {
    }

    /** Where a culture takes its first names, its last names, and under which key the last names are. */
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
            // Czech and Slovak surnames have a female form, so the male list is read directly.
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

    /** Editors are not tied to a country and may be of any gender. */
    private static final List<String> EDITOR_LOCALES = List.of("de", "es", "fr", "it", "en", "nl", "pt-BR", "cs-CZ");

    private final Rng rng;
    private final Map<String, Faker> fakers = new HashMap<>();

    Names(Rng rng) {
        this.rng = rng;
    }

    Person male(String culture) {
        Source source = CULTURES.get(culture);
        if (source == null) {
            throw new IllegalArgumentException("No name source for culture " + culture);
        }
        return new Person(clean(faker(source.firstLocale()).resolve(MALE_FIRST)),
                clean(faker(source.lastLocale()).resolve(source.lastKey())));
    }

    Person editor() {
        Faker faker = faker(rng.chance(0.7) ? "pl" : rng.pick(EDITOR_LOCALES));
        return new Person(clean(faker.name().firstName()), clean(faker.name().lastName()));
    }

    /** One Faker per locale, created on first use; all share the seeded Random. */
    private Faker faker(String tag) {
        return fakers.computeIfAbsent(tag, this::newFaker);
    }

    private Faker newFaker(String tag) {
        Locale locale = Locale.forLanguageTag(tag);
        Faker faker = new Faker(locale, rng.random);
        if (tag.startsWith("x")) {
            URL url = Names.class.getResource("/seed/names/" + tag + ".yml");
            if (url == null) {
                throw new IllegalStateException("Missing name file for " + tag);
            }
            faker.addUrl(locale, url);
        }
        return faker;
    }

    /** Some Datafaker entries carry invisible characters, e.g. a zero-width space in Croatian surnames. */
    private static String clean(String name) {
        return name.replaceAll("\\p{Cf}", "").trim();
    }
}
