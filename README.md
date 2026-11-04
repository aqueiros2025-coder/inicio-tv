# Início TV, launcher inspirado no ecrã inicial das consolas

Launcher para Android TV com o estilo de uma consola de jogos: filas horizontais de **mosaicos quadrados**, em que o mosaico em foco cresce e mostra o nome por baixo, e um fundo azul-escuro com ondas de luz. Tem uma fila de **destaque** com cartões retangulares pequenos (Nuvio, Arvio, Wuplay, Playtorrio, Stremio), filas de **continuar a ver**, **usadas recentemente**, **filmes sugeridos** e **séries sugeridas**, e uma com **todas as outras apps**. Não mostra sugestões da Google. É um desenho próprio: não usa logótipos, símbolos nem imagens de nenhuma marca.

Os cartões de destaque mostram o banner da app, se tiver um, ou o ícone.

## Filmes, séries e continuar a ver

- **Filmes sugeridos** e **Séries sugeridas**: posters vindos do catálogo público do Cinemeta (o mesmo do Stremio), sem chave. A lista muda todos os dias e fica guardada para funcionar sem rede. Ao carregar OK num poster, abre-se a página do título no **Stremio** (por defeito) ou a app que escolheres em Opções. Só o Stremio aceita abrir um título diretamente; com as outras apps abre-se a app e tens de procurar o título.
- **Continuar a ver**: lista dos títulos que abriste a partir destes cartões (mais recente primeiro). **Não é o progresso real** dentro das apps: o Android não deixa um launcher ler o que cada app de streaming está a ver. Manter OK premido num cartão desta fila remove-o.

No topo aparecem o tempo atual da tua cidade, a hora e o botão **Opções**, e num painel em baixo a previsão de hoje e dos 3 dias seguintes (dados do [Open-Meteo](https://open-meteo.com), gratuito e sem chave). A box precisa de internet.

## Obter o APK (só com o telemóvel)

1. Cria uma conta gratuita em github.com e um repositório novo (por exemplo `inicio-tv`).
2. Põe lá todos os ficheiros deste projeto, mantendo as pastas. Em "Add file > Create new file" podes escrever o caminho completo no nome, por exemplo `app/src/main/AndroidManifest.xml`.
3. Em cada gravação, o GitHub compila sozinho (separador **Actions**, demora uns 3 a 5 minutos).
4. Quando terminar, vai a **Releases > Início TV (última versão)** e descarrega `app-debug.apk`.

## Instalar na box

1. Na box, instala a app **Downloader** (AFTVnews) e ativa "Instalar apps de fontes desconhecidas" para ela.
2. No Downloader escreve o endereço do APK:
   `https://github.com/O_TEU_UTILIZADOR/O_TEU_REPOSITORIO/releases/download/latest/app-debug.apk`
3. Instala. Carrega no botão Home e escolhe "Início" como launcher por defeito (Sempre).
4. Se a box não perguntar qual launcher usar, desativa o launcher original por ADB (ver o guia `otimizar-box.md`, passo 5), só depois de o Início estar instalado.

## Personalizar

Abre o botão **Opções** (ou carrega na tecla Menu do comando) para:

- escolher o **fundo de ecrã** (ver abaixo);
- ligar ou desligar os **sons do comando**, as **sugestões de filmes e séries**, e escolher a app que as abre;
- ligar, desligar ou limpar a linha **Usadas recentemente** (as últimas apps que abriste, mais recente primeiro);
- mudar a **cidade** do tempo e a unidade (°C ou °F), ou desligar a previsão;
- escolher as **apps em destaque**, que ficam pela ordem em que as marcas;
- **esconder** apps da linha "Todas as apps";
- **repor** as predefinições.

Manter OK premido num cartão abre as informações da app (para desinstalar ou limpar a cache).

## Fundos de ecrã

Em **Opções > Fundos de ecrã**:

- **Escolher fundo**: fundos incluídos (Ondas azuis, animado ou fixo, que é o predefinido; gradientes fixos Noite, Oceano, Pôr do sol; gradientes animados Aurora, Brasas) e os teus ficheiros (imagens fixas JPG, PNG, WEBP; vídeos animados MP4, WEBM).
- **Descarregar da Internet**:
  - **Catálogo de imagens fixas** (12, da Wikimedia Commons, imagens em destaque da NASA, ESA e ESO: nebulosas, galáxias, Saturno, Terra à noite, Cabo Verde...).
  - **Catálogo de vídeos animados** (6 loops da Mixkit, licença gratuita Mixkit: túneis, espaço, floresta de inverno, outono). A app tenta 1080p, depois 720p e 360p.
  - uma foto aleatória (Picsum, 1920×1080) ou um endereço (URL) direto para uma imagem ou vídeo.
- **Enviar do telemóvel (QR Code)**: a box mostra um QR Code. Lê-o com a câmara do telemóvel (na mesma rede da box), abre a página e envia uma imagem ou vídeo, ou cola um endereço para a box descarregar. O servidor só funciona enquanto o QR Code estiver no ecrã e o link tem um código secreto.
- **Apagar um fundo descarregado**.

## Sons do comando

Cada tecla do comando faz um som curto (mover, OK, voltar), também nos menus. Os sons são gerados pela própria app. Desliga-os em **Opções > Sons do comando**.

Dicas para vídeos animados (vídeos verticais são recusados): curtos (10 a 30 s), 1080p, 16:9, H.264, até cerca de 8 Mbps, sem som necessário (é silenciado). O vídeo é esticado ao ecrã e pára quando abres outra app, para a box não ficar mais lenta. GIFs não são suportados: converte para MP4.

Valores iniciais (antes de mexeres nas Opções) estão no topo de `app/src/main/java/com/example/tvlauncher/MainActivity.kt`: `FEATURED_DEFAULT` e `HIDDEN_DEFAULT`.
