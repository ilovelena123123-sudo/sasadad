SKLEP – Paper 1.21.4

WYMAGANIA
- Java 21
- Paper 1.21.4
- Vault oraz plugin ekonomii obsługujący Vault (np. EssentialsX)

BUDOWANIE JAR
1. Zainstaluj Maven.
2. Otwórz terminal w folderze projektu.
3. Wykonaj: mvn clean package
4. Gotowy plik: target/Sklep.jar
5. Wrzuć Sklep.jar do folderu plugins/ i uruchom serwer.

KOMENDY
/sklep – sklep
/sklep reload – przeładowanie config.yml
/sklep-admin – panel administratora
/czasgry – czas gry do wydania

UWAGA – STATUS IMPLEMENTACJI
To jest projekt źródłowy / wersja bazowa. Produkty są przechowywane w plugins/Sklep/data.yml.
Administrator dodaje kategorię, trzymając w ręce nazwany przedmiot i klikając przycisk dodawania.
W kategorii dodaje produkt, trzymając go w ręce.
Ceny produktów można edytować w data.yml (buy/sell); ceny sklepu za czas podaje się w sekundach.
Czas gry jest naliczany online, także AFK.

W tej wersji nie ma jeszcze edytora cen przez kowadło, limitów zakupów, promocji/rabatów
ani pełnej obsługi ustawiania cen w GUI. Nie należy traktować jej jako finalnej wersji
produkcyjnej bez testów na kopii serwera.
