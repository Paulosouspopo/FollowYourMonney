# Déploiement (Oracle Cloud, offre gratuite)

Un seul serveur fait tout tourner avec `docker compose` :

```text
Internet ──443──▶ web (Caddy : HTTPS, front statique)
                    └── /api/* ──▶ api (Spring Boot, 8080) ──▶ db (PostgreSQL 16)
```

Front et API partagent la même origine. Il n'y a donc ni CORS ni souci de cookie (le cookie de session est `SameSite=Strict`), et le service worker fonctionne pour le push. Seul Caddy est exposé ; la base et l'API restent sur le réseau Docker interne. Les jobs (cours horaires, 23 h 30) tournent en continu, car le serveur ne se met jamais en veille.

Le même dossier servira pour un VPS payant plus tard : seule la machine change.

---

## 1. Créer le serveur

1. Créer un compte sur <https://www.oracle.com/cloud/free/>. Une carte bancaire est demandée pour vérifier l'identité, rien n'est débité tant qu'on reste dans l'offre « Always Free ».
   - **Région d'origine** : France Central (Paris) ou France South (Marseille). Elle ne peut plus être changée ensuite.
2. *Compute → Instances → Create instance* :
   - Image : **Ubuntu 24.04** (Canonical).
   - Shape : **VM.Standard.A1.Flex** (Ampere, ARM), **2 OCPU / 12 Go**. L'offre gratuite va jusqu'à 4 OCPU / 24 Go au total.
   - Réseau : laisser créer un VCN avec un sous-réseau public et **cocher l'adresse IPv4 publique**.
   - Clé SSH : téléverser ta clé publique (`~/.ssh/id_ed25519.pub`) ou télécharger celle qu'Oracle génère.
   - Si le message « Out of capacity » apparaît, réessayer plus tard ou changer de *availability domain*. C'est fréquent.
3. Noter l'**adresse IP publique** de l'instance.

> **Instances inactives :** Oracle peut récupérer une instance gratuite qui reste très peu utilisée pendant 7 jours. Passer le compte en « Pay As You Go » évite ce risque. On reste gratuit dans les limites « Always Free », mais il vaut mieux créer une **alerte de budget à 1 €** dans *Billing → Budgets*.

## 2. Ouvrir les ports 80 et 443

Il y a **deux pare-feu**, et il faut ouvrir les deux.

1. **Oracle** : *Networking → Virtual Cloud Networks → (ton VCN) → Security Lists → Default* → *Add Ingress Rules* :
   - Source `0.0.0.0/0`, TCP, port de destination `80`.
   - Source `0.0.0.0/0`, TCP, port de destination `443`.
2. **Ubuntu** : les images Oracle bloquent tout sauf SSH avec iptables. Une fois connecté (`ssh ubuntu@<IP>`) :
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

## 3. Nom de domaine gratuit

HTTPS demande un nom de domaine.

1. Aller sur <https://www.duckdns.org>, se connecter et créer un sous-domaine (ex. `mon-fym`).
2. Mettre l'IP publique du serveur dans le champ *current ip* → **update ip**.
3. Le domaine est alors `mon-fym.duckdns.org`.

Un vrai domaine (≈ 10 €/an) pourra remplacer celui-ci plus tard : il suffira de changer `DOMAIN` dans `.env`.

## 4. Installer Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit          # puis se reconnecter pour que le groupe docker soit pris en compte
```

## 5. Récupérer le code

Les deux dépôts doivent être **côte à côte** dans `~/fym` :

```bash
mkdir -p ~/fym && cd ~/fym
git clone https://github.com/Paulosouspopo/FollowYourMonney.git tracker
git clone https://github.com/Paulosouspopo/FollowYourMoney-ui.git tracker-ui
```

Si les dépôts sont privés : créer un *fine-grained token* GitHub en lecture seule sur ces deux dépôts, puis cloner avec `https://<token>@github.com/...`.

## 6. Configurer

```bash
cd ~/fym/tracker/deploy
cp .env.example .env
openssl rand -base64 24   # → DB_PASSWORD
openssl rand -base64 48   # → JWT_SECRET
nano .env                 # DOMAIN, DB_PASSWORD, JWT_SECRET, ADMIN_EMAILS
chmod 600 .env
```

**Emails :** avec `MAIL_ENABLED=false`, rien n'est envoyé et les liens (vérification d'adresse, mot de passe oublié) sont écrits dans les logs :

```bash
docker compose logs api | grep -i http
```

Pour de vrais emails, le plus simple est un SMTP gratuit comme Brevo (300 emails/jour) : `MAIL_ENABLED=true`, `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=587`, avec l'identifiant et la clé SMTP de Brevo.

## 7. Lancer

```bash
cd ~/fym/tracker/deploy
docker compose up -d --build     # 1er build : 5 à 10 min (Maven + npm)
docker compose ps
docker compose logs -f api       # attendre « Started TrackerApplication »
```

Ouvrir `https://<DOMAIN>`. Le certificat est obtenu automatiquement au premier accès (quelques secondes).

**À vérifier au premier démarrage :**
- `docker compose logs web` : pas d'erreur de certificat. Si le certificat échoue, les ports 80 et 443 sont fermés (étape 2) ou le DNS pointe mal (étape 3).
- `docker compose logs api | grep -i yahoo` : les cours arrivent. Yahoo limite parfois les IP de datacenter (erreur 429). Si c'est le cas, le noter : c'est le sujet de la « source de cours de secours ».

## 8. Mettre à jour

```bash
cd ~/fym/tracker && git pull
cd ~/fym/tracker-ui && git pull
cd ~/fym/tracker/deploy && docker compose up -d --build
docker image prune -f
```

Les migrations Flyway s'appliquent seules au démarrage de l'API.

## 9. Sauvegardes

```bash
chmod +x ~/fym/tracker/deploy/backup.sh
crontab -e
# ajouter :
15 3 * * * ~/fym/tracker/deploy/backup.sh >> ~/fym/backups/backup.log 2>&1
```

Les sauvegardes restent sur le serveur, 14 jours glissants. Pour en garder une copie ailleurs :

```bash
scp ubuntu@<IP>:~/fym/backups/fym-AAAA-MM-JJ.dump .
```

**Restaurer** une sauvegarde :

```bash
docker compose exec -T db pg_restore -U fym -d portfolio_db --clean --if-exists < fym-AAAA-MM-JJ.dump
docker compose restart api
```

## 10. Reprendre les données de dev (optionnel)

Sur le PC (base Docker locale, port 5433) :

```bash
docker exec tracker-postgres-1 pg_dump -U postgres -d portfolio_db --format=custom --no-owner > dev.dump
scp dev.dump ubuntu@<IP>:~/fym/
```

Sur le serveur, **avant la création de tout compte** :

```bash
cd ~/fym/tracker/deploy
docker compose stop api
docker compose exec -T db pg_restore -U fym -d portfolio_db --clean --if-exists --no-owner < ~/fym/dev.dump
docker compose start api
```

## Commandes utiles

| Besoin | Commande |
|---|---|
| État des services | `docker compose ps` |
| Logs de l'API | `docker compose logs -f --tail 200 api` |
| Redémarrer l'API | `docker compose restart api` |
| Console SQL | `docker compose exec db psql -U fym -d portfolio_db` |
| Mémoire / CPU | `docker stats` |
