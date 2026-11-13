#!/data/data/com.termux/files/usr/bin/bash
# Instala dependências e cria os atalhos do Termux:Widget.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
pkg install -y python android-tools termux-api git
mkdir -p ~/.shortcuts

cat > ~/.shortcuts/calibrar.sh <<EOT
#!/data/data/com.termux/files/usr/bin/bash
echo "Calibrando em 5s... larga o celular!"
sleep 5
python3 "$DIR/ghost.py" calibrar 45
termux-notification --title "Calibração" --content "Padrão salvo"
EOT

cat > ~/.shortcuts/monitor.sh <<EOT
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
python3 "$DIR/ghost.py" monitor
EOT

cat > ~/.shortcuts/monitor-reset.sh <<EOT
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
python3 "$DIR/ghost.py" monitor --reset
EOT

cat > ~/.shortcuts/mapa.sh <<EOT
#!/data/data/com.termux/files/usr/bin/bash
python3 "$DIR/ghost.py" mapa
python3 "$DIR/ghost.py" status
read -p "Enter pra fechar" _
EOT

chmod +x "$DIR/ghost.py" ~/.shortcuts/*.sh
echo
echo "Pronto! Atalhos criados: calibrar, monitor, monitor-reset, mapa."
echo "Adicione o widget do Termux:Widget na tela inicial."
echo "Teste:  python3 $DIR/ghost.py"
