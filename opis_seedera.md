# Opis losowego seedera

Seeder tworzy spójną historię piłkarską od lata 2023 do 1 listopada 2026, godz. 18:30. Rozgrywki, kluby i stadiony są prawdziwe, ludzie fikcyjni, a przebieg meczów powstaje w symulacji zgodnej z regulaminami.

Ten sam seed zawsze daje te same dane. Domyślny seed to 42.

Seeder wypełnia pustą bazę w jednej transakcji:

```bash
./mvnw compile exec:java -Dexec.args="random 42"
```

Dla seeda 42 powstaje ok. 423 tys. wierszy, w tym:

| Tabela | Wiersze |
| --- | --- |
| Appearance | 236 803 |
| MatchEvent | 153 291 |
| Injury | 7 453 |
| Player | 6 686 |
| TeamSquad | 6 493 |
| Match | 6 279 |
| Transfer | 2 684 |
| EditionParticipation | 1 064 |

## Główne założenia

- **Zakres:** LaLiga, Serie A i Ekstraklasa, ich puchary krajowe, UEFA Champions League, UEFA Europa League oraz turnieje reprezentacji. Sezony klubowe to 2023/24-2026/27.
- **Kluby i stadiony:** własny katalog prawdziwych obiektów, bo Faker nie ma tych danych w potrzebnej postaci.
- **Stała data „teraz”:** 1 listopada 2026, godz. 18:30. Kod nie używa bieżącej daty, więc wynik zależy tylko od seeda.
- **Schemat:** jedyna zmiana względem pierwotnego `ddl_script.sql` to usunięcie kolumny `EventType.score_impact`.
- **Zapis:** cały seed to jedna transakcja. Encje mają klucze `IDENTITY`, więc Hibernate nie grupuje insertów, a sesja jest opróżniana (`flush` i `clear`) po każdym dniu symulacji.

## Jak klasy współpracują

Kod jest w pakiecie `pl.football.seeder.random`. Wszystkie jego klasy są dostępne tylko w obrębie pakietu, z wyjątkiem `RandomSeedRun`.

```text
Main ─> DataSeeder ─> RandomSeedRun  (łączy części, po kolei)
                          │
   1. dane stałe          ├─ Catalog ............ pliki CSV z resources/seed
                          ├─ NameFactory ........ imiona i nazwiska przez Fakera
                          ├─ EditorDirectory .... redaktorzy i uprawnienia
                          ├─ MasterData ......... regiony, słowniki, stadiony
   2. kariery             ├─ CareerSimulator .... składy, transfery, trenerzy, kontuzje
   3. sędziowie           ├─ Officials .......... pule sędziów i obsada meczów
                          │
   4. symulacja           ├─ Simulation ......... kolejka zdarzeń według daty
                          │     ▲        │
                          │     │ kolejkuje mecze i starty sezonów
                          │     │        ▼ uruchamia
                          ├─ EditionPlanner ──> MatchSimulator
                          │     ▲                    │
                          │     └── koniec rundy ────┘
                          │
   5. podsumowanie        └─ tabele końcowe, EditionParticipation, Injury, TeamSquad

Wszystkie klasy zapisują przez Db, a losują przez Rng.
```

Najważniejsza zasada to podział na dwa etapy. Kariery powstają przed meczami i nie zależą od wyników. Mecze rozgrywają się potem chronologicznie, a każda kolejna runda pucharu powstaje dopiero po zakończeniu poprzedniej.

## Co robi każda klasa

### Punkt wejścia

| Klasa | Rola | Komunikacja |
| --- | --- | --- |
| `Main` | Czyta argumenty i konfigurację połączenia, zarządza transakcją. | Woła `DataSeeder`. |
| `DataSeeder` | Publiczna fasada seedera. | Przekazuje `EntityManager` i seed do `RandomSeedRun`. |
| `RandomSeedRun` | Tworzy obiekty w kolejności zależności i uruchamia kolejne etapy. Ustala też końcowe miejsca w ligach. | Jedyna klasa, która zna wszystkie pozostałe. |

### Narzędzia

| Klasa | Rola | Komunikacja |
| --- | --- | --- |
| `SeedTime` | Stała data „teraz” (`NOW`), początek historii i lista sezonów. Kod nie używa `LocalDate.now()`. | Stałe czytane przez większość klas. |
| `Rng` | Jedno źródło losowości. Wybory, liczby i daty idą przez Fakera, a rozkłady Poissona i Gaussa oraz wybór ważony przez `Random`. | Używana przez wszystkie klasy, które losują. |
| `Db` | Zapisuje encje, uzupełnia pola audytu, opróżnia sesję Hibernate. | Wołana przy każdym zapisie. |
| `Catalog` | Wczytuje `clubs`, `national_teams`, `stadiums`, `competitions`, `editions` i `injuries` z CSV do rekordów. | Tylko odczyt, korzysta z niej większość klas. |
| `NameFactory` | Zamienia kulturę imion (np. `pl`, `xjp`) na imię i nazwisko z Fakera. Nieznana kultura przerywa seed. | Wołana przez `CareerSimulator`, `Officials` i `EditorDirectory`. |

### Dane stałe i ludzie

| Klasa | Rola | Komunikacja |
| --- | --- | --- |
| `MasterData` | Zapisuje regiony, typy zdarzeń i transferów, stopy, waluty i stadiony. Przelicza kwoty według stałych kursów. | Daje pozostałym klasom encje po nazwie. |
| `EditorDirectory` | Tworzy redaktorów i uprawnienia. Mówi, kto może edytować dany rodzaj danych. | `EditionPlanner` prosi o zespół edycji, a `MatchSimulator` o gościa jednego meczu. |
| `CareerSimulator` | Tworzy drużyny i zawodników, symuluje okna transferowe, wypożyczenia, końce karier i wychowanków, planuje kontuzje treningowe i zmiany trenerów. Zapisuje ludzi, transfery, kontuzje i składy. | Zwraca `TeamState` i `PlayerState`. W nagłych brakach `MatchSimulator` prosi ją o wychowanka lub powołanie. |
| `TeamState` | Stan drużyny w pamięci: członkowie, historia trenerów, numery koszulek. Odpowiada, kto był w składzie danego dnia. | Czytany przez `EditionPlanner` i `MatchSimulator`. |
| `PlayerState` | Stan zawodnika w pamięci: okresy w klubach, kontuzje, powołania, data ostatniego meczu, koniec kariery. Odpowiada, czy jest dostępny danego dnia. | Ustawiany przez `CareerSimulator`, uzupełniany przez `EditionPlanner` i `MatchSimulator`. |
| `Officials` | Tworzy sędziów i obsadza mecze, pilnując przerw oraz neutralności w meczach międzynarodowych. | Wołana przez `MatchSimulator` przy każdym meczu. |

### Terminarz i rozgrywki

| Klasa | Rola | Komunikacja |
| --- | --- | --- |
| `SeasonCalendar` | Dzieli tygodnie sezonu na przerwy, tygodnie UEFA, pucharowe i kolejki ligowe. | Tworzony przez `EditionPlanner` dla każdego sezonu. |
| `Schedule` | Pamięta, kiedy grają drużyny i kiedy zajęte są stadiony. | `EditionPlanner` pyta o wolne terminy i je rezerwuje. |
| `Pairings` | Liczy pary: tabela Bergera, faza ligowa UEFA, grupy, losowania. Nie zna dat ani wyników. | Wołana przez `EditionPlanner`. |
| `EditionPlanner` | Zna wszystkie formaty. Tworzy `Competition`, `Edition` i `Stage`, wybiera uczestników, układa mecze i po każdej rundzie losuje następną. | Wstawia mecze do `Simulation`, a `Fixture.Round` zgłasza mu koniec rundy. |
| `EditionRun` | Edycja w trakcie gry: tabele, punkty, zawieszenia, żółte kartki, składy reprezentacji, medale. Zapisuje `EditionParticipation`. | Tworzony przez `EditionPlanner`, aktualizowany przez `MatchSimulator`. |
| `Fixture` | Umówiony mecz, a w nim `Tie` (para pucharowa, także dwumecz) i `Round` (licznik meczów rundy). | Przekazuje dane od `EditionPlanner` do `MatchSimulator`. |
| `Simulation` | Kolejka priorytetowa zdarzeń według daty. Po każdym dniu opróżnia sesję Hibernate. | `EditionPlanner` dodaje zadania, a kolejka je uruchamia. |

### Mecz

| Klasa | Rola | Komunikacja |
| --- | --- | --- |
| `MatchSimulator` | Rozgrywa mecz: składy, wynik, zdarzenia, dogrywka, karne, zapis i konsekwencje. Wewnętrzna klasa `Course` to jeden mecz. | Dostaje `Fixture`, po meczu aktualizuje `EditionRun` i `PlayerState`, a na końcu woła `Fixture.Round`. |

### Pliki danych

| Plik w `src/main/resources/seed` | Zawartość |
| --- | --- |
| `clubs.csv` | 162 kluby: LaLiga 20 + Segunda 13, Serie A 20 + Serie B 13, Ekstraklasa 18 + I liga 14 oraz 64 kluby tylko do pucharów europejskich. Dla każdego: skrót, rok założenia, kraj, miasto, stadion, pojemność, liga, poziom |
| `national_teams.csv` | 112 reprezentacji: nazwa angielska, kod FIFA jako `short_name`, rok założenia, konfederacja, kultura imion |
| `stadiums.csv` | Stadiony turniejów i finałów |
| `competitions.csv` | Rozgrywki: format, ławka, limit zmian, limit żółtych kartek, zasady awansu |
| `editions.csv` | Edycje UEFA i turniejów: daty, gospodarze, stadion finału, zasady |
| `injuries.csv` | Urazy: opis, zakres dni absencji, częstość |
| `names/*.yml` | Własne listy imion i nazwisk ładowane do Fakera |

## Przyjęte zasady danych

### Język i nazwy

- Po angielsku są opisy (awans, kontuzje, zdarzenia, uprawnienia), nazwy etapów i walut, kraje oraz nazwy typu FIFA World Cup.
- Nazwy własne są w oryginale: Ekstraklasa, Puchar Polski, Copa del Rey, Legia Warszawa.
- Nazwy krajów pochodzą z `Locale.getDisplayCountry(Locale.ENGLISH)`, z poprawkami tam, gdzie piłka używa innej nazwy (np. Turkey, Ivory Coast, DR Congo).
- Zamiast „United Kingdom” są England, Scotland, Wales i Northern Ireland, bo tak grają w FIFA. Regiony nie obejmują terytoriów bez własnej federacji.
- Kontynenty to Europe, Asia, Africa, North America, South America i Oceania. Dodatkowo „World” ma typ `continent`, bo tak dopuszcza schemat.

### Faker i ludzie

- Każdy człowiek najpierw dostaje kraj, a imię i nazwisko dopiero z lokalizacji tego kraju. Daty urodzenia, wybory, liczby i hash hasła też idą przez Fakera. Wszystkie instancje Fakera współdzielą jeden `Random`, więc o wszystkim decyduje seed.
- Dane Datafakera wymagały poprawek, więc część list jest własna:
  - `firstName()` miesza płcie (w `pl` jest Anna), a `malefirstName()` w lokalizacji bez listy męskiej spada na angielską, zawierającą m.in. Patricia i Maria.
  - Dla krajów bez imion w alfabecie łacińskim (Japonia, Maroko, Senegal i inne) są własne, kompletne listy.
  - Plik dodany do istniejącej lokalizacji przesłania jej dane, dlatego własne pliki mają osobne tagi (`xpl`), a nazwiska nadal pochodzą z oryginalnej lokalizacji.
  - W `cs-CZ` i `sk` metoda `lastName()` losuje też nazwiska żeńskie, więc nazwiska są czytane z list męskich przez `faker.resolve()`.
  - W `hr` niektóre nazwiska zawierają niewidoczny znak (U+200B), który jest usuwany.
- Redaktorzy mogą mieć imiona obu płci. Piłkarze, trenerzy i sędziowie mają imiona męskie.
- Metody `football().players()` i `coaches()` nie są używane, bo zwracają prawdziwych ludzi. Listy klubów i rozgrywek z Fakera są niepełne lub nieaktualne, a `medical()` nie zawiera urazów sportowych.
- Zawodnik ma wzrost zależny od pozycji i nogę: ok. 77% prawa, 20% lewa, 3% obie. W składzie startowym ma 17-35 lat.
- Pozostałe osoby: 314 trenerów (od 36 lat na początku historii), 204 sędziów (30 z Hiszpanii, 30 z Włoch, 30 z Polski, reszta z konfederacji) i 71 redaktorów.
- Udział miejscowych w składzie zależy od kraju, np. Hiszpania 62%, Włochy 42%, Polska 60%.

### Kariery i składy

- Klub zaczyna z 25 zawodnikami: 3 bramkarzy, 8 obrońców, 8 pomocników, 6 napastników. Później skład ma 22-30 zawodników i minimalną liczbę na każdej pozycji.
- W każdej dacie zawodnik jest w najwyżej jednym klubie, a w reprezentacji tylko swojego kraju.
- Skład startowy z 30 czerwca 2023 nie ma wierszy `Transfer`. Później transfery są w oknach: 1 VII-31 VIII (ok. 17% zawodników) i 1-31 I (ok. 4%). Zawodnik zmienia klub najwyżej raz w oknie, a ok. 55% ruchów odbywa się w obrębie kraju.
- Typy transferów: `permanent` (kwota w walucie kraju kupującego), `free` (bez kwoty i waluty), `loan` (do 30 czerwca, zwykle bez kwoty). Kwota zależy od wieku zawodnika i poziomu klubu kupującego.
- Wypożyczenie kończy się samo, bez osobnego wiersza, a zawodnik wraca do klubu macierzystego.
- Po 33. roku życia rośnie szansa końca kariery. W wieku 40 lat kariera kończy się zawsze. Miejsce zajmuje wychowanek w wieku 17-19 lat, tej samej narodowości, bez transferu.
- **Stare składy mają sens:** `TeamSquad` to stan na datę „teraz”, a skład meczowy bierze się ze stanu w dniu meczu. Zawodnik spoza obecnej kadry w starym meczu odszedł więc przez transfer albo skończył karierę. Wypożyczony jest w `TeamSquad` tylko w klubie, który go wypożyczył, a ten, kto zakończył karierę, nie ma składu.
- Skład reprezentacji na turniej liczy 26 zawodników: 3 bramkarzy, 8 obrońców, 8 pomocników, 7 napastników. W `TeamSquad` jest ostatnie powołanie bez tych, którzy zakończyli karierę.
- Każdy kraj ma pulę co najmniej 32 zawodników. Brakujący są dopisywani bez klubu w bazie, jakby grali w klubach spoza katalogu. Dlatego zawodników jest ok. 6700, choć kluby mają łącznie ok. 4000 miejsc.
- Trenerzy też mają historię w pamięci: `Team.coach_id` to obecny trener, a `Appearance` zapisuje tego z dnia meczu. Zmiany zdarzają się też w trakcie sezonu, a trener prowadzi naraz tylko jedną drużynę.

### Rozgrywki

| Rozgrywki | Format |
| --- | --- |
| LaLiga, Serie A | 20 drużyn, mecz i rewanż, 3 spadają |
| Ekstraklasa | 18 drużyn, mecz i rewanż, 3 spadają |
| Copa del Rey, Coppa Italia, Puchar Polski | 32 kluby: cała liga i kluby z niższej ligi. Od Round of 32, jeden mecz, przy remisie dogrywka i karne. Półfinały dwumeczowe poza Pucharem Polski. W Copa del Rey gospodarzem jest klub z niższej ligi. Finały na neutralnym stadionie: La Cartuja, Stadio Olimpico, PGE Narodowy |
| UEFA CL i EL 2023/24 | 32 kluby, 8 grup po 4, potem puchar. Trzecie drużyny z LM trafiają do baraży LE |
| UEFA CL i EL od 2024/25 | 36 klubów, faza ligowa z 8 meczami (4 u siebie, 4 na wyjeździe). Miejsca 1-8 do 1/8, 9-24 do baraży, 25-36 odpadają |
| FIFA World Cup 2026 | 48 drużyn, 12 grup, Round of 32, mecz o 3. miejsce |
| UEFA Euro 2024 | 24 drużyny, 6 grup, Round of 16, bez meczu o 3. miejsce |
| AFC Asian Cup 2023 | 24 drużyny, bez meczu o 3. miejsce |
| Africa Cup of Nations 2023 i 2025 | 24 drużyny, z meczem o 3. miejsce |
| Copa América 2024 | 16 drużyn, dogrywka tylko w finale |

- Dwumecze UEFA nie mają reguły bramek wyjazdowych, a finał jest jednym meczem na neutralnym stadionie.
- Gospodarze turniejów są w nich zawsze: Katar (Asian Cup 2023), Wybrzeże Kości Słoniowej (AFCON 2023), Niemcy (Euro 2024), USA (Copa América 2024), Maroko (AFCON 2025) oraz USA, Meksyk i Kanada (World Cup 2026).
- Drużyny pochodzą z właściwego kraju lub konfederacji. Reprezentacje są losowane, więc wyniki nie muszą być sensowne: w finale mistrzostw świata może zagrać np. Rumunia.
- W lidze trzy ostatnie kluby spadają, a na ich miejsce wchodzą trzy losowe kluby z niższej ligi.
- Kluby z Hiszpanii, Włoch i Polski dostają miejsca w UEFA według poprzedniej tabeli (w pierwszym sezonie według poziomu klubu). Reszta Europy jest losowana.
- Każda grupa to osobny `Stage`, bo schemat nie ma innego miejsca na przynależność do grupy. `stage_order` rośnie przez kolejne grupy i rundy. Dwumecz to dwa mecze w jednym etapie, a `advancing_side` ma tylko rewanż.
- Edycje 2026/27 są w toku i mają pusty `end_date`. Część kolejek ligowych i fazy ligowej UEFA jest rozegrana, a w pucharach krajowych po Round of 32 rozlosowano już Round of 16 jako mecze zaplanowane.

### Terminarz

- Ligi grają w weekendy (pt-pon), a środkami tygodnia tylko wtedy, gdy brakuje weekendów. UEFA gra wt-czw, puchary w wolne środki tygodnia. Są przerwy reprezentacyjne i świąteczna.
- Drużyna, zawodnik i sędzia mają co najmniej 2 dni między meczami.
- Kluby z pucharów europejskich nie grają w piątki ani poniedziałki.
- Kluby dzielące stadion nie grają u siebie tego samego dnia, chyba że nie ma innego terminu w pucharach. Wtedy mają różne godziny.
- Sędziowie meczów międzynarodowych pochodzą z innego kraju niż obie drużyny.
- Stadion, sędzia i drużyna nie mają dwóch meczów o tej samej godzinie, zgodnie z ograniczeniami `uk_match_*`.

### Przebieg meczu

- Skład to 11 zawodników z jednym bramkarzem w formacji z listy (np. 4-3-3, 4-4-2, 3-5-2), ławka według rozgrywek (9 lub 12, a 15 na mistrzostwach świata, Euro i Copa América) i wiersz trenera (`is_starting=false`, bez numeru).
- Numer koszulki jest stały przez okres gry w klubie albo przez cały turniej.
- Grać może tylko ktoś dostępny: w klubie tego dnia, bez kontuzji i zawieszenia, bez powołania na trwający turniej, po przerwie od poprzedniego meczu.
- Bramki pochodzą z rozkładu Poissona: bazowo 1,55 dla gospodarza, 1,15 dla gościa i 1,32 na neutralnym stadionie, a każdą wartość zmienia ukryta forma drużyny w danej edycji. Dla seeda 42 wychodzi ok. 2,75 gola na mecz i 24% remisów.
- Doliczony czas wynosi 1-5 minut po 1. połowie i 2-8 po 2. Godziny meczu i zdarzeń są z nim zgodne.
- Minuty zdarzeń to 1-45, 46-90, 91-105 i 106-120, a doliczone trafiają do `stoppage_minute`. `sequence_no` rośnie przez cały mecz.
- Dogrywka i karne są tylko w etapach `knockout`, gdy regulamin ich wymaga. W dwumeczu tylko w rewanżu, przy remisie w agregacie.
- Strzelcami są najczęściej napastnicy, potem pomocnicy i obrońcy. Asystent jest inny niż strzelec.
- Działać może tylko zawodnik na boisku. Zmieniony nie wraca, wyrzucony nie jest zastępowany. Wyjątkiem jest zmiana bramkarza po czerwonej kartce, kosztem zawodnika z pola.
- Zmiana to para `substitution_off` i `substitution_on` w tej samej minucie. `Edition.substitution_limit` wynosi 5 w ligach i Copa América, a 6 w pozostałych rozgrywkach, gdzie szósta zmiana jest możliwa tylko w dogrywce. W regulaminowym czasie są najwyżej 3 okienka plus przerwa.
- Ok. 10% goli pada z rzutu karnego (`penalty_goal`), a ok. 3% to samobóje (`own_goal`). Niewykorzystane karne mają nowy typ `penalty_missed`, a gole anulowane przez VAR mają `is_cancelled=true` i opis.
- Seria karnych ma okres `penalties` i minutę 120 (90, gdy nie było dogrywki). To 5 strzałów i seria nagłej śmierci, a kończy się, gdy jest rozstrzygnięta. Strzelają zawodnicy, którzy skończyli mecz na boisku.
- Faule są zapisywane tylko wtedy, gdy kończą się kartką lub karnym.
- Żółtych kartek jest ok. 3,8 na mecz. Druga żółta daje `yellow_card` i `red_card` w tej samej minucie.
- Czerwona kartka to 1 mecz pauzy, a bezpośrednia czasem 3. Jeden mecz pauzy dają też żółte kartki: 5 w LaLiga i Serie A, 4 w Ekstraklasie, 3 w pucharach i UEFA, 2 na turniejach. Kary dotyczą tych samych rozgrywek.
- Część kontuzji powstaje w meczu (wymuszona zmiana), a część na treningu. Nie nakładają się, a `end_date` jest pusty tylko dla urazu trwającego „teraz”.

### Statusy meczów

| Status | Kiedy |
| --- | --- |
| `finished` | Mecz skończył się przed datą „teraz” |
| `in_progress` | Trwa 1 listopada 2026 o 18:30. Jest co najmniej po jednym w 1. połowie, w przerwie i w 2. połowie; ma częściowe zdarzenia i nie wlicza się do tabel |
| `scheduled` | Przyszły, bez składów i zdarzeń |
| `postponed`, `cancelled` | Kilka meczów ligowych sezonu 2026/27, bez składów, poza tabelami. W starszych edycjach ich nie ma, żeby tabele były kompletne |

### Tabele (`EditionParticipation`)

- Punkty (3/1/0) liczą się tylko z lig, faz ligowych i grup. Bramki obejmują wszystkie mecze, bez serii karnych.
- `stage_reached` to nazwa ostatniego rozegranego etapu.
- `final_position` to miejsce w tabeli ligi, a w pucharach i turniejach tylko miejsca medalowe. W edycjach w toku jest puste.
- O kolejności decydują punkty, różnica bramek i bramki zdobyte.

### Redaktorzy

- Mecze, składy i zdarzenia edytuje ktoś z uprawnieniem `match_editor` do danej edycji albo danego meczu, nadanym przed zmianą.
- Zablokowani redaktorzy nie mają aktywnych uprawnień.
- Daty `created_at` i `updated_at` są zgodne z historią, nie z chwilą uruchomienia seedera.

## Usunięta kolumna `score_impact`

Kolumna `EventType.score_impact` została usunięta ze schematu, encji i `TestDataSeeder`. Ta kolumna gubiła znaczenie zdarzeń:

- `own_goal = -1` sugerował, że gol samobójczy zmniejsza wynik własnej drużyny, a on dodaje bramkę rywalowi.
- `penalty_goal = +1` był fałszywy w serii karnych, która nie zmienia `home_goals`.

Wynik wynika teraz z typu i okresu zdarzenia, bez anulowanych:

- `goal` i `penalty_goal` poza okresem `penalties` dają bramkę drużynie zawodnika.
- `own_goal` daje bramkę drużynie przeciwnej.
- `penalty_goal` w okresie `penalties` liczy się do `home_penalties` lub `away_penalties`.

Zmiana wymaga odtworzenia bazy (`docker compose down -v`), bo schemat ładuje się tylko do pustej bazy.

## Uproszczenia i ograniczenia

- **Sprawdzone uruchomienie:** seed 42 na PostgreSQL zapisuje się w ok. 47 sekund. Pozostałych seedów nie zapisywano do bazy.
- **Dane katalogu nie były sprawdzane w źródłach.** Lata założenia, pojemności, nazwy stadionów i daty edycji mogą zawierać błędy.
- Puchary krajowe zaczynają się od Round of 32, a UEFA bez eliminacji.
- Promocja z niższej ligi jest losowa, bo te ligi nie mają własnych meczów. Kluby spoza trzech lig grają tylko w pucharach europejskich.
- Faza ligowa UEFA nie używa koszyków ani limitu 2 rywali z jednego kraju, ale unika par z tego samego kraju. Drabinka turniejów nie odwzorowuje oficjalnej tabeli przydziału trzecich drużyn.
- Losowanie grup turniejów nie rozdziela konfederacji.
- Zawodnik powołany na turniej nie gra w klubie do końca turnieju, nawet jeśli jego drużyna odpadła wcześniej.
- `last_login_at` redaktora jest losowy i nie wynika z dat jego edycji.
- Czas meczów nie uwzględnia stref czasowych, bo kolumny to `TIMESTAMP` bez strefy.
