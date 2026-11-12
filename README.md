# TouchGuard 1.2

App para reduzir toques fantasmas. Ele descobre onde os toques falsos aparecem,
cria zonas invisíveis nesses pontos e ignora qualquer toque que caia nelas.
Também tem uma trava que ignora toques na tela inteira.

Não conserta o defeito da tela. Só ajuda quando os toques falsos caem sempre nas
mesmas regiões.

## Como gerar o APK só com o celular (GitHub Actions)

1. No repositório do GitHub, envie todos os arquivos deste zip, substituindo os antigos,
   incluindo a pasta `.github` (no site: "Add file" > "Upload files". Descompacte antes).
2. Abra a aba **Actions**. O build "Build APK" roda sozinho (uns 3 a 5 minutos).
   Se não rodar, escolha "Build APK" e toque em "Run workflow".
3. Quando terminar, abra a execução e baixe **TouchGuard-apk** em "Artifacts".
   Dentro do zip está o `app-debug.apk`.
4. Instale por cima da versão anterior. As configurações são mantidas.

## Como gerar no computador

Android Studio: abra a pasta e use Build > Build APK(s).
Linha de comando (JDK 17 e Android SDK): `gradle assembleDebug`.
O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Usando

1. Abra o TouchGuard > "Ativar na Acessibilidade" > ative o TouchGuard.
   Se o Android bloquear: Configurações > Apps > TouchGuard > ⋮ >
   "Permitir configurações restritas".
2. **Calibração**: escolha o tempo (30 s, 1 min ou 2 min) e toque em "Iniciar calibração".
   Solte o celular sobre a mesa. Depois de 5 s, todo toque registrado é fantasma.
   Para cancelar, segure o botão na tela por 2 s.
3. No fim abre o **editor de zonas**: pontos amarelos são os toques fantasmas,
   retângulos laranja tracejados são as zonas sugeridas. Toque em "Aplicar sugestões"
   (ou em cada área laranja) e depois em "Salvar".
4. No editor você também pode **desenhar zonas** em qualquer lugar arrastando o dedo,
   e apagar zonas no modo "Apagar". O botão ⇅ muda a barra de lugar.
5. **Painel Edge (Samsung)**: na seção "Alça do Painel Edge", escolha o lado da alça,
   ligue "Mostrar zonas bloqueadas" e ajuste posição e altura até a área verde ficar
   em cima da alça. Ali os toques sempre passam, então dá para puxar o painel.
6. **Trava total**: pelo app ou pelo botão "Travar toques" do painel rápido
   (no Android 13+ o app adiciona o botão com um toque). Para liberar, segure o botão
   na tela por 2 s. A trava também se solta sozinha após o tempo configurado.

## Bom saber

- Com o TouchGuard aberto, as zonas desenhadas ficam pausadas, para o app nunca
  ficar bloqueado. As faixas de borda continuam ativas.
- As zonas acompanham o mesmo ponto físico da tela quando o celular gira.
- Zonas grandes ou no meio da tela bloqueiam também os toques de verdade naquela área.
- O app não consegue analisar cada toque e deixar passar só os reais: o Android não
  permite inspecionar um toque e depois entregá-lo ao app de baixo sem lentidão e
  falhas. Por isso a "inteligência" fica na calibração, que mostra onde bloquear.
- O código não foi compilado num aparelho antes de ser entregue (só teve a sintaxe
  conferida). Se o build falhar, mande a mensagem de erro do Actions.

## Novidades da versão 1.2

- Calibração com mapa de calor, estatísticas (duração e tamanho do contato) e
  sugestão automática de zonas.
- Zonas retangulares em qualquer lugar da tela, com editor em tela cheia.
- Passagem livre para a alça do Painel Edge (ligada na direita por padrão em Samsung).
- Visual novo, com tema claro e escuro e ícone de escudo.
- Botão do painel rápido consulta o sistema para saber se o serviço está ligado.
- Arrastar os controles não faz mais as zonas piscarem.
- O tempo do auto-desbloqueio só reinicia quando a trava é ligada.
- Botão para adicionar "Travar toques" ao painel rápido (Android 13+).
