# TouchGuard

App para reduzir toques fantasmas: cria faixas invisíveis nas bordas da tela que
ignoram qualquer toque, e um modo que trava a tela toda.

Não conserta o defeito da tela. Só ajuda quando os toques falsos caem sempre nas
mesmas regiões.

## Como gerar o APK só com o celular (GitHub Actions)

1. Crie uma conta grátis em github.com e um repositório novo (pode ser privado).
2. Envie todos os arquivos deste zip para o repositório, incluindo a pasta `.github`
   (no site: "Add file" > "Upload files". Descompacte o zip antes).
3. Abra a aba **Actions**. O build "Build APK" roda sozinho (leva uns 3 a 5 minutos).
   Se não rodar, escolha "Build APK" e toque em "Run workflow".
4. Quando terminar, abra a execução e baixe o arquivo **TouchGuard-apk** em "Artifacts".
   Dentro do zip baixado está o `app-debug.apk`.
5. Instale o APK no celular (permita "instalar apps desconhecidos" para o navegador
   ou gerenciador de arquivos).

## Como gerar o APK no computador

Com Android Studio: abra a pasta do projeto e use Build > Build APK(s).
Pela linha de comando (JDK 17 e Android SDK instalados): `gradle assembleDebug`.
O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Usando

1. Abra o TouchGuard > "Abrir configurações de Acessibilidade" > ative o TouchGuard.
   Se o Android bloquear (configuração restrita): Configurações > Apps > TouchGuard >
   menu de três pontos > "Permitir configurações restritas".
2. Ligue "Mostrar zonas bloqueadas" e ajuste as faixas até cobrirem a região onde os
   toques fantasmas aparecem. Depois pode desligar a opção.
3. Adicione o botão "Travar toques" ao painel rápido para travar a tela toda quando
   precisar. Para liberar, segure o botão na tela por 2 segundos. A trava também se
   solta sozinha após o tempo configurado (padrão: 30 min; 0 = nunca).

## Limitações

- Faixas muito largas ou nas bordas de cima e de baixo podem atrapalhar gestos do
  sistema, barra de notificações e teclado.
- Não dá para filtrar toques fantasmas no meio da tela sem bloquear toques de verdade.
- O código não foi compilado nem testado em um aparelho antes de ser entregue.
  Se o build falhar, mande a mensagem de erro que eu corrijo.

## Novidades da versão 1.1

- A trava total nunca volta ligada depois de reiniciar o celular ou desligar o serviço.
- Auto-desbloqueio da trava total, configurável de 0 a 120 minutos.
- Faixas limitadas a 48 dp e margem do app maior, para o próprio app nunca ficar bloqueado.
- Opção (ligada por padrão) para as faixas laterais não cobrirem a barra de status nem a
  barra de gestos. As faixas são recalculadas ao girar a tela.
- O botão do painel rápido mostra "Serviço desligado" e abre o app quando a
  Acessibilidade não está ativa, em vez de fingir que travou.
- Ícone de cadeado no app.
