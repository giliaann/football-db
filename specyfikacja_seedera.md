# Specyfikacja seedera danych

## Cel

Seeder wypełnia pustą bazę losowymi, zróżnicowanymi i spójnymi danymi piłkarskimi, które nadają się do pisania i analizy zapytań. Przyjęto, że realizm ustępuje prostocie kodu wszędzie tam, gdzie nie wpływa na wyniki zapytań. Spójność danych, które się agreguje (wyniki, gole, tabele, składy), jest zachowana zawsze.

## Założenia ogólne

- Dane obejmują okres od 1 czerwca 2024 do stałej chwili „teraz”: **1 listopada 2026, godz. 18:30**. Bieżąca data systemowa nie jest używana.
- Wszystkie losowania korzystają z jednego generatora z ziarnem. To samo ziarno daje zawsze tę samą bazę. Domyślne ziarno to 42.
- Cały seed wykonywany jest w jednej transakcji. Błąd na dowolnym etapie wycofuje wszystkie zmiany.
- Seeder uruchamiany jest poleceniem (ziarno jest opcjonalne):

  ```bash
  docker compose run --rm --build seeder 42
  # lub lokalnie:
  ./mvnw compile exec:java -Dexec.args="42"
  ```

- Prawdziwe są kluby, stadiony, reprezentacje i nazwy rozgrywek (pliki CSV w `src/main/resources/seed`). Ludzie i przebieg meczów są fikcyjni.
- Imiona i nazwiska pochodzą z biblioteki Datafaker, z lokalizacji odpowiadającej narodowości osoby. Zawodnicy, trenerzy i sędziowie mają imiona męskie, redaktorzy imiona obu płci.

## Kluczowa zasada konstrukcji

Wszystko, co nie zależy od wyników meczów, wyliczane jest **przed** rozegraniem pierwszego meczu:

- przynależność zawodników do klubów w każdym sezonie (po transferach),
- historia trenerów,
- kontuzje,
- terminarz (z gotowego szablonu dat).

Mecze rozgrywane są potem kolejno, edycja po edycji. Mecz tylko odczytuje gotowe dane, np. „kto był w klubie i był zdrowy w dniu meczu”. Od wyników zależą wyłącznie rzeczy wewnątrz jednej edycji: tabela, awans do kolejnej rundy pucharu oraz kwalifikacja do Ligi Mistrzów w następnym sezonie.

## Zakres rozgrywek

| Rozgrywki | Sezony | Format |
| --- | --- | --- |
| LaLiga, Serie A | 2024/25, 2025/26, 2026/27 | 20 klubów, mecz i rewanż |
| Ekstraklasa | jw. | 18 klubów, mecz i rewanż |
| Copa del Rey, Coppa Italia, Puchar Polski | jw. | 32 kluby, jeden mecz w każdej rundzie od 1/16 finału |
| UEFA Champions League | jw. | 32 kluby, 8 grup po 4, potem dwumecze i finał |
| Copa América | 2024 | 16 reprezentacji, 4 grupy po 4, potem ćwierćfinały |

Sezony 2024/25 i 2025/26 są zakończone. Sezon 2026/27 trwa: mecze sprzed chwili „teraz” są rozegrane, późniejsze są zaplanowane.

### Ligi

- Terminarz układany jest metodą kołową (tabela Bergera): każdy z każdym, u siebie i na wyjeździe.
- Za zwycięstwo przyznawane są 3 punkty, za remis 1. O kolejności decydują punkty, różnica bramek i bramki zdobyte.
- Po sezonie trzy ostatnie kluby spadają, a na ich miejsce wchodzą trzy losowe kluby z niższej ligi (Segunda División, Serie B, I liga).

### Puchary krajowe

- Uczestniczy cała liga i losowe kluby z niższej ligi, łącznie 32 kluby.
- Każda runda jest losowana dopiero po zakończeniu poprzedniej. Przy remisie rozgrywana jest dogrywka, a potem rzuty karne.
- Finał odbywa się na stadionie z puli finałowej w danym kraju (Estadio de La Cartuja, PGE Narodowy). Gdy takiego stadionu brak (Włochy), finał gra się na stadionie wylosowanego gospodarza.

### Liga Mistrzów

- Uczestniczą po 4 najlepsze kluby z każdej z trzech lig według tabeli z poprzedniego sezonu (w pierwszym sezonie według siły składu) oraz 20 losowych klubów z pozostałych krajów Europy.
- Przyjęto **uproszczony format grupowy** (8 grup po 4 drużyny, mecz i rewanż) we wszystkich sezonach, choć od 2024/25 rzeczywisty format jest inny.
- Dwie najlepsze drużyny z grupy awansują do 1/8 finału. Zwycięzca grupy gra rewanż u siebie z drugą drużyną sąsiedniej grupy. Ćwierćfinały i półfinały są losowane.
- Dwumecze nie mają reguły bramek wyjazdowych. Przy remisie w dwumeczu rewanż kończy się dogrywką i ewentualnie rzutami karnymi.
- Finał to jeden mecz na stadionie z puli finałowej, z dogrywką i karnymi.

### Copa América 2024

- Skład grup odpowiada rzeczywistemu turniejowi, a wyniki są losowe.
- Grupa to każdy z każdym, jeden mecz. Dwie najlepsze drużyny awansują do ćwierćfinałów. Rozgrywane są półfinały, mecz o 3. miejsce i finał.
- W fazie pucharowej remis po 90 minutach oznacza od razu rzuty karne. Dogrywka jest tylko w finale.
- Mecze odbywają się na stadionach z puli turnieju w USA, finał na Hard Rock Stadium.

## Terminarz

Przyjęto szablon dat, w którym kolizje nie mogą wystąpić:

- **Ligi** grają w kolejne weekendy od pierwszej soboty po 15 sierpnia, z przerwą zimową (20 grudnia–6 stycznia). Mecze jednej kolejki mają różne godziny od piątku do poniedziałku.
- **Liga Mistrzów** gra we wtorki i środy ustalonych tygodni (6 kolejek grupowych jesienią, faza pucharowa od lutego, finał na przełomie maja i czerwca).
- **Puchary krajowe** grają we wtorki, środy i czwartki innych tygodni niż Liga Mistrzów.
- **Copa América** odbywa się w czerwcu i lipcu, poza sezonem klubowym.
- Przy wyborze godziny sprawdzane jest, czy stadion, obie drużyny i sędzia są wolni o tej godzinie. Ograniczenia unikalności tabeli `Match` są więc zawsze spełnione.
- Każda kolejka ligowa ma mecze w niedzielę o 17:00, 17:30 i 18:15. Dzięki temu w chwili „teraz” trwają mecze w każdej fazie: pierwsza połowa, przerwa i druga połowa.

Nie modeluje się przerw między meczami jednej drużyny w różnych rozgrywkach ani przerw reprezentacyjnych.

## Ludzie

### Kluby i zawodnicy

- Każdy klub z katalogu (162 kluby) zaczyna z 25 zawodnikami: 3 bramkarzy, 8 obrońców, 8 pomocników i 6 napastników.
- Zawodnik z prawdopodobieństwem 70% ma narodowość kraju klubu, w pozostałych przypadkach losową z katalogu reprezentacji.
- Każdy zawodnik ma ukrytą „jakość” losowaną z rozkładu normalnego, ze średnią zależną od poziomu klubu (elitarny, średni, mały). Siła drużyny to średnia jakości 11 najlepszych zawodników.
- Wzrost zależy od pozycji (rozkład normalny), noga: ok. 77% prawa, 20% lewa, 3% obie.

### Reprezentacje

- Reprezentacje tworzone są tylko dla uczestników Copa América. Skład liczy 26 zawodników (3/8/8/7).
- Do składu wybierani są najlepsi zawodnicy danej narodowości z klubów. Brakujące miejsca uzupełniają nowi zawodnicy bez klubu w bazie (grający w klubach spoza katalogu). Części z nich brakuje wzrostu i nogi, żeby kolumny dopuszczające `NULL` miały takie wartości.

### Transfery

- Skład z początku historii nie ma wierszy `Transfer`.
- Transfery odbywają się w oknach 1–31 lipca 2025 i 2026, zanim zaczyna się sezon. W trakcie sezonu skład klubu się nie zmienia.
- Klub zmienia ok. 15% zawodników. W 55% przypadków klub docelowy jest z tego samego kraju.
- Typy transferów: `permanent` (kwota w walucie kraju klubu kupującego), `free` (bez kwoty i waluty), `loan` (do 30 czerwca następnego roku, czasem z opłatą).
- Wypożyczony zawodnik wraca do klubu macierzystego w kolejnym oknie, bez osobnego wiersza.
- Klub nie oddaje zawodnika, jeśli zostałoby mu mniej niż 2 bramkarzy, 6 obrońców, 6 pomocników lub 4 napastników. Klub nie przyjmuje zawodnika, jeśli ma już 30.

### Kontuzje

- Kontuzje losowane są z góry: średnio 0,35 na zawodnika na sezon (rozkład Poissona). Rodzaj i czas trwania pochodzą z katalogu urazów z wagami częstości.
- Kontuzje jednego zawodnika się nie nakładają. Kontuzja trwająca w chwili „teraz” ma pusty `end_date`.
- Kontuzjowany zawodnik nie jest powoływany do składu meczowego.

### Trenerzy

- Każda drużyna ma trenera od początku historii. Zmiany następują w stałych datach: latem (20% klubów, 15% reprezentacji) oraz w listopadzie i lutym (6% klubów).
- Zwolniony trener trafia do puli wolnych i może objąć inną drużynę przy kolejnej zmianie. Trener prowadzi naraz tylko jedną drużynę.
- `Team.coach_id` wskazuje trenera z chwili „teraz”. Wiersz trenera w składzie meczowym wskazuje trenera z dnia meczu.

### Sędziowie

Każde rozgrywki mają własną pulę sędziów: po jednej puli na kraj (wspólnej dla ligi i pucharu), pulę Ligi Mistrzów (sędziowie z innych krajów Europy) i pulę Copa América (sędziowie z krajów obu Ameryk).

## Przebieg meczu

- **Skład:** 11 zawodników w jednej z kilku formacji (np. 4-4-2, 4-3-3, 3-5-2), dokładnie jeden bramkarz, wybór według jakości z losowym szumem. Ławka ma rozmiar zgodny z regulaminem rozgrywek i zawiera rezerwowego bramkarza. Do tego jeden wiersz trenera.
- **Numery koszulek** są stałe dla zawodnika w danym klubie i sezonie (z zachowaniem numeru z poprzedniego sezonu, jeśli to możliwe).
- **Wynik:** liczba goli z rozkładu Poissona. Średnia zależy od różnicy sił i od tego, czy mecz jest na stadionie gospodarza.
- **Gole:** strzelec losowany z wagami pozycji (najczęściej napastnicy). Ok. 70% goli ma asystę innego zawodnika tej samej drużyny. Ok. 4% goli to samobóje.
- **Rzuty karne w trakcie meczu:** faul zawodnika drużyny przeciwnej (najczęściej obrońcy), a w tej samej minucie gol z karnego albo niewykorzystany karny.
- **Gole anulowane przez VAR:** zapisywane jako gol z `is_cancelled = true` i opisem. Nie liczą się do wyniku.
- **Kartki:** średnio 1,9 żółtej na drużynę. Druga żółta daje czerwoną w tej samej minucie. Czasem zdarza się bezpośrednia czerwona. Ukarany czerwoną kartką schodzi z boiska i nie jest zastępowany.
- **Zmiany:** 3–5 w regulaminowym czasie (w 1–3 okienkach), w dogrywce czasem jedna dodatkowa. Zmieniony zawodnik nie wraca.
- **Czas:** doliczony czas 1–5 minut po pierwszej połowie i 2–8 po drugiej. Godziny połów, dogrywki i zdarzeń są ze sobą zgodne. Minuty doliczone zapisywane są w `stoppage_minute`.
- **Seria rzutów karnych:** okres `penalties`, minuta 120 (lub 90 bez dogrywki). Najpierw 5 serii, potem nagła śmierć. Seria kończy się, gdy jest rozstrzygnięta. Gole z serii nie wliczają się do `home_goals` i `away_goals`.
- **Kto działa:** zdarzenia dotyczą tylko zawodników, którzy są w danej chwili na boisku.
- `advancing_side` ustawiane jest w meczach rozstrzygających: mecz pucharowy, rewanż dwumeczu i finał.

### Statusy meczów

| Status | Kiedy |
| --- | --- |
| `finished` | Mecz zakończył się przed chwilą „teraz” |
| `in_progress` | Mecz trwa w chwili „teraz”. Zapisane są tylko zdarzenia do tej chwili i ustawiony jest `match_period` |
| `scheduled` | Mecz w przyszłości, bez składów i zdarzeń |
| `postponed`, `cancelled` | Kilka meczów ligowych sezonu 2026/27 wybranych na stałe. Nie mają składów i nie liczą się do tabel |

## Tabele (`EditionParticipation`)

- Każdy uczestnik edycji ma jeden wiersz.
- Punkty liczą się tylko z meczów ligowych i grupowych. Bramki obejmują wszystkie rozegrane mecze, bez serii rzutów karnych.
- `stage_reached` to nazwa ostatniego etapu, w którym drużyna rozegrała mecz.
- `final_position` to miejsce w tabeli ligi, a w pucharach i turniejach tylko miejsca medalowe (1–2, w Copa América 1–4). W edycjach w toku kolumna jest pusta.

## Redaktorzy i uprawnienia

- Pierwszy redaktor („System Seeder”) wskazuje sam na siebie jako ostatniego edytującego. Wymaga tego niezerowy klucz obcy `last_edited_by_id`, więc identyfikator pobierany jest z sekwencji przed wstawieniem wiersza.
- Administrator globalny otrzymuje uprawnienie od redaktora systemowego, a pozostałe uprawnienia globalne (dane podstawowe, transfery, kontuzje) nadaje administrator.
- Dla każdej edycji administrator globalny nadaje uprawnienie `competition_administrator` jednemu redaktorowi. Ten tworzy uprawnienie `match_editor` dla edycji i nadaje je trzem redaktorom meczów.
- Mecze, składy i zdarzenia edytuje jeden z redaktorów meczów danej edycji. Ok. 1% zakończonych meczów edytuje gość, który otrzymał uprawnienie tylko do tego jednego meczu.
- W zakończonych edycjach jeden redaktor ma uprawnienie odebrane miesiąc po końcu edycji (`revoked_at`).
- Kilka kont jest zablokowanych, a ich uprawnienia są odebrane. Kilka kont nie ma żadnych uprawnień i część z nich nigdy się nie logowała.
- Hasła to losowe wartości w formacie SHA-256, które nie są hashem żadnego znanego hasła. Na wygenerowane konta nie da się zalogować.
- Kto edytuje co: słowniki i regiony — redaktor systemowy; drużyny, ludzie, stadiony i składy — redaktor danych podstawowych; transfery — redaktor transferów; kontuzje — redaktor kontuzji; rozgrywki i edycje — administrator; etapy i tabele — administrator rozgrywek danej edycji.
- `created_at` i `updated_at` odpowiadają historii (np. mecz tworzony jest przy publikacji terminarza, skład godzinę przed meczem), a nie chwili uruchomienia seedera.

## Kontrola pokrycia

Po zapisaniu danych, ale przed zatwierdzeniem transakcji, wykonywane są zapytania sprawdzające, czy każdy wariant występuje co najmniej raz: każdy status meczu, każda faza meczu trwającego, każdy typ zdarzenia, gol anulowany, dogrywka, seria karnych, oba warianty `advancing_side`, każdy typ transferu, transfer z kwotą i bez, wypożyczenie, kontuzja trwająca, każda rola i każdy zakres uprawnienia, uprawnienie odebrane, konto zablokowane, każdy typ etapu, rozgrywek i drużyny, a także wartości `NULL` i nie-`NULL` w wybranych kolumnach. Brak któregokolwiek wariantu przerywa seed i wycofuje transakcję.

Na końcu wypisywana jest liczba wierszy w każdej tabeli.

## Gwarancje spójności

Z konstrukcji seedera wynikają następujące własności danych. Każdą z nich można sprawdzić zapytaniem, które powinno zwrócić zero naruszeń:

- `home_goals` i `away_goals` są równe liczbie nieanulowanych zdarzeń `goal`, `penalty_goal` i `own_goal` (samobój liczy się drużynie przeciwnej), bez serii rzutów karnych;
- punkty w `EditionParticipation` wynikają z zakończonych meczów ligowych i grupowych;
- w każdej rundzie pucharowej występują tylko drużyny, które awansowały z poprzedniej;
- zawodnik nie występuje w meczu w dniu, w którym jest kontuzjowany;
- zawodnik gra w danym sezonie tylko dla jednego klubu;
- zawodnik nie ma zdarzeń po zejściu z boiska lub czerwonej kartce, a rezerwowy przed wejściem na boisko;
- autor meczu ma uprawnienie `match_editor` do edycji albo do tego meczu;
- trener nie prowadzi dwóch drużyn tego samego dnia, a drużyna nie gra dwóch meczów tego samego dnia;
- czas zapisu zdarzenia mieści się między początkiem a końcem meczu, a mecz jest tworzony przed jego rozpoczęciem.

## Wynik dla ziarna 42

| Tabela | Wiersze |
| --- | --- |
| Appearance | 135 310 |
| MatchEvent | 77 581 |
| Injury | 4 532 |
| TeamSquad | 4 466 |
| Player | 4 371 |
| Match | 3 834 |
| Transfer | 1 066 |
| EditionParticipation | 574 |
| Coach | 208 |
| Stadium | 178 |
| Team | 178 |
| PermissionAssignment | 141 |
| Region | 119 |
| Referee | 98 |
| Stage | 85 |
| Permission | 75 |
| Editor | 45 |
| Edition | 22 |

W zakończonych meczach pada średnio 2,95 gola, 22% kończy się remisem, a 48% zwycięstwem gospodarza. Seed trwa ok. 25 sekund.

## Zapis

- Encje mają klucze `IDENTITY`, więc wiersz trafia do bazy od razu przy zapisie. Encja musi być kompletna w chwili zapisu i nie jest później zmieniana.
- Po każdej kolejce lub rundzie sesja Hibernate jest opróżniana (`flush` i `clear`), żeby nie gromadziła tysięcy obiektów. Encje odłączone służą dalej jako odwołania w kluczach obcych.

## Struktura kodu

Kod znajduje się w pakiecie `pl.football.seeder.random`. Publiczna jest tylko klasa `RandomSeeder`, którą wywołuje `Main` (konfiguracja połączenia, transakcja i podsumowanie liczby wierszy).

| Klasa | Odpowiedzialność |
| --- | --- |
| `RandomSeeder` | Wywołuje kolejne etapy w ustalonej kolejności |
| `SeedTime` | Stała chwila „teraz”, początek historii, lista sezonów |
| `Rng` | Jedyne źródło losowości: liczby, wybory, rozkład normalny i Poissona, wybór ważony |
| `Db` | Zapis encji z polami audytu, opróżnianie sesji |
| `Catalog` | Wczytanie plików CSV (kluby, reprezentacje, stadiony, rozgrywki, urazy) |
| `Names` | Imiona i nazwiska według narodowości |
| `Editors` | Redaktorzy i uprawnienia, wybór redaktora do danego rodzaju danych |
| `Dictionaries` | Regiony, słowniki (nogi, typy transferów, waluty, typy zdarzeń), stadiony |
| `TeamInfo`, `PlayerInfo` | Stan drużyny i zawodnika w pamięci: składy w sezonach, trenerzy, kontuzje, numery |
| `People` | Drużyny, zawodnicy, reprezentacje, kontuzje, trenerzy, sędziowie, końcowe składy |
| `Transfers` | Okna transferowe i zapis transferów |
| `Calendar` | Szablon dat i rezerwacje godzin (stadion, drużyny, sędzia) |
| `RoundRobin` | Pary meczów „każdy z każdym” |
| `MatchEngine` | Jeden mecz: składy, przebieg, status i zapis |
| `Standings` | Punkty, bramki i osiągnięty etap w jednej edycji |
| `RunningEdition` | Jedna edycja w trakcie: etapy, sędziowie, rozgrywanie meczów, zapis tabeli |
| `Competitions` | Formaty rozgrywek: ligi, puchary, Liga Mistrzów, Copa América, awanse i spadki |
| `CoverageCheck` | Kontrola pokrycia wariantów danych |

## Przyjęte uproszczenia

Poniższe uproszczenia nie naruszają spójności danych, ale obniżają ich realizm:

- terminarz pochodzi z szablonu, a nie z rzeczywistych kalendarzy rozgrywek;
- nie ma zawieszeń za kartki ani wymaganej przerwy między meczami drużyny;
- kontuzje nie wynikają z przebiegu meczów;
- transfery odbywają się tylko latem, a zawodnicy nie kończą karier;
- Liga Mistrzów we wszystkich sezonach ma format grupowy;
- reprezentacje występują tylko w jednym turnieju;
- nie sprawdza się, czy kluby dzielące stadion grają u siebie w tej samej kolejce (zachowana jest tylko unikalność godzin).
