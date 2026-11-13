# ghosttouch

Detecta, mapeia e registra **toques fantasmas** da tela, direto pelo Termux.
Lê os toques brutos via ADB (Depuração por Wi-Fi), então enxerga mais do que um app comum.
**Só observa: não bloqueia nem mexe nos toques**, então não atrapalha o teclado.

## Instalação (Termux)

```bash
git clone https://github.com/SEU-USUARIO/ghosttouch ~/ghosttouch
cd ~/ghosttouch && bash install.sh
```

Depois, uma vez:
1. Opções do desenvolvedor → **Depuração por Wi-Fi** (precisa estar no Wi-Fi).
2. "Parear com código" → `adb pair localhost:PORTA_DO_PAREAMENTO`
3. Tela principal da depuração → `adb connect localhost:PORTA`
4. Confirme com `adb devices` (tem que aparecer `device`).

Instale também **Termux:API** (notificações) e **Termux:Widget** (atalhos).

## Uso

| Comando | O que faz |
|---|---|
| `python3 ghost.py calibrar [seg]` | Grava toques sozinhos com o celular parado (padrão 45 s) |
| `python3 ghost.py monitor` | Vigia, notifica e registra rajadas. Aprende sozinho o padrão de cada crise |
| `python3 ghost.py monitor --reset` | Além disso, apaga/acende a tela quando detectar |
| `python3 ghost.py monitor --reset --reboot` | Se a rajada voltar em até 2,5 min, reinicia o celular |
| `python3 ghost.py mapa` | Mapa de calor do padrão salvo |
| `python3 ghost.py status` | Pontos mais ativos da tela |
| `python3 ghost.py relatorio` | Resumo das crises (serve de prova pra reclamação) |
| `python3 ghost.py limpar` | Apaga o padrão (o log é mantido) |

Atalhos criados na tela inicial: **calibrar**, **monitor**, **monitor-reset**, **mapa**.

## Teclado

Com o teclado aberto, o monitor **não** trata digitação como bug e **nunca** executa reset ou
reboot. Só registra se a rajada bater com o padrão já salvo.

## Ajustes

No topo do `ghost.py`: `MIN_TOQUES`, `JANELA`, `MAX_CELULAS`, `DUR_CURTA`, `FRACAO_PADRAO`.
Detectando demais? Aumente `MIN_TOQUES`. Detectando de menos? Diminua.

## Notas

- O reset apaga/acende a tela, então o celular pode **bloquear**: talvez precise desbloquear.
- Depois de reiniciar, a Depuração por Wi-Fi pode desligar e precisar ser religada.
- `padrao.json` e `log.csv` ficam só no celular (estão no `.gitignore`).
- Ao atualizar o projeto, **não apague a pasta**: faça `git pull` e `bash install.sh`.

## Próximo passo

Etapa 2: app Android (APK) com overlay de zonas mortas pequenas, recorte do teclado e
atalho por tecla de volume, usando o padrão salvo aqui.
