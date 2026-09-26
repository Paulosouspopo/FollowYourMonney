#!/bin/sh
# Sauvegarde compressée de la base, 14 jours gardés. À planifier (crontab -e) :
#   15 3 * * * ~/fym/tracker/deploy/backup.sh >> ~/fym/backups/backup.log 2>&1
set -eu
cd "$(dirname "$0")"
DEST="$HOME/fym/backups"
mkdir -p "$DEST"
docker compose exec -T db pg_dump -U fym -d portfolio_db --format=custom > "$DEST/fym-$(date +%F).dump"
find "$DEST" -name 'fym-*.dump' -mtime +14 -delete
echo "$(date '+%F %T') sauvegarde OK"
