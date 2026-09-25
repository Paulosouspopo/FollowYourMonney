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

## Import de relevés (`imports/`)
- API : `POST /api/imports/inspect` (format détecté + extrait), `POST
  /api/imports/preview` (multipart : fichier + partie JSON `options`), `POST
  /api/imports/commit`. Le fichier n'est jamais stocké.
- `CsvReader` : UTF-8 (BOM) ou ISO-8859-1, séparateur détecté, guillemets.
- Un `StatementParser` par courtier (Fortuneo, Trade Republic, Binance) +
  `GenericParser` (colonnes associées par l'utilisateur). Sans réseau ni base :
  produisent des `ImportedOperation` (READY / IGNORED avec raison).
  - Fortuneo : pas d'ISIN → recherche par libellé ; OST de coupon ignorées.
  - Trade Republic : `transaction_id` = référence ; MIGRATION ignorée ;
    dividende brut = net + taxe ; horodatage UTC → heure de Paris.
  - Binance : grand livre → jambes regroupées (même Remark ou < 2 s) ;
    crypto→crypto = vente + achat estimés au cours de clôture du jour ;
    récompenses (Crypto Box...) et conversion de leurs poussières ignorées.
- `AssetResolver` : mémoire (`import_asset_mappings`) → ISIN → paire
  `CODE-EUR` → `CODE-USD` → recherche par nom. Seuls REMEMBERED/CERTAIN sont
  acceptés d'office ; l'utilisateur valide le reste (jamais de saisie de symbole).
- Doublons : `external_ref` (identifiant du courtier ou empreinte de la ligne)
  sur `transactions` / `cash_movements`, + heuristique (même jour, type,
  actif, quantité / montant). Réimporter le même relevé ne crée rien.
- Validation : tout passe par `TransactionService` / `CashMovementService`
  (mêmes règles qu'une saisie), dans une transaction : une ligne invalide
  annule tout (`ImportRowException` → 400, champ `row:<id>`). Un seul
  recalcul d'historique à la fin.
- Tests sur des relevés FICTIFS (`src/test/resources/imports`). Les exports
  réels de l'utilisateur sont dans `examples-imports/` : gitignoré, ne JAMAIS
  les commiter ni en recopier le contenu.

## Investissements programmés (`plan/`)
- `InvestmentPlan` : BUY (achat d'un actif, pas sur un livret) ou DEPOSIT
  (versement, compte avec suivi des liquidités). Montant par échéance en
  EUR frais compris, fréquence (`PlanFrequency`, n-ième échéance calculée
  depuis la date de début), date de fin, parts entières ou fractionnées, pause.
- `PlanExecutor` : chaque échéance échue → transaction (cours de clôture du
  jour via `MarketPriceLookup`, prix estimé) ou versement, référence
  `PLAN:<id>:<date>` (idempotent). Un plan par transaction sous verrou ;
  un échec annule le passage et est noté dans `lastError` (retenté le soir).
  Parts entières : échéance sautée si le montant ne suffit pas.
- `PlanJobs` : au démarrage + 21h30. Création/modification → exécution
  immédiate des échéances passées (un plan démarré dans le passé recrée son
  historique). Reprise après pause : la période de pause n'est pas rattrapée.
- Après la 1re échéance : type, actif, fréquence et début figés (400).
- Import : un achat du relevé à ±4 jours d'une échéance de plan sur le même
  actif est proposé comme doublon (l'exécution réelle du courtier).

## Notifications (`notification/`)
- `NotificationService.notify` : point d'entrée unique ; toujours dans la
  boîte de réception (`notifications`), + email si demandé, + push (après
  commit) sauf si l'utilisateur l'a coupé ou pendant ses heures calmes
  (`NotificationPreferences`, plage pouvant passer minuit).
- **Web Push** (`notification/push`) : chiffrement RFC 8291 (aes128gcm) et
  VAPID RFC 8292 codés avec la JDK seule (`WebPushCrypto`, testé sur le
  vecteur de la RFC). Clés VAPID : `app.push.vapid-*` (à fixer en prod),
  sinon générées et gardées en base (`app_secrets`). `PushService` supprime
  un abonnement refusé en 404/410. API : `/api/push/public-key`,
  `/api/push/subscriptions`, `/api/push/unsubscribe`, `/api/push/test`,
  `/api/notification-preferences`.
- `AlertRule` : périmètre GLOBAL / PORTFOLIO / ASSET (actif détenu ou non) ;
  conditions RISES / FALLS / MOVES (% sur DAY/WEEK/MONTH), ABOVE / BELOW
  (EUR), PROFIT_ABOVE / LOSS_BELOW (plus-value latente / prix de revient des
  positions), NEW_HIGH / NEW_LOW (actif seulement, clôtures WEEK/MONTH/YEAR,
  seuil 0), WEIGHT_ABOVE (actif ou portefeuille, % du patrimoine). Nom libre
  (`label` = titre de la notification), canaux push/email, sourdine
  (`mutedUntil`, `POST /api/alert-rules/{id}/mute`).
- `AlertEvaluator` : appelé par `MarketDataJobs` juste après la mise à jour
  horaire des cours. Portefeuille/patrimoine : variation de PLUS-VALUE
  rapportée à la valeur de départ (snapshot) → un versement/achat ne
  déclenche rien. Actif : cours EUR vs clôture passée. Anti-répétition :
  désarmée après déclenchement, réarmée quand la condition retombe (ou
  chaque nouveau jour pour une variation sur 1 jour ou un record).
- `PlanExecutor.runDuePlans` prévient aussi d'un NOUVEAU problème de plan
  (`lastError` changé), pas à chaque nouvel essai.
- `ReportService` : rapport DAILY / WEEKLY (lundi) à l'heure choisie
  (Europe/Paris, `TimeZones`), job à hh:15 ; aperçu via
  `/api/report-settings/preview`.
- `PlanExecutor.runDuePlans` notifie les échéances exécutées par le job.
- Purge des notifications de plus de 180 jours.

## Actifs suivis et fiche d'un actif (`watchlist/`)
- `WatchlistItem` : symbole Yahoo suivi par un utilisateur, détenu ou non
  (100 max). Ajout = cotation vérifiée + 1 an d'historique téléchargé.
- La cotation horaire (`AssetPriceService.updateAllAssetPrices`) couvre les
  symboles détenus, suivis ET visés par une alerte active.
- `GET /api/watchlist` lit la base (dernier cours, variation vs clôture
  précédente, 30 jours pour la mini-courbe, quantité détenue, alertes) :
  pas d'appel réseau. `GET /api/market/detail?symbol=` (cotation live,
  plus bas/haut 1 an via `MarketPriceLookup.closingRange`, lignes détenues,
  alertes) et `GET /api/market/history?symbol=&range=1M|3M|6M|1Y|5Y`.
  Symbole en paramètre de requête (`^FCHI`, `EURUSD=X`).

## Performance (`performance/`)
- Chaque snapshot porte `net_flow` (flux externe du jour) et
  `performance_value` (valeur, découvert de liquidités compté à 0), calculés
  dans le `rebuild` (`PerformanceFlows`) :
  - compte avec suivi des liquidités : versements - retraits, + découvert
    apparu (achat non financé = apport implicite, pas une perte) ; intérêts,
    dividendes et frais = rendement ;
  - compte sans suivi : achat (frais compris) = apport, vente et dividende
    nets = retrait.
- Snapshots sans flux (antérieurs à V9) : `catchUp` recalcule entièrement
  les portefeuilles concernés.
- `PerformanceCalculator` (pur, testé) : TWR en Dietz journalier
  (apport en début de journée, retrait en fin de journée), XIRR (Newton puis
  dichotomie). Annualisation seulement au-delà d'un an.
- `GET /api/performance[/portfolios/{id}]?period=1m|3m|ytd|1y|3y|5y|all&benchmark=`
  : TWR, rendement de l'argent (MWR / XIRR), gain, apports nets, série
  jour par jour, indice rebasé en EUR (paire FX historique), classement des
  portefeuilles en vue globale.

## Devise d'affichage
- `DisplayCurrency` : EUR, USD, GBP, CHF. `GET /api/exchange-rates/display`
  (taux du jour). `?currency=` sur `/api/dashboard` : la courbe est convertie
  au taux de chaque jour (`curveCurrency`) ; les totaux restent en EUR (le
  front les convertit au taux du jour). Les calculs restent en EUR.

## Qualité des saisies (`quality/`)
- `QualityRules` (pur, testé) : écart prix / clôture du jour toléré 15 %
  (25 % crypto) ; au-delà de ×5 (ou ÷5) = `SPLIT_SUSPECTED` (division
  d'actions non reflétée : les séries Yahoo sont corrigées rétroactivement,
  SANS événement de split ; ou zéro en trop), jamais proposé comme « prix à
  utiliser » ; PEA : crypto ou cotation hors UE (suffixes Yahoo).
- `/api/data-checks/transaction` pendant la saisie (peut télécharger
  l'historique), `/api/data-checks` audit de l'existant en mémoire sans
  réseau (divisions regroupées par actif si écart comparable),
  `/api/data-checks/dismiss` (« c'est normal », table `data_check_dismissals`).

## Revenus passifs (`income/`)
- Dividendes par action : `MarketDataProvider.getDividends` (Yahoo
  `events=div`, cache 12 h par symbole dans `IncomeService`).
- `/api/income` : projection 12 mois = dividendes des 12 derniers mois ×
  quantité (`DividendProjection`, pur) + livrets (solde × taux, versé au
  31/12) ; reçu par mois (DIVIDEND nets + mouvements INTEREST) sur 24 mois ;
  prochains versements estimés (dates de l'an dernier + 1 an).

## Objectifs (`goal/`)
- `goals` (V10) : montant, échéance facultative, patrimoine ou portefeuille.
  `/api/goals` renvoie valeur actuelle du périmètre + versements programmés
  mensuels ; la projection (intérêts composés, délai, effort) est côté front.

## Remplacer l'actif d'une ligne
- `POST /api/portfolios/{id}/assets/{assetId}/replace {symbol}`
  (`AssetReplacementService`) : opérations conservées, symbole / nom /
  devise / type mis à jour ; fusion si le nouvel actif est déjà dans le
  portefeuille (refusée si une vente dépassait la quantité détenue) ; plans et
  mémoire d'import suivent ; historique recalculé.

## Fiscalité (`tax/`)
- `TaxEngine` (pur, testé) : titres hors PEA au PMP par symbole tous comptes
  (même règle que `PositionState`), report des moins-values sur 10 ans ;
  crypto 150 VH bis (gain = C - PTA × C / V, V = portefeuille crypto global au
  jour de la vente, cours en mémoire) ; échanges crypto contre crypto
  (vente + achat du même compte à ≤ 2 min, montants à ±5 %) non imposables et
  sans effet sur le PTA ; franchise de 305 €.
- `/api/tax?year=` (défaut : année écoulée) : cessions, dividendes, cases
  3VG/3VH, 2DC, 3AN/3BN, flat tax 30 %, PEA (5 ans depuis `opened_at` (V11)
  ou la 1re opération, plafond 150 000 €, versements estimés sans suivi des
  liquidités, 17,2 % en cas de retrait). Estimation : l'IFU fait foi.

## Tutoriels (`tutorial/`)
- `tutorial_states` (V12) : une ligne par compte, `auto_enabled` + clés des
  visites terminées (CSV, clés `[a-z0-9-]{1,40}`, 40 max). Les clés sont
  définies par le front (visites guidées) ; le back ne fait que les retenir.
- `GET /api/tutorials`, `POST /api/tutorials/{key}/complete`,
  `PUT /api/tutorials/settings {autoEnabled}`, `DELETE /api/tutorials` (tout
  revoir). Chaque appel renvoie l'état complet.

## Dev local : antivirus Avast
- Avast (« Web/Mail Shield », analyse HTTPS) re-signe tout le trafic HTTPS :
  Java refuse alors Yahoo (`PKIX path building failed`), git et Docker aussi.
- Contournement : lancer la JVM avec
  `-Djavax.net.ssl.trustStoreType=Windows-ROOT` (magasin de certificats
  Windows ; dans VS Code : `java.debug.settings.vmArgs` du `.vscode/settings.json`
  local), git avec `http.sslBackend=schannel` ; ou désactiver l'analyse
  HTTPS d'Avast.

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