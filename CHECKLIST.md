# Čeklista za implementaciju Firebase Studio preporuka

## I. Project Structure and Setup

### Gradle Konfiguracija:

- [x] 1. Ažurirati `compileSdk` i `targetSdk` sa 35 na 36 u `app/build.gradle.kts`
- [x] 2. Omogućiti minifikaciju (`isMinifyEnabled = true`) u release build-u
- [x] 3. Ažurirati `kotlinCompilerExtensionVersion` na najnoviju stabilnu verziju
- [x] 4. Ažurirati `sourceCompatibility` i `targetCompatibility` sa Java 11 na Java 17
- [x] 5. Premestiti plugin `com.google.gms.google-services` iz `build.gradle.kts` u `settings.gradle.kts`
- [x] 6. Ažurirati `gradle.properties` sa JVM argumentima za optimizaciju build vremena

## II. App Architecture and Code Quality

- [ ] 7. Implementirati Hilt za dependency injection
- [ ] 8. Poboljšati razdvajanje odgovornosti (UI, ViewModels, repositories)
- [ ] 9. Implementirati use cases/interactors za encapsulation business logike
- [ ] 10. Poboljšati upravljanje stanjem koristeći Flow/StateFlow
- [ ] 11. Poboljšati obradu grešaka koristeći sealed klase

## III. UI and UX

- [ ] 12. Podeliti UI u manje, reusable komponente
- [ ] 13. Pratiti state hoisting tehnike u Compose
- [ ] 14. Poboljšati performanse izbegavajući nepotrebne recompositions
- [ ] 15. Osigurati pristupačnost (accessibility)

## IV. Data Layer

- [ ] 16. Poboljšati abstrakciju repozitorijuma
- [ ] 17. Implementirati strategije za keširanje
- [ ] 18. Optimizovati Firestore upite

## V. Security

- [ ] 19. Sigurno čuvanje API ključeva
- [ ] 20. Enkriptovanje osetljivih podataka
- [ ] 21. Osigurati bezbednu autentikaciju