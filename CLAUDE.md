# FollowYourMonney — Backend

## Contexte général
Application de suivi de portefeuille d'investissement (actions, crypto, ETF).
L'utilisateur ajoute manuellement ses transactions (achat/vente/dividende),
le backend récupère les prix des actifs via Yahoo Finance (API publique non
officielle) et calcule la valorisation du portefeuille dans le temps.

Objectif produit : une app moderne, claire, avec des graphiques d'évolution,
des vues par portefeuille/actif/transaction, et à terme des notifications
(hausse/baisse, bilan journalier).

## Stack
- Java 21, Spring Boot 3.x
- PostgreSQL 16 (via Docker, `compose.yml`)
- JPA/Hibernate en `ddl-auto=validate` : le schéma est géré par **Flyway**
  (`src/main/resources/db/migration`). Toute modification d'entité = une
  nouvelle migration `V<n>__description.sql` (ne JAMAIS modifier une
  migration déjà appliquée). Les tests d'intégration appliquent les
  migrations puis Hibernate valide : un oubli fait échouer le build.
- Base existante sans historique Flyway → baselinée en V1.
- CI : GitHub Actions (`.github/workflows/ci.yml`, `./mvnw verify`).
- Lombok
- Tests : JUnit 5 + Mockito

## Historique des décisions importantes (ne pas revenir en arrière dessus)
- ❌ **Alpha Vantage et CoinGecko supprimés** → remplacés par **Yahoo Finance**
  (endpoints non officiels `query1.finance.yahoo.com`, pas de clé API, pas de
  rate limit connu à ce jour).
- ❌ **AssetTemplate / AssetExternalService (whitelist figée) supprimés** →
  remplacés par une **recherche live** via `GET /v1/finance/search?q=...`.
  L'utilisateur tape un nom, choisit dans les résultats Yahoo, le `symbol`
  exact retourné (ex: `TTE.PA`, `BTC-EUR`) est stocké tel quel dans `Asset`.
  Ne jamais laisser l'utilisateur saisir un symbole à la main.
- **Devise fixe : EUR** comme devise de base pour toute agrégation/dashboard.
  `totalAmountInBaseCurrency` a été retiré de `Transaction` : on stocke
  `totalAmount` + `currency` (devise réelle de la transaction), et on
  convertit à la volée via `ExchangeRateService` uniquement à l'affichage/
  agrégation.
- **CUMP (coût unitaire moyen pondéré)** pour le calcul de prix de revient
  lors des ventes partielles.
- **Dashboard** : hybride — valorisation "live" calculée à la volée
  (`PortfolioValuationService`) + **snapshots journaliers stockés**
  (`PortfolioSnapshot`) pour la courbe d'évolution dans le temps.

## Architecture des entités clés
- `Asset` : un actif détenu dans un portefeuille précis (ex: BTC dans
  Portfolio A ET BTC dans Portfolio B = 2 lignes `Asset` distinctes).
  Le `symbol` doit être le symbole canonique Yahoo (ex: `BTC-EUR`, pas `BTC`).
- `AssetPrice` : une ligne par `(symbol, price_date)` (contrainte
  `uk_asset_prices_symbol_price_date`), jour de bourse dans le fuseau de la
  place. Le jour courant est mis à jour en place (upsert) par le job horaire.
  Sert aussi aux **paires de devises** (`USDEUR=X`) : l'historique FX est une
  série de marché comme une autre.
- `PriceHistoryCoverage` : intervalle de jours déjà DEMANDÉ à Yahoo par
  symbole (contigu, jusqu'à hier). `PriceHistoryService.ensureCoverage` ne
  télécharge que ce qui manque.
- `Transaction` : liée à un `Asset`. Types : BUY, SELL, DIVIDEND. Champs
  `quantity`, `pricePerUnit`, `fees`, `totalAmount`, `currency`,
  `transactionDate`.
- `ExchangeRate` : taux de change entre devises, avec historique
  (`fromCurrency`, `toCurrency`, `lastUpdated`).
- `PortfolioSnapshot` : valeur agrégée d'un portefeuille à une date donnée
  (en EUR), utilisé pour tracer la courbe d'évolution sans tout recalculer
  à chaque requête. Contrainte unique `(portfolio_id, date)`.

## Flux historique (transaction → prix → snapshots → courbe)
- `TransactionService` / `AssetService` publient `PortfolioHistoryChangedEvent
  (portfolioId, from)` ; `PortfolioHistoryListener` regroupe les événements
  d'une transaction et déclenche `PortfolioHistoryService.refresh` **après
  commit** (sinon le recalcul, en REQUIRES_NEW, ne voit pas les données).
- `refresh` = `ensureCoverage` (HTTP, hors transaction) puis `rebuild`
  (DB + mémoire, idempotent : delete + reinsert depuis `from`).
- `rebuild` : 5 requêtes par portefeuille (dont les mouvements d'argent) puis parcours jour par jour en
  mémoire. **Aucune requête dans la boucle.** Le CUMP est dans `PositionState`,
  partagé avec `PortfolioValuationService` : courbe et dashboard doivent
  toujours donner le même chiffre pour aujourd'hui.
- Taux : `Transaction.exchangeRateToEur` = taux du **jour de l'opération**
  (`ExchangeRateService.getRateAsOf`). Snapshots passés = taux historique du
  jour ; aujourd'hui = taux courant.
- Pas de cours de marché pour un jour → dernier cours connu ; aucun cours du
  tout → prix de la dernière transaction (jamais 0 : faux décrochage).
- `MarketDataJobs` : rattrapage au démarrage + 23h30, cotations + point du jour
  chaque heure. Désactivé en test (`app.scheduling.enabled=false`).
- Synchrone volontairement (dev). Passage en async : exécuter
  `PortfolioHistoryListener.refreshDirty` sur un executor.

## Règles métier des transactions
- `quantity > 0` et `pricePerUnit > 0` pour tous les types (`totalAmount =
  quantity × pricePerUnit`, y compris un dividende : le front envoie
  `quantity = 1` + montant total).
- Pas de vente à découvert : à aucune date une vente ne dépasse la quantité
  détenue (vérifié à la création, la modification ET la suppression).
- Devise absente → devise de cotation de l'actif.
- Ordre de rejeu : `Transaction.CHRONOLOGICAL` (date puis `createdAt`),
  identique pour la valorisation et l'historique.
- Aucun cours de marché → valorisation au prix de la dernière transaction
  (`priceMissing = true`), dashboard comme courbe.

## Liquidités et livrets
- `CashMovement` (versement, retrait, intérêts, frais de compte), en EUR,
  montant toujours positif (le type donne le sens). API :
  `/api/portfolios/{id}/cash-movements`.
- `Portfolio.cashTracking` : le solde entre dans la valeur. Forcé pour un
  LIVRET (`PortfolioRules`), optionnel ailleurs ; mouvements refusés si
  désactivé ; le changer relance un recalcul complet de l'historique.
- Solde (`CashState`, partagé valorisation/historique comme `PositionState`) =
  versements - retraits + intérêts - frais de compte - (achats + frais)
  + (ventes - frais) + (dividendes - frais). Peut être négatif sur un compte
  (versements non saisis) ; jamais sur un livret (refusé, y compris via une
  suppression).
- Valeur = positions + liquidités ; investi = prix de revient + liquidités
  (la plus-value latente reste celle des positions) ; % latent calculé sur le
  seul prix de revient. Mêmes règles dans les snapshots.
- Un livret ne détient pas d'actifs : transactions refusées, et un
  portefeuille avec actifs ne peut pas devenir un livret.
- Répartition : catégorie = type d'actif, `LIVRET`, ou `LIQUIDITES`.

## Sécurité
- **Session** : JWT d'accès court (15 min, en-tête `Authorization`) + jeton
  de renouvellement opaque (30 j) dans le cookie `fym_refresh` (HttpOnly,
  SameSite=Strict, Path=/api/auth, Secure en prod via
  `app.auth.cookie-secure`). En base : empreinte SHA-256 seulement
  (`refresh_tokens`, `SecureTokens`).
- **Rotation** à chaque `/api/auth/refresh` ; un jeton déjà remplacé depuis
  plus de `AuthService.ROTATION_GRACE` (onglets simultanés) = vol présumé →
  toutes les sessions de l'utilisateur révoquées.
- **Comptes** : inscription `POST /api/auth/register` (plus de `POST
  /api/users` public), email à vérifier avant connexion (403 + code
  `EMAIL_NOT_VERIFIED`), mot de passe oublié / réinitialisation (liens à usage
  unique, `account_tokens`), changement de mot de passe et réinitialisation
  = toutes les sessions révoquées. Réponses identiques pour un email inconnu
  (pas d'énumération des comptes).
- **Emails** : `EmailSender` → SMTP si `app.mail.enabled=true` (Mailpit en
  dev : `docker compose up -d`, http://localhost:8025), sinon écrits dans les
  logs (le lien de vérification s'y trouve).
- **Limitation de débit** en mémoire (`RateLimiter`, 429 + Retry-After) sur
  les endpoints publics sensibles ; désactivée dans le profil test.
- Non authentifié → **401 JSON** (`JsonSecurityErrorHandler`) : le front s'en
  sert pour renouveler la session. Rôle insuffisant → 403.
- **Rôles en base** (`users.role`) ; `app.admin.emails` promeut ADMIN au
  démarrage (`AdminBootstrap`). ADMIN requis pour `/api/admin/**`,
  `POST /api/asset-prices/**` et les `@PreAuthorize`.
- Derrière un reverse proxy en prod : configurer
  `server.forward-headers-strategy` pour que la limitation par IP voie la
  vraie adresse du client.

## Points sensibles / dette technique restante
- En local, DEUX PostgreSQL écoutent sur 5432 : le service Windows natif
  (`postgresql-x64-16`, qui contient les données de dev) et le conteneur
  `compose.yaml` (vide). À unifier.
- Le job horaire recalcule le point du jour de TOUS les portefeuilles
  (OK à petite échelle ; à cibler sur les portefeuilles détenant les
  symboles mis à jour si le volume grossit).

## Conventions de code à respecter
- DTOs : suffixes `CreateRequest` / `UpdateRequest` / `Response` par domaine,
  mappers dédiés (`XxxMapper`), validations Bean Validation + règles métier
  dans `validateBusinessRules(...)`.
- Exceptions centralisées dans `/shared` (déjà en place, les réutiliser
  plutôt qu'en recréer).
- Tests unitaires obligatoires sur tout ce qui touche à
  `PortfolioValuationService` (les scénarios couverts : achat simple,
  achats multiples à prix différents/CUMP, vente partielle, vente totale,
  dividende, prix manquant, devise étrangère) — c'est l'endroit où un bug
  silencieux affiche des chiffres faux à l'utilisateur.

## Ce qu'il ne faut PAS faire
- Ne pas réintroduire Alpha Vantage / CoinGecko / AssetTemplate.
- Ne pas faire choisir un symbole à la main par l'utilisateur.
- Ne pas proposer de code "iso" avec des méthodes qui n'existent pas déjà
  dans le repository/service — toujours vérifier l'existant avant de
  fournir une modification.
- Ne pas casser la compatibilité EUR-first du dashboard.

## Repo
https://github.com/Paulosouspopo/FollowYourMonney