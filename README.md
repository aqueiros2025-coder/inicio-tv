# Início TV, launcher inspirado no ecrã inicial das consolas

Launcher para Android TV com o estilo de uma consola de jogos: filas horizontais de **mosaicos quadrados**, em que o mosaico em foco cresce e mostra o nome por baixo, e um fundo azul-escuro com ondas de luz. Tem uma fila de **destaque** (Nuvio, Arvio, Wuplay, Playtorrio, Stremio), uma de **usadas recentemente** e uma com **todas as outras apps**. Não mostra sugestões da Google. É um desenho próprio: não usa logótipos, símbolos nem imagens de nenhuma marca.

Os mosaicos mostram o ícone da app. Mostrar capas de séries e filmes só é possível se a própria app as publicar no Android TV.

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
- ligar, desligar ou limpar a linha **Usadas recentemente** (as últimas apps que abriste, mais recente primeiro);
- mudar a **cidade** do tempo e a unidade (°C ou °F), ou desligar a previsão;
- escolher as **apps em destaque**, que ficam pela ordem em que as marcas;
- **esconder** apps da linha "Todas as apps";
- **repor** as predefinições.

Manter OK premido num cartão abre as informações da app (para desinstalar ou limpar a cache).

## Fundos de ecrã

Em **Opções > Fundos de ecrã**:

- **Escolher fundo**: fundos incluídos (Ondas azuis, animado ou fixo, que é o predefinido; gradientes fixos Noite, Oceano, Pôr do sol; gradientes animados Aurora, Brasas) e os teus ficheiros (imagens fixas JPG, PNG, WEBP; vídeos animados MP4, WEBM).
- **Descarregar da Internet**: uma foto aleatória (Picsum, 1920×1080) ou um endereço (URL) direto para uma imagem ou vídeo.
- **Enviar do telemóvel (QR Code)**: a box mostra um QR Code. Lê-o com a câmara do telemóvel (na mesma rede da box), abre a página e envia uma imagem ou vídeo, ou cola um endereço para a box descarregar. O servidor só funciona enquanto o QR Code estiver no ecrã e o link tem um código secreto.
- **Apagar um fundo descarregado**.

Dicas para vídeos animados: curtos (10 a 30 s), 1080p, 16:9, H.264, até cerca de 8 Mbps, sem som necessário (é silenciado). O vídeo é esticado ao ecrã e pára quando abres outra app, para a box não ficar mais lenta. GIFs não são suportados: converte para MP4.

Valores iniciais (antes de mexeres nas Opções) estão no topo de `app/src/main/java/com/example/tvlauncher/MainActivity.kt`: `FEATURED_DEFAULT` e `HIDDEN_DEFAULT`.
