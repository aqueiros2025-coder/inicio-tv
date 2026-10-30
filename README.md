# Início TV, launcher estilo Apple TV

Launcher para Android TV com fundo em gradiente, título grande da app em foco, uma linha de **cartões grandes em destaque** (Nuvio, Arvio, Wuplay, Playtorrio, Stremio) e uma linha com **todas as outras apps**. Não mostra sugestões da Google.

Os cartões grandes mostram o banner de TV da app, se ela tiver um, ou o ícone. Mostrar capas de séries e filmes só é possível se a própria app as publicar no Android TV.

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

Tudo está no topo de `app/src/main/java/com/example/tvlauncher/MainActivity.kt`:

- `FEATURED`: nomes (ou parte do nome) das apps em destaque, pela ordem em que aparecem.
- `HIDDEN_PACKAGES`: apps que nunca aparecem na linha "Todas as apps".

Manter OK premido num cartão abre as informações da app (para desinstalar ou limpar a cache).
