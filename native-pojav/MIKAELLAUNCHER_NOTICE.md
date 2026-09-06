# MikaelLauncher — núcleo Android baseado no PojavLauncher

Esta pasta incorpora o código-fonte do [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher), branch `v3_openjdk`, usado como base para a execução do Minecraft: Java Edition no Android.

O PojavLauncher é licenciado sob a **GNU Lesser General Public License v3.0 (LGPLv3)**. A licença original está em `LICENSE` e deve acompanhar qualquer redistribuição. Os créditos e licenças das dependências estão documentados no README original.

O código do MikaelLauncher será desenvolvido separadamente e integrado por meio de uma camada Android nativa, sem remover os avisos de copyright ou as obrigações da LGPLv3.

## Capacidades herdadas a integrar

- inicialização do Minecraft Java;
- runtimes OpenJDK móveis e seleção por versão;
- Forge, Fabric, Quilt, OptiFine e instaladores relacionados;
- gerenciador de mods e modpacks;
- controles touch, teclado, mouse e gamepad;
- renderização OpenGL/GL4ES, áudio OpenAL e ponte GLFW/LWJGL;
- perfis, versões, logs e configurações do launcher.

A implementação final do MikaelLauncher ainda precisa conectar esta base à interface Expo e adicionar o fluxo de primeiro acesso que instala Java 8, 17, 21 e 25. O repositório original lista suporte explícito a Java 8, 17 e 21; Java 25 será tratado como runtime adicional quando houver um build Android compatível.
