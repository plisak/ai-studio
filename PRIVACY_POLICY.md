# Polityka Prywatności aplikacji „Kalkulator Złomu”

Ostatnia aktualizacja: 2026-06-01

Aplikacja **Kalkulator Złomu** (dostępna również jako kod źródłowy w serwisie GitHub) stawia prywatność swoich użytkowników na pierwszym miejscu. Poniższy dokument opisuje zasady dotyczące przetwarzania i ochrony danych.

### 1. Gromadzenie danych osobowych i telemetrycznych
* Aplikacja **nie zbiera, nie przechowuje ani nie przesyła** żadnych danych osobowych, kontaktowych, lokalizacyjnych ani identyfikacyjnych użytkownika.
* Nie korzystamy z żadnych zewnętrznych narzędzi analitycznych (takich jak Google Analytics, Firebase Analytics itp.) ani systemów śledzenia błędów, które przesyłałyby dane telemetryczne poza Twoje urządzenie.
* Aplikacja nie wymaga logowania ani zakładania konta.

### 2. Przechowywanie danych (Baza danych Room / SQLite)
* Wszystkie dane wprowadzane przez użytkownika — w tym cenniki metali i produktów złożonych, historia obliczeń oraz bieżący koszyk kalkulacji — są zapisywane **wyłącznie lokalnie** na Twoim urządzeniu w bezpiecznej bazie danych SQLite (Jetpack Room).
* Żadne z tych danych nie są przesyłane do chmur obliczeniowych, serwerów zewnętrznych ani podmiotów trzecich. Usunięcie aplikacji powoduje automatyczne usunięcie wszystkich zapisanych w niej danych lokalnych.

### 3. Eksport i Import danych (Backup JSON oraz CSV)
* Aplikacja umożliwia ręczny eksport i import bazy danych (w formacie JSON lub arkusza CSV kompatybilnego z programami Excel / Arkusze Google).
* Te operacje są inicjowane **wyłącznie i bezpośrednio przez użytkownika**. Generowane pliki są zapisywane w pamięci urządzenia w lokalizacji wybranej przez użytkownika i to użytkownik decyduje, komu i jak je udostępnia.

### 4. Uprawnienia systemowe Android
Aplikacja może wymagać dostępu do standardowych uprawnień systemu Android, takich jak:
* **Dostęp do plików / selektora plików (System Document Provider)**: Wyłącznie w celu odczytu/zapisu plików kopii zapasowej (JSON/CSV) przy wywołaniu przez użytkownika funkcji importu/eksportu. Aplikacja nie ma stałego dostępu do pamięci masowej w tle.

### 5. Kontakt i kod źródłowy
Projekt jest w pełni otwarty i transparentny. Kod źródłowy aplikacji jest publicznie dostępny w serwisie GitHub:
👉 [https://github.com/lisak-przemyslaw/kalkulator-zlomu](https://github.com/lisak-przemyslaw/kalkulator-zlomu)

W razie pytań dotyczących aplikacji lub polityki prywatności, prosimy o kontakt pod adresem e-mail: `lisak.przemyslaw@gmail.com`.
