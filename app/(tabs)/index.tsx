import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { StatusBar } from "expo-status-bar";
import React, { useMemo, useState } from "react";
import {
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { getMemoryStatus, MEMORY_PRESETS } from "@/lib/launcher-utils";

type IconName = React.ComponentProps<typeof MaterialIcons>["name"];
type ScreenKey =
  | "home"
  | "installations"
  | "mods"
  | "modpacks"
  | "accounts"
  | "settings"
  | "runtime"
  | "versions"
  | "files"
  | "downloads"
  | "logs"
  | "player";
type ModalKey = "account" | "install" | "runtime" | "memory" | "version" | "menu" | null;

type Colors = {
  bg: string;
  surface: string;
  surface2: string;
  border: string;
  text: string;
  muted: string;
  dim: string;
  accent: string;
  accentSoft: string;
  lime: string;
  danger: string;
};

const DARK: Colors = {
  bg: "#0d0d14",
  surface: "#171723",
  surface2: "#20202f",
  border: "#2d2d40",
  text: "#f6f4fb",
  muted: "#9695a6",
  dim: "#666576",
  accent: "#9b7bff",
  accentSoft: "#2c234a",
  lime: "#c7f35b",
  danger: "#ff6f91",
};

const LIGHT: Colors = {
  bg: "#f4f3fa",
  surface: "#ffffff",
  surface2: "#eeecf7",
  border: "#dedbea",
  text: "#1c1a25",
  muted: "#6e6a7c",
  dim: "#918c9f",
  accent: "#7053dc",
  accentSoft: "#e7e0ff",
  lime: "#75a600",
  danger: "#d93d6d",
};

const navItems: { key: ScreenKey; label: string; icon: IconName }[] = [
  { key: "home", label: "Início", icon: "home" },
  { key: "installations", label: "Instalações", icon: "view-quilt" },
  { key: "mods", label: "Mods", icon: "extension" },
  { key: "modpacks", label: "Packs", icon: "inventory-2" },
  { key: "accounts", label: "Contas", icon: "people" },
  { key: "settings", label: "Ajustes", icon: "settings" },
];

const menuItems: { key: ScreenKey; label: string; icon: IconName; description: string }[] = [
  { key: "home", label: "Início", icon: "home", description: "Visão geral do launcher" },
  { key: "versions", label: "Versões", icon: "category", description: "Releases e snapshots" },
  { key: "installations", label: "Instalações", icon: "view-quilt", description: "Perfis configurados" },
  { key: "runtime", label: "Java Runtime", icon: "coffee", description: "Ambientes Java" },
  { key: "accounts", label: "Contas", icon: "people", description: "Provedores conectados" },
  { key: "mods", label: "Mods", icon: "extension", description: "Biblioteca de mods" },
  { key: "modpacks", label: "Modpacks", icon: "inventory-2", description: "Coleções instaladas" },
  { key: "files", label: "Arquivos", icon: "folder-open", description: "Explorador .minecraft" },
  { key: "downloads", label: "Downloads", icon: "download", description: "Fila de downloads" },
  { key: "logs", label: "Logs", icon: "terminal", description: "Console e diagnósticos" },
  { key: "settings", label: "Configurações", icon: "settings", description: "Preferências do app" },
  { key: "player", label: "Meu perfil", icon: "person", description: "Skin e histórico" },
];

const versions = [
  { name: "Minecraft 1.12.2", type: "Release", loader: "Forge", java: "Java 8", size: "298 MB", installed: true, favorite: true },
  { name: "Minecraft 1.20.1", type: "Release", loader: "Fabric", java: "Java 17", size: "412 MB", installed: true, favorite: false },
  { name: "Minecraft 1.8.9", type: "Release", loader: "OptiFine", java: "Java 8", size: "214 MB", installed: false, favorite: false },
  { name: "Minecraft 1.21.4", type: "Release", loader: "NeoForge", java: "Java 21", size: "536 MB", installed: false, favorite: false },
  { name: "25w05a", type: "Snapshot", loader: "Vanilla", java: "Java 21", size: "189 MB", installed: false, favorite: false },
];

const installations = [
  { name: "Survival", version: "1.12.2", loader: "Forge", java: "Java 8", ram: "1024 MB", mods: 25, color: "#9b7bff" },
  { name: "PvP Arena", version: "1.8.9", loader: "OptiFine", java: "Java 8", ram: "768 MB", mods: 8, color: "#c7f35b" },
  { name: "Create: Above", version: "1.20.1", loader: "Fabric", java: "Java 17", ram: "2048 MB", mods: 63, color: "#ff9f6e" },
];

const runtimeItems = [
  { name: "Java 8", version: "8u402", arch: "ARM64", size: "186 MB", status: "Instalado", color: "#c7f35b" },
  { name: "Java 17", version: "17.0.10", arch: "ARM64", size: "204 MB", status: "Instalado", color: "#9b7bff" },
  { name: "Java 21", version: "21.0.2", arch: "ARM64", size: "219 MB", status: "Disponível", color: "#71d7ff" },
  { name: "Java 25", version: "25-ea", arch: "ARM64", size: "231 MB", status: "Experimental", color: "#ff9f6e" },
];

const modItems = [
  { name: "JEI — Just Enough Items", version: "1.12.2-4.16.1", loader: "Forge", enabled: true, color: "#9b7bff" },
  { name: "JourneyMap", version: "5.7.1", loader: "Forge", enabled: true, color: "#c7f35b" },
  { name: "Iron Chests", version: "7.0.72", loader: "Forge", enabled: false, color: "#71d7ff" },
  { name: "Mouse Tweaks", version: "2.10", loader: "Fabric", enabled: true, color: "#ff9f6e" },
];

const packItems = [
  { name: "Create: Above & Beyond", version: "1.1", mc: "1.16.5", loader: "Forge", mods: 88, ram: "4096 MB", color: "#ff9f6e" },
  { name: "Fabulously Optimized", version: "6.2.0", mc: "1.20.1", loader: "Fabric", mods: 42, ram: "2048 MB", color: "#c7f35b" },
  { name: "Better Minecraft", version: "v28", mc: "1.20.1", loader: "Forge", mods: 312, ram: "6144 MB", color: "#9b7bff" },
];

const fileItems = [
  { name: "mods", count: "25 arquivos", icon: "extension", color: "#9b7bff" },
  { name: "config", count: "84 arquivos", icon: "tune", color: "#71d7ff" },
  { name: "saves", count: "6 mundos", icon: "public", color: "#c7f35b" },
  { name: "resourcepacks", count: "12 arquivos", icon: "palette", color: "#ff9f6e" },
  { name: "shaderpacks", count: "4 arquivos", icon: "blur-on", color: "#d6a6ff" },
  { name: "versions", count: "8 instalações", icon: "category", color: "#9b7bff" },
  { name: "libraries", count: "1.2 GB", icon: "folder", color: "#71d7ff" },
  { name: "runtime", count: "2 ambientes", icon: "coffee", color: "#c7f35b" },
];

function Icon({ name, size = 20, color, style }: { name: IconName; size?: number; color: string; style?: object }) {
  return <MaterialIcons name={name} size={size} color={color} style={style} />;
}

function Pill({ children, color, colors, outline = false }: { children: React.ReactNode; color?: string; colors: Colors; outline?: boolean }) {
  const tint = color ?? colors.accent;
  return (
    <View style={[styles.pill, { backgroundColor: outline ? "transparent" : `${tint}1f`, borderColor: `${tint}66` }]}>
      <Text style={[styles.pillText, { color: tint }]}>{children}</Text>
    </View>
  );
}

function SectionTitle({ title, action, onPress, colors }: { title: string; action?: string; onPress?: () => void; colors: Colors }) {
  return (
    <View style={styles.sectionTitle}>
      <Text style={[styles.sectionHeading, { color: colors.text }]}>{title}</Text>
      {action ? (
        <Pressable onPress={onPress} hitSlop={8}>
          <Text style={[styles.sectionAction, { color: colors.accent }]}>{action}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function StatCard({ label, value, icon, color, colors }: { label: string; value: string; icon: IconName; color: string; colors: Colors }) {
  return (
    <View style={[styles.statCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <View style={[styles.statIcon, { backgroundColor: `${color}1f` }]}><Icon name={icon} size={17} color={color} /></View>
      <Text style={[styles.statValue, { color: colors.text }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: colors.muted }]}>{label}</Text>
    </View>
  );
}

function RowButton({ icon, label, value, onPress, colors, accent }: { icon: IconName; label: string; value?: string; onPress: () => void; colors: Colors; accent?: string }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.rowButton, { borderBottomColor: colors.border }, pressed && styles.pressed]}>
      <View style={[styles.rowIcon, { backgroundColor: `${accent ?? colors.accent}1c` }]}><Icon name={icon} size={18} color={accent ?? colors.accent} /></View>
      <Text style={[styles.rowLabel, { color: colors.text }]}>{label}</Text>
      {value ? <Text style={[styles.rowValue, { color: colors.muted }]}>{value}</Text> : null}
      <Icon name="chevron-right" size={19} color={colors.dim} />
    </Pressable>
  );
}

function Header({ title, subtitle, onMenu, onAvatar, colors }: { title: string; subtitle?: string; onMenu: () => void; onAvatar: () => void; colors: Colors }) {
  return (
    <View style={styles.header}>
      <Pressable onPress={onMenu} style={({ pressed }) => [styles.headerIcon, pressed && styles.pressed]}>
        <Icon name="menu" size={23} color={colors.text} />
      </Pressable>
      <View style={styles.headerTitles}>
        <Text style={[styles.headerTitle, { color: colors.text }]}>{title}</Text>
        {subtitle ? <Text style={[styles.headerSubtitle, { color: colors.muted }]}>{subtitle}</Text> : null}
      </View>
      <View style={styles.headerRight}>
        <Pressable onPress={() => {}} style={styles.headerIcon}>
          <Icon name="notifications-none" size={23} color={colors.text} />
          <View style={[styles.notificationDot, { backgroundColor: colors.lime }]} />
        </Pressable>
        <Pressable onPress={onAvatar} style={({ pressed }) => [styles.avatar, { backgroundColor: colors.accentSoft, borderColor: colors.accent }, pressed && styles.pressed]}>
          <Text style={[styles.avatarText, { color: colors.accent }]}>M</Text>
        </Pressable>
      </View>
    </View>
  );
}

function EmptyHeader({ title, subtitle, onMenu, onAvatar, colors }: { title: string; subtitle: string; onMenu: () => void; onAvatar: () => void; colors: Colors }) {
  return <Header title={title} subtitle={subtitle} onMenu={onMenu} onAvatar={onAvatar} colors={colors} />;
}

export default function HomeScreen() {
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [accent, setAccent] = useState("#9b7bff");
  const [screen, setScreen] = useState<ScreenKey>("home");
  const [modal, setModal] = useState<ModalKey>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [account, setAccount] = useState("Microsoft");
  const [runtime, setRuntime] = useState("Java 8");
  const [version, setVersion] = useState("1.12.2");
  const [memory, setMemory] = useState(1024);
  const [playing, setPlaying] = useState(false);
  const [mods, setMods] = useState(modItems);
  const [favoriteVersions, setFavoriteVersions] = useState<string[]>(["Minecraft 1.12.2"]);
  const [settings, setSettings] = useState({ animations: true, sounds: true, notifications: true, wifiOnly: true, devMode: false });

  const colors = useMemo(() => {
    const base = theme === "dark" ? DARK : LIGHT;
    return { ...base, accent };
  }, [theme, accent]);

  const showToast = (message: string) => {
    setToast(message);
    setTimeout(() => setToast(null), 2400);
  };

  const navigate = (next: ScreenKey) => {
    setScreen(next);
    setModal(null);
  };

  const handlePlay = () => {
    if (playing) return;
    setPlaying(true);
    showToast("Preparando instalação no modo demonstração…");
    setTimeout(() => {
      setPlaying(false);
      showToast("Tudo pronto! A execução real será conectada depois.");
    }, 1800);
  };

  const setSetting = (key: keyof typeof settings, value: boolean) => setSettings((current) => ({ ...current, [key]: value }));

  const renderHome = () => (
    <>
      <Header title="MIKAELLAUNCHER" subtitle="Seu Minecraft, do seu jeito" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors} />
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>
        <View style={styles.greetingRow}>
          <View>
            <Text style={[styles.eyebrow, { color: colors.accent }]}>BEM-VINDO DE VOLTA</Text>
            <Text style={[styles.greeting, { color: colors.text }]}>Olá, Mikael <Text style={{ color: colors.lime }}>✦</Text></Text>
            <Text style={[styles.bodyText, { color: colors.muted }]}>Tudo pronto para sua próxima aventura.</Text>
          </View>
          <Pressable onPress={() => setModal("account")} style={({ pressed }) => [styles.accountMini, { backgroundColor: colors.surface, borderColor: colors.border }, pressed && styles.pressed]}>
            <View style={[styles.accountStatus, { backgroundColor: colors.lime }]} />
            <Text style={[styles.accountMiniText, { color: colors.text }]}>{account}</Text>
            <Icon name="expand-more" size={17} color={colors.muted} />
          </Pressable>
        </View>

        <View style={[styles.heroCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <View style={[styles.heroGlow, { backgroundColor: colors.accent }]} />
          <View style={[styles.heroGlowSmall, { backgroundColor: colors.lime }]} />
          <View style={styles.heroTopLine}>
            <View style={styles.mcBadge}><Text style={styles.mcBadgeText}>M</Text></View>
            <View style={{ flex: 1 }}>
              <Text style={[styles.heroKicker, { color: colors.muted }]}>INSTALAÇÃO ATIVA</Text>
              <Text style={[styles.heroTitle, { color: colors.text }]}>Survival</Text>
            </View>
            <Pressable onPress={() => setModal("install")} style={({ pressed }) => [styles.moreButton, { backgroundColor: colors.surface2 }, pressed && styles.pressed]}><Icon name="more-horiz" size={20} color={colors.muted} /></Pressable>
          </View>
          <View style={styles.heroVersionRow}>
            <Text style={[styles.versionNumber, { color: colors.text }]}>Minecraft {version}</Text>
            <Pill colors={colors} color={colors.accent}>Forge</Pill>
            <Pill colors={colors} color={colors.lime}>Java 8</Pill>
          </View>
          <View style={styles.heroDivider} />
          <View style={styles.heroMetaRow}>
            <View><Text style={[styles.metaLabel, { color: colors.muted }]}>MEMÓRIA</Text><Text style={[styles.metaValue, { color: colors.text }]}>{memory} MB</Text></View>
            <View><Text style={[styles.metaLabel, { color: colors.muted }]}>MODS</Text><Text style={[styles.metaValue, { color: colors.text }]}>25 ativos</Text></View>
            <View><Text style={[styles.metaLabel, { color: colors.muted }]}>STATUS</Text><View style={styles.statusInline}><View style={[styles.statusDot, { backgroundColor: colors.lime }]} /><Text style={[styles.metaValue, { color: colors.lime }]}>Pronto</Text></View></View>
          </View>
          <Pressable onPress={handlePlay} style={({ pressed }) => [styles.playButton, { backgroundColor: colors.lime }, pressed && styles.buttonPressed]}>
            <Icon name={playing ? "hourglass-top" : "play-arrow"} size={23} color="#11120d" />
            <Text style={styles.playButtonText}>{playing ? "PREPARANDO…" : "JOGAR AGORA"}</Text>
          </Pressable>
          <View style={styles.heroActions}>
            <Pressable onPress={() => setModal("install")} style={({ pressed }) => [styles.secondaryButton, { borderColor: colors.border }, pressed && styles.pressed]}><Icon name="tune" size={17} color={colors.accent} /><Text style={[styles.secondaryButtonText, { color: colors.text }]}>Configurar</Text></Pressable>
            <Pressable onPress={() => setModal("install")} style={({ pressed }) => [styles.secondaryButton, { borderColor: colors.border }, pressed && styles.pressed]}><Icon name="swap-horiz" size={17} color={colors.muted} /><Text style={[styles.secondaryButtonText, { color: colors.text }]}>Trocar</Text></Pressable>
          </View>
        </View>

        <SectionTitle title="Acesso rápido" action="Ver tudo" onPress={() => setModal("menu")} colors={colors} />
        <View style={[styles.quickGrid, { backgroundColor: colors.surface, borderColor: colors.border }]}>
          <QuickItem icon="coffee" label="Runtime" value={runtime} color="#d6a6ff" onPress={() => setModal("runtime")} colors={colors} />
          <QuickItem icon="memory" label="RAM" value={`${memory} MB`} color="#c7f35b" onPress={() => setModal("memory")} colors={colors} />
          <QuickItem icon="category" label="Versão" value={version} color="#71d7ff" onPress={() => setModal("version")} colors={colors} />
          <QuickItem icon="account-circle" label="Conta" value={account} color="#ff9f6e" onPress={() => setModal("account")} colors={colors} />
        </View>

        <SectionTitle title="Status do dispositivo" colors={colors} />
        <View style={styles.statsRow}>
          <StatCard label="RAM livre" value="3.2 GB" icon="memory" color="#c7f35b" colors={colors} />
          <StatCard label="CPU" value="34%" icon="speed" color="#71d7ff" colors={colors} />
          <StatCard label="Armazenamento" value="42 GB" icon="storage" color="#ff9f6e" colors={colors} />
        </View>

        <SectionTitle title="Novidades" action="Abrir notícias" onPress={() => showToast("Central de notícias em breve")} colors={colors} />
        <Pressable onPress={() => showToast("Notícia marcada como lida")} style={({ pressed }) => [styles.newsCard, { backgroundColor: colors.surface, borderColor: colors.border }, pressed && styles.pressed]}>
          <View style={[styles.newsAccent, { backgroundColor: colors.accent }]} />
          <View style={{ flex: 1 }}><Text style={[styles.newsTag, { color: colors.accent }]}>MIKAEL UPDATE · 06 SET</Text><Text style={[styles.newsTitle, { color: colors.text }]}>Prepare seu mundo para a próxima aventura</Text><Text style={[styles.newsBody, { color: colors.muted }]}>Novos perfis, atalhos mais rápidos e uma experiência mais leve.</Text></View>
          <Icon name="arrow-forward" size={20} color={colors.muted} />
        </Pressable>

        <SectionTitle title="Instalações recentes" action="Gerenciar" onPress={() => navigate("installations")} colors={colors} />
        {installations.slice(0, 2).map((item) => <InstallationMini key={item.name} item={item} colors={colors} onPress={() => setModal("install")} />)}
        <View style={{ height: 20 }} />
      </ScrollView>
    </>
  );

  const renderInstallations = () => (
    <PageShell title="Instalações" subtitle="Perfis prontos para jogar" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={[styles.infoBanner, { backgroundColor: colors.accentSoft }]}><Icon name="auto-awesome" size={20} color={colors.accent} /><View style={{ flex: 1 }}><Text style={[styles.bannerTitle, { color: colors.text }]}>Seu setup está otimizado</Text><Text style={[styles.bannerText, { color: colors.muted }]}>3 perfis configurados e prontos para iniciar.</Text></View></View>
      <Pressable onPress={() => showToast("Editor de instalação aberto (mock)")} style={({ pressed }) => [styles.addButton, { backgroundColor: colors.accent }, pressed && styles.buttonPressed]}><Icon name="add" size={20} color="#fff" /><Text style={styles.addButtonText}>Nova instalação</Text></Pressable>
      <SectionTitle title="Seus perfis" action="Ordenar" onPress={() => showToast("Ordenação alterada")} colors={colors} />
      {installations.map((item) => <InstallationCard key={item.name} item={item} colors={colors} onConfigure={() => setModal("install")} onPlay={handlePlay} />)}
      <SectionTitle title="Atalhos" colors={colors} />
      <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <RowButton icon="download" label="Gerenciar versões" value="8 disponíveis" onPress={() => navigate("versions")} colors={colors} />
        <RowButton icon="coffee" label="Java Runtime" value="2 instalados" onPress={() => navigate("runtime")} colors={colors} accent="#c7f35b" />
        <RowButton icon="folder-open" label="Diretório do jogo" value="/.minecraft" onPress={() => navigate("files")} colors={colors} accent="#71d7ff" />
      </View>
    </PageShell>
  );

  const renderRuntime = () => (
    <PageShell title="Java Runtime" subtitle="Ambientes para suas versões" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={[styles.selectedRuntimeCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.bigIconCircle, { backgroundColor: `${colors.lime}1c` }]}><Icon name="coffee" size={25} color={colors.lime} /></View><View style={{ flex: 1 }}><Text style={[styles.eyebrow, { color: colors.lime }]}>RUNTIME PADRÃO</Text><Text style={[styles.selectedTitle, { color: colors.text }]}>{runtime}</Text><Text style={[styles.bodyText, { color: colors.muted }]}>ARM64 · 8u402 · Instalado</Text></View><Icon name="check-circle" size={23} color={colors.lime} /></View>
      <View style={styles.actionChips}><ActionChip icon="add" label="Adicionar" onPress={() => showToast("Adicionar runtime (mock)")} colors={colors} /><ActionChip icon="file-upload" label="Importar" onPress={() => showToast("Seletor de arquivo (mock)")} colors={colors} /><ActionChip icon="download" label="Baixar" onPress={() => navigate("downloads")} colors={colors} /></View>
      <SectionTitle title="Ambientes instalados" colors={colors} />
      {runtimeItems.map((item) => <RuntimeCard key={item.name} item={item} selected={runtime === item.name} colors={colors} onPress={() => { setRuntime(item.name); showToast(`${item.name} selecionado`); }} />)}
      <SectionTitle title="Configuração avançada" colors={colors} />
      <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><RowButton icon="architecture" label="Arquitetura" value="ARM64" onPress={() => showToast("ARM64 selecionado")} colors={colors} /><RowButton icon="code" label="Argumentos JVM" value="3 ativos" onPress={() => showToast("Editor de argumentos (mock)")} colors={colors} /><RowButton icon="route" label="Caminho do Java" value="/runtime/java-8" onPress={() => showToast("Caminho copiado")} colors={colors} /></View>
    </PageShell>
  );

  const renderVersions = () => (
    <PageShell title="Versões" subtitle="Escolha seu próximo mundo" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>
        {['Releases', 'Snapshots', 'Betas', 'Instaladas', 'Favoritas'].map((filter, index) => <Pressable key={filter} onPress={() => showToast(`${filter} selecionado`)} style={[styles.filterChip, { backgroundColor: index === 0 ? colors.accent : colors.surface, borderColor: index === 0 ? colors.accent : colors.border }]}><Text style={[styles.filterText, { color: index === 0 ? "#fff" : colors.muted }]}>{filter}</Text></Pressable>)}
      </ScrollView>
      <View style={[styles.searchBox, { backgroundColor: colors.surface, borderColor: colors.border }]}><Icon name="search" size={20} color={colors.muted} /><TextInput placeholder="Buscar versão" placeholderTextColor={colors.dim} style={[styles.searchInput, { color: colors.text }]} /></View>
      {versions.map((item) => <VersionCard key={item.name} item={item} favorite={favoriteVersions.includes(item.name)} colors={colors} onFavorite={() => setFavoriteVersions((current) => current.includes(item.name) ? current.filter((name) => name !== item.name) : [...current, item.name])} onInstall={() => showToast(`${item.name} adicionado às instalações`)} />)}
    </PageShell>
  );

  const renderAccounts = () => (
    <PageShell title="Contas" subtitle="Escolha como você quer jogar" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={[styles.accountHero, { backgroundColor: colors.accentSoft }]}><View style={[styles.accountHeroIcon, { backgroundColor: colors.accent }]}><Icon name="verified-user" size={24} color="#fff" /></View><View style={{ flex: 1 }}><Text style={[styles.selectedTitle, { color: colors.text }]}>Conta selecionada</Text><Text style={[styles.bodyText, { color: colors.muted }]}>{account} · Conectada agora</Text></View><View style={[styles.onlineBadge, { backgroundColor: colors.lime }]} /></View>
      <SectionTitle title="Provedores" action="Adicionar" onPress={() => setModal("account")} colors={colors} />
      <AccountCard provider="Microsoft" username="Mikael Silva" uuid="a13f…7c20" icon="window" active={account === "Microsoft"} color="#71d7ff" colors={colors} onSelect={() => { setAccount("Microsoft"); showToast("Microsoft definida como ativa"); }} onManage={() => showToast("Gerenciamento visual aberto")} />
      <AccountCard provider="Offline" username="Mikael_Offline" uuid="offline…2026" icon="person-outline" active={account === "Offline"} color="#c7f35b" colors={colors} onSelect={() => { setAccount("Offline"); showToast("Conta offline definida como ativa"); }} onManage={() => showToast("Perfil offline aberto")} />
      <AccountCard provider="Ely.by" username="mikael_dev" uuid="elyby…a872" icon="alternate-email" active={account === "Ely.by"} color="#ff9f6e" colors={colors} onSelect={() => { setAccount("Ely.by"); showToast("Ely.by definida como ativa"); }} onManage={() => showToast("Login Ely.by visual (mock)")} />
      <View style={[styles.warningCard, { backgroundColor: `${colors.lime}12`, borderColor: `${colors.lime}40` }]}><Icon name="info-outline" size={19} color={colors.lime} /><Text style={[styles.warningText, { color: colors.muted }]}>As contas exibidas são fictícias. Nenhuma autenticação real está conectada neste protótipo.</Text></View>
    </PageShell>
  );

  const renderMods = () => (
    <PageShell title="Mods" subtitle="Personalize cada detalhe" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={styles.modToolbar}><View style={[styles.searchBox, { backgroundColor: colors.surface, borderColor: colors.border, flex: 1 }]}><Icon name="search" size={20} color={colors.muted} /><TextInput placeholder="Pesquisar mods" placeholderTextColor={colors.dim} style={[styles.searchInput, { color: colors.text }]} /></View><Pressable onPress={() => showToast("Novo mod adicionado (mock)")} style={[styles.squareButton, { backgroundColor: colors.accent }]}><Icon name="add" size={22} color="#fff" /></Pressable></View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterRow}>{['Todos', 'Forge', 'Fabric', 'NeoForge', 'Quilt'].map((filter, index) => <Pressable key={filter} onPress={() => showToast(`Filtro ${filter}`)} style={[styles.filterChip, { backgroundColor: index === 0 ? colors.accent : colors.surface, borderColor: index === 0 ? colors.accent : colors.border }]}><Text style={[styles.filterText, { color: index === 0 ? "#fff" : colors.muted }]}>{filter}</Text></Pressable>)}</ScrollView>
      <View style={styles.modSummary}><Text style={[styles.bodyText, { color: colors.muted }]}><Text style={{ color: colors.text, fontWeight: "800" }}>25</Text> mods no perfil Survival</Text><Pill colors={colors} color={colors.lime}>23 ativos</Pill></View>
      {mods.map((mod, index) => <ModCard key={mod.name} item={mod} colors={colors} onToggle={() => setMods((current) => current.map((entry, entryIndex) => entryIndex === index ? { ...entry, enabled: !entry.enabled } : entry))} onRemove={() => showToast(`${mod.name} removido (mock)`)} />)}
    </PageShell>
  );

  const renderModpacks = () => (
    <PageShell title="Modpacks" subtitle="Grandes aventuras em um toque" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={styles.actionChips}><ActionChip icon="add" label="Criar pack" onPress={() => showToast("Criador de modpack (mock)")} colors={colors} /><ActionChip icon="file-upload" label="Importar ZIP" onPress={() => showToast("Importador ZIP (mock)")} colors={colors} /><ActionChip icon="favorite-border" label="Favoritos" onPress={() => showToast("Filtro de favoritos")} colors={colors} /></View>
      <SectionTitle title="Sua biblioteca" action="Explorar" onPress={() => showToast("Biblioteca online (mock)")} colors={colors} />
      {packItems.map((item) => <ModpackCard key={item.name} item={item} colors={colors} onPress={() => showToast(`${item.name} selecionado`)} />)}
    </PageShell>
  );

  const renderFiles = () => (
    <PageShell title="Arquivos" subtitle=".minecraft · armazenamento local" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={[styles.pathBar, { backgroundColor: colors.surface, borderColor: colors.border }]}><Icon name="folder-open" size={19} color={colors.accent} /><Text style={[styles.pathText, { color: colors.text }]}>/storage/emulated/0/.minecraft</Text><Icon name="more-vert" size={20} color={colors.muted} /></View>
      <View style={styles.fileActions}><ActionChip icon="create-new-folder" label="Pasta" onPress={() => showToast("Nova pasta (mock)")} colors={colors} /><ActionChip icon="file-upload" label="Importar" onPress={() => showToast("Importar arquivo (mock)")} colors={colors} /><ActionChip icon="sort" label="Ordenar" onPress={() => showToast("Ordenação alterada")} colors={colors} /></View>
      <View style={[styles.fileGrid, { backgroundColor: colors.surface, borderColor: colors.border }]}>{fileItems.map((item) => <Pressable key={item.name} onPress={() => showToast(`Abrindo ${item.name}/`)} style={({ pressed }) => [styles.fileTile, { borderColor: colors.border }, pressed && styles.pressed]}><View style={[styles.fileIcon, { backgroundColor: `${item.color}1a` }]}><Icon name={item.icon as IconName} size={23} color={item.color} /></View><Text style={[styles.fileName, { color: colors.text }]}>{item.name}</Text><Text style={[styles.fileCount, { color: colors.muted }]}>{item.count}</Text></Pressable>)}</View>
    </PageShell>
  );

  const renderDownloads = () => (
    <PageShell title="Downloads" subtitle="Fila de conteúdos do launcher" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={[styles.downloadSummary, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.downloadRing, { borderColor: colors.accent }]}><Text style={[styles.downloadRingText, { color: colors.text }]}>2</Text><Text style={[styles.downloadRingLabel, { color: colors.muted }]}>ativos</Text></View><View style={{ flex: 1 }}><Text style={[styles.selectedTitle, { color: colors.text }]}>Baixando conteúdo</Text><Text style={[styles.bodyText, { color: colors.muted }]}>1.4 GB de 2.8 GB concluídos</Text><View style={[styles.progressTrack, { backgroundColor: colors.surface2 }]}><View style={[styles.progressFill, { backgroundColor: colors.accent, width: "52%" }]} /></View><Text style={[styles.progressMeta, { color: colors.muted }]}>12.4 MB/s · aprox. 02:18 restantes</Text></View></View>
      {[{ name: "Minecraft 1.21.4", type: "Minecraft", progress: "72%", speed: "12.4 MB/s", color: "#9b7bff" }, { name: "Java Runtime 21", type: "Runtime", progress: "36%", speed: "8.1 MB/s", color: "#c7f35b" }, { name: "Create modpack", type: "Modpack", progress: "Concluído", speed: "412 MB", color: "#ff9f6e" }].map((item) => <DownloadCard key={item.name} item={item} colors={colors} onPress={() => showToast(`${item.name}: ação pausada (mock)`)} />)}
    </PageShell>
  );

  const renderLogs = () => (
    <PageShell title="Console" subtitle="Diagnóstico da última sessão" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={styles.logToolbar}><Pill colors={colors} color={colors.lime}>● ONLINE</Pill><View style={{ flex: 1 }} /><IconButton icon="search" colors={colors} onPress={() => showToast("Pesquisar logs (mock)")} /><IconButton icon="content-copy" colors={colors} onPress={() => showToast("Console copiado")} /><IconButton icon="delete-outline" colors={colors} onPress={() => showToast("Console limpo")} /></View>
      <View style={[styles.console, { backgroundColor: "#08090d", borderColor: colors.border }]}>
        <Text style={styles.consoleText}><Text style={{ color: "#8991a8" }}>16:07:42 </Text><Text style={{ color: "#c7f35b" }}>INFO </Text> Preparing launcher session</Text>
        <Text style={styles.consoleText}><Text style={{ color: "#8991a8" }}>16:07:43 </Text><Text style={{ color: "#9b7bff" }}>DEBUG </Text> Runtime selected: Java 8 ARM64</Text>
        <Text style={styles.consoleText}><Text style={{ color: "#8991a8" }}>16:07:43 </Text><Text style={{ color: "#71d7ff" }}>INFO </Text> Loading profile Survival</Text>
        <Text style={styles.consoleText}><Text style={{ color: "#8991a8" }}>16:07:44 </Text><Text style={{ color: "#ffcc66" }}>WARN </Text> Demo mode enabled — no game will launch</Text>
        <Text style={styles.consoleText}><Text style={{ color: "#8991a8" }}>16:07:44 </Text><Text style={{ color: "#c7f35b" }}>INFO </Text> Visual flow ready</Text>
        <Text style={styles.consoleCursor}>_</Text>
      </View>
      <SectionTitle title="Crash Report" action="Ver histórico" onPress={() => showToast("Nenhum crash recente")} colors={colors} />
      <View style={[styles.crashCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.crashIcon, { backgroundColor: `${colors.lime}1c` }]}><Icon name="check" size={21} color={colors.lime} /></View><View style={{ flex: 1 }}><Text style={[styles.selectedTitle, { color: colors.text }]}>Nenhum problema encontrado</Text><Text style={[styles.bodyText, { color: colors.muted }]}>Sua última sessão terminou normalmente.</Text></View></View>
    </PageShell>
  );

  const renderPlayer = () => (
    <PageShell title="Meu perfil" subtitle="Identidade do jogador" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <View style={[styles.playerHero, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.playerAvatar, { backgroundColor: colors.accentSoft, borderColor: colors.accent }]}><Text style={[styles.playerAvatarText, { color: colors.accent }]}>M</Text></View><Text style={[styles.playerName, { color: colors.text }]}>Mikael Silva</Text><Text style={[styles.bodyText, { color: colors.muted }]}>mikael_dev · {account}</Text><View style={styles.playerTags}><Pill colors={colors} color={colors.lime}>Conta ativa</Pill><Pill colors={colors} color={colors.accent}>UUID verificado</Pill></View></View>
      <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><RowButton icon="badge" label="Nickname" value="Mikael_dev" onPress={() => showToast("Nickname copiado")} colors={colors} /><RowButton icon="fingerprint" label="UUID" value="a13f…7c20" onPress={() => showToast("UUID copiado")} colors={colors} /><RowButton icon="account-balance" label="Tipo de conta" value={account} onPress={() => setModal("account")} colors={colors} /><RowButton icon="sports-esports" label="Minecraft selecionado" value={`Java ${version}`} onPress={() => setModal("version")} colors={colors} /></View>
      <SectionTitle title="Histórico de instalações" colors={colors} />
      {installations.map((item) => <InstallationMini key={item.name} item={item} colors={colors} onPress={() => setModal("install")} />)}
    </PageShell>
  );

  const renderSettings = () => (
    <PageShell title="Configurações" subtitle="Ajuste o launcher ao seu estilo" onMenu={() => setModal("menu")} onAvatar={() => setModal("account")} colors={colors}>
      <SectionTitle title="Aparência" colors={colors} />
      <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
        <View style={[styles.settingRow, { borderBottomColor: colors.border }]}><View style={[styles.rowIcon, { backgroundColor: `${colors.accent}1c` }]}><Icon name="brightness-6" size={18} color={colors.accent} /></View><Text style={[styles.rowLabel, { color: colors.text }]}>Tema</Text><Pressable onPress={() => setTheme(theme === "dark" ? "light" : "dark")} style={[styles.themeToggle, { backgroundColor: colors.surface2 }]}><Text style={[styles.themeToggleText, { color: colors.muted }]}>{theme === "dark" ? "Dark" : "Light"}</Text><Icon name={theme === "dark" ? "dark-mode" : "light-mode"} size={16} color={colors.accent} /></Pressable></View>
        <View style={[styles.settingRow, { borderBottomColor: colors.border }]}><View style={[styles.rowIcon, { backgroundColor: `${colors.lime}1c` }]}><Icon name="color-lens" size={18} color={colors.lime} /></View><Text style={[styles.rowLabel, { color: colors.text }]}>Cor de destaque</Text><View style={styles.colorDots}>{["#9b7bff", "#71d7ff", "#c7f35b", "#ff6f91", "#ff9f6e"].map((color) => <Pressable key={color} onPress={() => setAccent(color)} style={[styles.colorDot, { backgroundColor: color }, accent === color && styles.colorDotActive]} />)}</View></View>
        <ToggleRow label="Animações suaves" value={settings.animations} onChange={(value) => setSetting("animations", value)} colors={colors} />
        <ToggleRow label="Sons de interface" value={settings.sounds} onChange={(value) => setSetting("sounds", value)} colors={colors} />
        <ToggleRow label="Notificações" value={settings.notifications} onChange={(value) => setSetting("notifications", value)} colors={colors} />
      </View>
      <SectionTitle title="Minecraft" colors={colors} />
      <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><RowButton icon="folder" label="Diretório do jogo" value="/.minecraft" onPress={() => navigate("files")} colors={colors} /><RowButton icon="category" label="Versão padrão" value="1.12.2" onPress={() => setModal("version")} colors={colors} /><RowButton icon="coffee" label="Runtime padrão" value={runtime} onPress={() => setModal("runtime")} colors={colors} /><RowButton icon="memory" label="RAM padrão" value={`${memory} MB`} onPress={() => setModal("memory")} colors={colors} /></View>
      <SectionTitle title="Downloads e avançado" colors={colors} />
      <View style={[styles.listCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><ToggleRow label="Somente Wi-Fi" value={settings.wifiOnly} onChange={(value) => setSetting("wifiOnly", value)} colors={colors} /><RowButton icon="speed" label="Limite de velocidade" value="Sem limite" onPress={() => showToast("Limite atualizado")} colors={colors} /><RowButton icon="cleaning-services" label="Limpar cache" value="1.4 GB" onPress={() => showToast("Cache limpo (mock)")} colors={colors} /><ToggleRow label="Modo desenvolvedor" value={settings.devMode} onChange={(value) => setSetting("devMode", value)} colors={colors} /></View>
      <View style={{ height: 24 }} />
    </PageShell>
  );

  const body = screen === "home" ? renderHome() : screen === "installations" ? renderInstallations() : screen === "runtime" ? renderRuntime() : screen === "versions" ? renderVersions() : screen === "accounts" ? renderAccounts() : screen === "mods" ? renderMods() : screen === "modpacks" ? renderModpacks() : screen === "files" ? renderFiles() : screen === "downloads" ? renderDownloads() : screen === "logs" ? renderLogs() : screen === "player" ? renderPlayer() : renderSettings();

  return (
    <ScreenContainer edges={["top", "left", "right", "bottom"]} containerClassName="bg-background">
      <StatusBar style={theme === "dark" ? "light" : "dark"} />
      <View style={[styles.app, { backgroundColor: colors.bg }]}>
        {body}
        <View style={[styles.bottomNav, { backgroundColor: colors.surface, borderTopColor: colors.border }]}>
          {navItems.map((item) => <Pressable key={item.key} onPress={() => navigate(item.key)} style={({ pressed }) => [styles.navItem, pressed && styles.pressed]}><View style={[styles.navIconWrap, screen === item.key && { backgroundColor: colors.accentSoft }]}><Icon name={item.icon} size={20} color={screen === item.key ? colors.accent : colors.muted} /></View><Text style={[styles.navLabel, { color: screen === item.key ? colors.accent : colors.muted }]}>{item.label}</Text></Pressable>)}
        </View>

        {toast ? <View style={[styles.toast, { backgroundColor: colors.surface2, borderColor: colors.border }]}><Icon name="check-circle" size={18} color={colors.lime} /><Text style={[styles.toastText, { color: colors.text }]}>{toast}</Text></View> : null}

        <Modal transparent animationType="slide" visible={modal !== null} onRequestClose={() => setModal(null)}>
          <View style={styles.modalBackdrop}>
            <Pressable style={StyleSheet.absoluteFill} onPress={() => setModal(null)} />
            {modal === "menu" ? <MenuSheet colors={colors} active={screen} onSelect={navigate} onClose={() => setModal(null)} /> : null}
            {modal === "account" ? <AccountSheet account={account} colors={colors} onSelect={(next) => { setAccount(next); setModal(null); showToast(`${next} definida como ativa`); }} onClose={() => setModal(null)} /> : null}
            {modal === "install" ? <InstallSheet version={version} runtime={runtime} memory={memory} colors={colors} onVersion={() => setModal("version")} onRuntime={() => setModal("runtime")} onMemory={() => setModal("memory")} onClose={() => setModal(null)} /> : null}
            {modal === "runtime" ? <RuntimeSheet runtime={runtime} colors={colors} onSelect={(next) => { setRuntime(next); setModal(null); showToast(`${next} selecionado`); }} onClose={() => setModal(null)} /> : null}
            {modal === "memory" ? <MemorySheet memory={memory} colors={colors} onSelect={(next) => { setMemory(next); setModal(null); showToast(`${next} MB configurados`); }} onClose={() => setModal(null)} /> : null}
            {modal === "version" ? <VersionSheet version={version} colors={colors} onSelect={(next) => { setVersion(next); setModal(null); showToast(`Minecraft ${next} selecionado`); }} onClose={() => setModal(null)} /> : null}
          </View>
        </Modal>
      </View>
    </ScreenContainer>
  );
}

function PageShell({ title, subtitle, onMenu, onAvatar, colors, children }: { title: string; subtitle: string; onMenu: () => void; onAvatar: () => void; colors: Colors; children: React.ReactNode }) {
  return <><EmptyHeader title={title} subtitle={subtitle} onMenu={onMenu} onAvatar={onAvatar} colors={colors} /><ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={styles.scrollContent}>{children}<View style={{ height: 24 }} /></ScrollView></>;
}

function QuickItem({ icon, label, value, color, onPress, colors }: { icon: IconName; label: string; value: string; color: string; onPress: () => void; colors: Colors }) {
  return <Pressable onPress={onPress} style={({ pressed }) => [styles.quickItem, pressed && styles.pressed]}><View style={[styles.quickIcon, { backgroundColor: `${color}1c` }]}><Icon name={icon} size={19} color={color} /></View><Text style={[styles.quickLabel, { color: colors.muted }]}>{label}</Text><Text numberOfLines={1} style={[styles.quickValue, { color: colors.text }]}>{value}</Text><Icon name="chevron-right" size={16} color={colors.dim} /></Pressable>;
}

function InstallationMini({ item, colors, onPress }: { item: typeof installations[number]; colors: Colors; onPress: () => void }) {
  return <Pressable onPress={onPress} style={({ pressed }) => [styles.installMini, { backgroundColor: colors.surface, borderColor: colors.border }, pressed && styles.pressed]}><View style={[styles.installMiniIcon, { backgroundColor: `${item.color}1c` }]}><Icon name="sports-esports" size={19} color={item.color} /></View><View style={{ flex: 1 }}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>Minecraft {item.version} · {item.loader} · {item.ram}</Text></View><Icon name="chevron-right" size={19} color={colors.dim} /></Pressable>;
}

function InstallationCard({ item, colors, onConfigure, onPlay }: { item: typeof installations[number]; colors: Colors; onConfigure: () => void; onPlay: () => void }) {
  return <View style={[styles.installCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.installCardStripe, { backgroundColor: item.color }]} /><View style={styles.installCardTop}><View style={[styles.installMiniIcon, { backgroundColor: `${item.color}1c` }]}><Icon name="sports-esports" size={20} color={item.color} /></View><View style={{ flex: 1 }}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>Perfil personalizado · {item.mods} mods</Text></View><Pressable onPress={onConfigure}><Icon name="more-horiz" size={21} color={colors.muted} /></Pressable></View><View style={styles.installTags}><Pill colors={colors} color={item.color}>Minecraft {item.version}</Pill><Pill colors={colors} color={colors.muted}>{item.loader}</Pill><Pill colors={colors} color={colors.muted}>{item.java}</Pill></View><View style={styles.installCardBottom}><Text style={[styles.installDetail, { color: colors.muted }]}>{item.ram} · pronto para jogar</Text><Pressable onPress={onPlay} style={({ pressed }) => [styles.smallPlay, { backgroundColor: colors.lime }, pressed && styles.buttonPressed]}><Icon name="play-arrow" size={17} color="#11120d" /><Text style={styles.smallPlayText}>Jogar</Text></Pressable></View></View>;
}

function RuntimeCard({ item, selected, colors, onPress }: { item: typeof runtimeItems[number]; selected: boolean; colors: Colors; onPress: () => void }) {
  return <Pressable onPress={onPress} style={({ pressed }) => [styles.runtimeCard, { backgroundColor: colors.surface, borderColor: selected ? item.color : colors.border }, pressed && styles.pressed]}><View style={[styles.runtimeIcon, { backgroundColor: `${item.color}1c` }]}><Icon name="coffee" size={21} color={item.color} /></View><View style={{ flex: 1 }}><View style={styles.runtimeTitleRow}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text>{selected ? <Pill colors={colors} color={colors.lime}>Padrão</Pill> : null}</View><Text style={[styles.installDetail, { color: colors.muted }]}>{item.version} · {item.arch} · {item.size}</Text><Text style={[styles.runtimeStatus, { color: item.status === "Instalado" ? colors.lime : colors.muted }]}>{item.status}</Text></View><Icon name={selected ? "radio-button-checked" : "radio-button-unchecked"} size={22} color={selected ? item.color : colors.dim} /></Pressable>;
}

function VersionCard({ item, favorite, colors, onFavorite, onInstall }: { item: typeof versions[number]; favorite: boolean; colors: Colors; onFavorite: () => void; onInstall: () => void }) {
  return <View style={[styles.versionCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.versionIcon, { backgroundColor: `${colors.accent}1c` }]}><Icon name="view-in-ar" size={21} color={colors.accent} /></View><View style={{ flex: 1 }}><View style={styles.versionTitleRow}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text><Pressable onPress={onFavorite}><Icon name={favorite ? "star" : "star-border"} size={20} color={favorite ? "#ffd166" : colors.dim} /></Pressable></View><View style={styles.inlineTags}><Pill colors={colors} color={item.type === "Snapshot" ? "#ff9f6e" : colors.lime}>{item.type}</Pill><Text style={[styles.installDetail, { color: colors.muted }]}>{item.loader} · {item.java} · {item.size}</Text></View><View style={styles.versionBottom}><Text style={[styles.installDetail, { color: item.installed ? colors.lime : colors.muted }]}>{item.installed ? "✓ Instalada" : "Não instalada"}</Text><Pressable onPress={onInstall} style={[styles.outlineSmallButton, { borderColor: colors.border }]}><Text style={[styles.outlineSmallText, { color: colors.text }]}>{item.installed ? "Configurar" : "Instalar"}</Text></Pressable></View></View></View>;
}

function AccountCard({ provider, username, uuid, icon, active, color, colors, onSelect, onManage }: { provider: string; username: string; uuid: string; icon: IconName; active: boolean; color: string; colors: Colors; onSelect: () => void; onManage: () => void }) {
  return <Pressable onPress={onSelect} style={({ pressed }) => [styles.accountCard, { backgroundColor: colors.surface, borderColor: active ? color : colors.border }, pressed && styles.pressed]}><View style={[styles.providerIcon, { backgroundColor: `${color}1c` }]}><Icon name={icon} size={22} color={color} /></View><View style={{ flex: 1 }}><View style={styles.versionTitleRow}><Text style={[styles.installName, { color: colors.text }]}>{provider}</Text>{active ? <Pill colors={colors} color={colors.lime}>Ativa</Pill> : null}</View><Text style={[styles.accountUsername, { color: colors.text }]}>{username}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>UUID {uuid} · <Text style={{ color: colors.lime }}>Conectada</Text></Text><View style={styles.accountCardActions}><Pressable onPress={onManage}><Text style={[styles.sectionAction, { color: colors.accent }]}>Gerenciar</Text></Pressable><Text style={[styles.accountActionDivider, { color: colors.border }]}>•</Text><Pressable onPress={onSelect}><Text style={[styles.sectionAction, { color: colors.muted }]}>{active ? "Selecionada" : "Selecionar"}</Text></Pressable></View></View></Pressable>;
}

function ModCard({ item, colors, onToggle, onRemove }: { item: typeof modItems[number]; colors: Colors; onToggle: () => void; onRemove: () => void }) {
  return <View style={[styles.modCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.modIcon, { backgroundColor: `${item.color}1c` }]}><Icon name="extension" size={21} color={item.color} /></View><View style={{ flex: 1 }}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>{item.version} · {item.loader} · dependências OK</Text><View style={styles.modBottom}><Text style={[styles.installDetail, { color: item.enabled ? colors.lime : colors.muted }]}>{item.enabled ? "Ativado" : "Desativado"}</Text><Pressable onPress={onRemove}><Icon name="delete-outline" size={18} color={colors.muted} /></Pressable></View></View><Switch value={item.enabled} onValueChange={onToggle} trackColor={{ false: colors.surface2, true: `${colors.lime}66` }} thumbColor={item.enabled ? colors.lime : colors.muted} /> </View>;
}

function ModpackCard({ item, colors, onPress }: { item: typeof packItems[number]; colors: Colors; onPress: () => void }) {
  return <Pressable onPress={onPress} style={({ pressed }) => [styles.packCard, { backgroundColor: colors.surface, borderColor: colors.border }, pressed && styles.pressed]}><View style={[styles.packCover, { backgroundColor: item.color }]}><Icon name="inventory-2" size={29} color="#15131d" /><Text style={styles.packCoverText}>PACK</Text></View><View style={styles.packInfo}><View style={styles.versionTitleRow}><Text style={[styles.installName, { color: colors.text, flex: 1 }]}>{item.name}</Text><Icon name="chevron-right" size={20} color={colors.dim} /></View><Text style={[styles.installDetail, { color: colors.muted }]}>{item.mc} · {item.loader} · v{item.version}</Text><View style={styles.packMeta}><Pill colors={colors} color={item.color}>{item.mods} mods</Pill><Text style={[styles.installDetail, { color: colors.muted }]}>RAM {item.ram}</Text></View></View></Pressable>;
}

function DownloadCard({ item, colors, onPress }: { item: { name: string; type: string; progress: string; speed: string; color: string }; colors: Colors; onPress: () => void }) {
  const complete = item.progress === "Concluído";
  return <View style={[styles.downloadCard, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={[styles.downloadIcon, { backgroundColor: `${item.color}1c` }]}><Icon name={complete ? "check" : "download"} size={20} color={item.color} /></View><View style={{ flex: 1 }}><View style={styles.versionTitleRow}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text><Text style={[styles.installDetail, { color: item.color }]}>{item.progress}</Text></View><Text style={[styles.installDetail, { color: colors.muted }]}>{item.type} · {item.speed}</Text><View style={[styles.progressTrack, { backgroundColor: colors.surface2 }]}><View style={[styles.progressFill, { backgroundColor: item.color, width: (complete ? "100%" : item.progress) as `${number}%` }]} /></View></View>{!complete ? <Pressable onPress={onPress}><Icon name="pause" size={20} color={colors.muted} /></Pressable> : <Icon name="more-horiz" size={20} color={colors.muted} />}</View>;
}

function ActionChip({ icon, label, onPress, colors }: { icon: IconName; label: string; onPress: () => void; colors: Colors }) {
  return <Pressable onPress={onPress} style={({ pressed }) => [styles.actionChip, { backgroundColor: colors.surface, borderColor: colors.border }, pressed && styles.pressed]}><Icon name={icon} size={18} color={colors.accent} /><Text style={[styles.actionChipText, { color: colors.text }]}>{label}</Text></Pressable>;
}

function IconButton({ icon, colors, onPress }: { icon: IconName; colors: Colors; onPress: () => void }) {
  return <Pressable onPress={onPress} style={({ pressed }) => [styles.iconButton, { backgroundColor: colors.surface, borderColor: colors.border }, pressed && styles.pressed]}><Icon name={icon} size={18} color={colors.muted} /></Pressable>;
}

function ToggleRow({ label, value, onChange, colors }: { label: string; value: boolean; onChange: (value: boolean) => void; colors: Colors }) {
  return <View style={[styles.settingRow, { borderBottomColor: colors.border }]}><Text style={[styles.rowLabel, { color: colors.text, marginLeft: 0 }]}>{label}</Text><Switch value={value} onValueChange={onChange} trackColor={{ false: colors.surface2, true: `${colors.accent}66` }} thumbColor={value ? colors.accent : colors.muted} /></View>;
}

function SheetFrame({ title, subtitle, colors, onClose, children }: { title: string; subtitle?: string; colors: Colors; onClose: () => void; children: React.ReactNode }) {
  return <View style={[styles.sheet, { backgroundColor: colors.surface, borderColor: colors.border }]}><View style={styles.sheetHandle} /><View style={styles.sheetHeader}><View><Text style={[styles.sheetTitle, { color: colors.text }]}>{title}</Text>{subtitle ? <Text style={[styles.bodyText, { color: colors.muted }]}>{subtitle}</Text> : null}</View><Pressable onPress={onClose} style={[styles.sheetClose, { backgroundColor: colors.surface2 }]}><Icon name="close" size={19} color={colors.muted} /></Pressable></View>{children}</View>;
}

function AccountSheet({ account, colors, onSelect, onClose }: { account: string; colors: Colors; onSelect: (account: string) => void; onClose: () => void }) {
  return <SheetFrame title="Troca rápida de conta" subtitle="Selecione uma conta para esta sessão" colors={colors} onClose={onClose}>{["Microsoft", "Offline", "Ely.by"].map((item, index) => <Pressable key={item} onPress={() => onSelect(item)} style={({ pressed }) => [styles.sheetOption, { borderColor: account === item ? colors.accent : colors.border }, pressed && styles.pressed]}><View style={[styles.sheetOptionIcon, { backgroundColor: `${["#71d7ff", "#c7f35b", "#ff9f6e"][index]}1c` }]}><Icon name={["window", "person-outline", "alternate-email"][index] as IconName} size={21} color={["#71d7ff", "#c7f35b", "#ff9f6e"][index]} /></View><View style={{ flex: 1 }}><Text style={[styles.installName, { color: colors.text }]}>{item}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>{item === account ? "Conta ativa agora" : "Toque para selecionar"}</Text></View><Icon name={account === item ? "radio-button-checked" : "radio-button-unchecked"} size={21} color={account === item ? colors.accent : colors.dim} /></Pressable>)}<Pressable onPress={() => { onClose(); }} style={[styles.sheetCta, { backgroundColor: colors.accent }]}><Icon name="add" size={20} color="#fff" /><Text style={styles.sheetCtaText}>Adicionar conta</Text></Pressable><Text style={[styles.sheetFootnote, { color: colors.muted }]}>Apenas interface demonstrativa. Login real será integrado em uma etapa futura.</Text></SheetFrame>;
}

function InstallSheet({ version, runtime, memory, colors, onVersion, onRuntime, onMemory, onClose }: { version: string; runtime: string; memory: number; colors: Colors; onVersion: () => void; onRuntime: () => void; onMemory: () => void; onClose: () => void }) {
  return <SheetFrame title="Configurar instalação" subtitle="Survival · perfil padrão" colors={colors} onClose={onClose}><View style={[styles.installNameInput, { backgroundColor: colors.surface2, borderColor: colors.border }]}><Text style={[styles.installInputLabel, { color: colors.muted }]}>NOME DA INSTALAÇÃO</Text><Text style={[styles.installInputValue, { color: colors.text }]}>Survival</Text></View><RowButton icon="category" label="Versão do Minecraft" value={version} onPress={onVersion} colors={colors} /><RowButton icon="extension" label="Loader" value="Forge 14.23.5.2864" onPress={() => {}} colors={colors} /><RowButton icon="coffee" label="Runtime" value={runtime} onPress={onRuntime} colors={colors} /><RowButton icon="memory" label="Memória máxima" value={`${memory} MB`} onPress={onMemory} colors={colors} /><Pressable onPress={onClose} style={[styles.sheetCta, { backgroundColor: colors.lime }]}><Icon name="save" size={19} color="#11120d" /><Text style={[styles.sheetCtaText, { color: "#11120d" }]}>Salvar configuração</Text></Pressable></SheetFrame>;
}

function RuntimeSheet({ runtime, colors, onSelect, onClose }: { runtime: string; colors: Colors; onSelect: (runtime: string) => void; onClose: () => void }) {
  return <SheetFrame title="Runtime padrão" subtitle="Escolha o Java usado ao iniciar" colors={colors} onClose={onClose}>{runtimeItems.slice(0, 3).map((item) => <Pressable key={item.name} onPress={() => onSelect(item.name)} style={({ pressed }) => [styles.sheetOption, { borderColor: runtime === item.name ? item.color : colors.border }, pressed && styles.pressed]}><View style={[styles.sheetOptionIcon, { backgroundColor: `${item.color}1c` }]}><Icon name="coffee" size={21} color={item.color} /></View><View style={{ flex: 1 }}><Text style={[styles.installName, { color: colors.text }]}>{item.name}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>{item.version} · {item.arch}</Text></View><Icon name={runtime === item.name ? "radio-button-checked" : "radio-button-unchecked"} size={21} color={runtime === item.name ? item.color : colors.dim} /></Pressable>)}</SheetFrame>;
}

function MemorySheet({ memory, colors, onSelect, onClose }: { memory: number; colors: Colors; onSelect: (memory: number) => void; onClose: () => void }) {
  const options = MEMORY_PRESETS;
  return <SheetFrame title="Memória" subtitle="RAM máxima para a instalação" colors={colors} onClose={onClose}><View style={[styles.memoryHero, { backgroundColor: colors.surface2 }]}><Text style={[styles.memoryNumber, { color: colors.text }]}>{memory} <Text style={[styles.memoryUnit, { color: colors.muted }]}>MB</Text></Text><Text style={[styles.bodyText, { color: colors.muted }]}>{getMemoryStatus(memory).detail}</Text></View><Text style={[styles.eyebrow, { color: colors.muted, marginBottom: 10 }]}>PRESETS</Text><View style={styles.memoryGrid}>{options.map((item) => <Pressable key={item} onPress={() => onSelect(item)} style={[styles.memoryOption, { backgroundColor: memory === item ? colors.accentSoft : colors.surface2, borderColor: memory === item ? colors.accent : colors.border }]}><Text style={[styles.memoryOptionText, { color: memory === item ? colors.accent : colors.text }]}>{item} MB</Text></Pressable>)}</View><View style={[styles.memoryWarning, { backgroundColor: `${colors.lime}12`, borderColor: `${colors.lime}35` }]}><Icon name="info-outline" size={18} color={colors.lime} /><Text style={[styles.installDetail, { color: colors.muted, flex: 1 }]}>{getMemoryStatus(memory).detail}</Text></View></SheetFrame>;
}

function VersionSheet({ version, colors, onSelect, onClose }: { version: string; colors: Colors; onSelect: (version: string) => void; onClose: () => void }) {
  return <SheetFrame title="Versão do Minecraft" subtitle="Versões instaladas e compatíveis" colors={colors} onClose={onClose}>{["1.21.4", "1.20.1", "1.12.2", "1.8.9", "1.7.10"].map((item) => <Pressable key={item} onPress={() => onSelect(item)} style={({ pressed }) => [styles.versionOption, { borderColor: version === item ? colors.accent : colors.border }, pressed && styles.pressed]}><View style={[styles.versionDot, { backgroundColor: version === item ? colors.accent : colors.surface2 }]}><Icon name="view-in-ar" size={18} color={version === item ? "#fff" : colors.muted} /></View><Text style={[styles.installName, { color: colors.text, flex: 1 }]}>Minecraft {item}</Text>{version === item ? <Icon name="check" size={20} color={colors.accent} /> : <Text style={[styles.installDetail, { color: colors.muted }]}>Disponível</Text>}</Pressable>)}</SheetFrame>;
}

function MenuSheet({ active, colors, onSelect, onClose }: { active: ScreenKey; colors: Colors; onSelect: (key: ScreenKey) => void; onClose: () => void }) {
  return <SheetFrame title="Menu do launcher" subtitle="Tudo em um só lugar" colors={colors} onClose={onClose}><ScrollView style={{ maxHeight: 470 }} showsVerticalScrollIndicator={false}>{menuItems.map((item) => <Pressable key={item.key} onPress={() => onSelect(item.key)} style={({ pressed }) => [styles.menuOption, { backgroundColor: active === item.key ? colors.accentSoft : "transparent" }, pressed && styles.pressed]}><View style={[styles.menuIcon, { backgroundColor: active === item.key ? `${colors.accent}2c` : colors.surface2 }]}><Icon name={item.icon} size={19} color={active === item.key ? colors.accent : colors.muted} /></View><View style={{ flex: 1 }}><Text style={[styles.installName, { color: colors.text }]}>{item.label}</Text><Text style={[styles.installDetail, { color: colors.muted }]}>{item.description}</Text></View>{active === item.key ? <Icon name="check" size={19} color={colors.accent} /> : <Icon name="chevron-right" size={18} color={colors.dim} />}</Pressable>)}</ScrollView></SheetFrame>;
}

const styles = StyleSheet.create({
  app: { flex: 1 },
  scrollContent: { paddingHorizontal: 18, paddingBottom: 10 },
  header: { minHeight: 70, paddingHorizontal: 18, flexDirection: "row", alignItems: "center", gap: 12 },
  headerIcon: { width: 38, height: 38, borderRadius: 13, alignItems: "center", justifyContent: "center" },
  headerTitles: { flex: 1 },
  headerTitle: { fontSize: 16, fontWeight: "900", letterSpacing: 1.2 },
  headerSubtitle: { fontSize: 11, marginTop: 2 },
  headerRight: { flexDirection: "row", alignItems: "center", gap: 9 },
  notificationDot: { width: 6, height: 6, borderRadius: 6, position: "absolute", right: 6, top: 7 },
  avatar: { width: 36, height: 36, borderRadius: 13, alignItems: "center", justifyContent: "center", borderWidth: 1 },
  avatarText: { fontSize: 16, fontWeight: "900" },
  greetingRow: { flexDirection: "row", alignItems: "flex-end", justifyContent: "space-between", paddingTop: 13, paddingBottom: 19, gap: 12 },
  eyebrow: { fontSize: 10, fontWeight: "900", letterSpacing: 1.25 },
  greeting: { fontSize: 27, fontWeight: "900", letterSpacing: -0.7, marginTop: 5 },
  bodyText: { fontSize: 12, lineHeight: 18 },
  accountMini: { flexDirection: "row", alignItems: "center", gap: 5, borderWidth: 1, paddingVertical: 7, paddingHorizontal: 8, borderRadius: 10, maxWidth: 112 },
  accountStatus: { width: 7, height: 7, borderRadius: 7 },
  accountMiniText: { fontSize: 10, fontWeight: "800", flexShrink: 1 },
  heroCard: { borderRadius: 22, borderWidth: 1, padding: 17, overflow: "hidden", marginBottom: 24 },
  heroGlow: { position: "absolute", width: 175, height: 175, borderRadius: 100, opacity: 0.13, right: -60, top: -70 },
  heroGlowSmall: { position: "absolute", width: 80, height: 80, borderRadius: 60, opacity: 0.08, left: -30, bottom: 62 },
  heroTopLine: { flexDirection: "row", alignItems: "center", gap: 11 },
  mcBadge: { width: 42, height: 42, borderRadius: 14, backgroundColor: "#7b5ce1", alignItems: "center", justifyContent: "center", transform: [{ rotate: "-4deg" }] },
  mcBadgeText: { color: "#fff", fontSize: 24, fontWeight: "900", textShadowColor: "#31236a", textShadowOffset: { width: 1, height: 2 }, textShadowRadius: 1 },
  heroKicker: { fontSize: 9, fontWeight: "900", letterSpacing: 1.1 },
  heroTitle: { fontSize: 20, fontWeight: "900", marginTop: 2 },
  moreButton: { width: 33, height: 33, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  heroVersionRow: { flexDirection: "row", alignItems: "center", flexWrap: "wrap", gap: 7, marginTop: 17 },
  versionNumber: { fontSize: 15, fontWeight: "800", marginRight: 3 },
  pill: { borderWidth: 1, paddingHorizontal: 8, paddingVertical: 4, borderRadius: 7, alignSelf: "flex-start" },
  pillText: { fontSize: 9, fontWeight: "800", letterSpacing: 0.2 },
  heroDivider: { height: 1, backgroundColor: "#ffffff12", marginVertical: 16 },
  heroMetaRow: { flexDirection: "row", justifyContent: "space-between", marginBottom: 16 },
  metaLabel: { fontSize: 9, fontWeight: "800", letterSpacing: 0.8, marginBottom: 5 },
  metaValue: { fontSize: 12, fontWeight: "800" },
  statusInline: { flexDirection: "row", alignItems: "center", gap: 5 },
  statusDot: { width: 6, height: 6, borderRadius: 6 },
  playButton: { minHeight: 52, borderRadius: 14, alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 7 },
  playButtonText: { color: "#11120d", fontWeight: "900", fontSize: 13, letterSpacing: 0.8 },
  heroActions: { flexDirection: "row", gap: 8, marginTop: 9 },
  secondaryButton: { flex: 1, height: 37, borderWidth: 1, borderRadius: 11, alignItems: "center", justifyContent: "center", flexDirection: "row", gap: 6 },
  secondaryButtonText: { fontSize: 11, fontWeight: "800" },
  sectionTitle: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 11, marginTop: 5 },
  sectionHeading: { fontSize: 15, fontWeight: "900", letterSpacing: -0.2 },
  sectionAction: { fontSize: 11, fontWeight: "800" },
  quickGrid: { borderWidth: 1, borderRadius: 17, paddingVertical: 4, paddingHorizontal: 12, flexDirection: "row", flexWrap: "wrap", marginBottom: 22 },
  quickItem: { width: "50%", minHeight: 62, flexDirection: "row", alignItems: "center", gap: 8, borderBottomWidth: 0.5, borderBottomColor: "#ffffff12" },
  quickIcon: { width: 32, height: 32, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  quickLabel: { fontSize: 10, position: "absolute", left: 41, top: 10 },
  quickValue: { fontSize: 11, fontWeight: "800", marginTop: 14, maxWidth: 66 },
  statsRow: { flexDirection: "row", gap: 8, marginBottom: 22 },
  statCard: { flex: 1, borderWidth: 1, borderRadius: 15, padding: 10, minHeight: 88 },
  statIcon: { width: 29, height: 29, borderRadius: 9, alignItems: "center", justifyContent: "center", marginBottom: 6 },
  statValue: { fontSize: 14, fontWeight: "900" },
  statLabel: { fontSize: 9, marginTop: 3 },
  newsCard: { minHeight: 112, borderWidth: 1, borderRadius: 17, padding: 14, flexDirection: "row", gap: 11, alignItems: "center", marginBottom: 22 },
  newsAccent: { width: 4, height: 67, borderRadius: 4 },
  newsTag: { fontSize: 9, fontWeight: "900", letterSpacing: 0.7, marginBottom: 6 },
  newsTitle: { fontSize: 14, fontWeight: "900", lineHeight: 18, marginBottom: 4 },
  newsBody: { fontSize: 10, lineHeight: 15 },
  installMini: { minHeight: 65, borderWidth: 1, borderRadius: 15, paddingHorizontal: 12, paddingVertical: 10, flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 8 },
  installMiniIcon: { width: 36, height: 36, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  installName: { fontSize: 13, fontWeight: "800" },
  installDetail: { fontSize: 10, marginTop: 4 },
  infoBanner: { flexDirection: "row", alignItems: "center", gap: 11, padding: 13, borderRadius: 15, marginTop: 6, marginBottom: 11 },
  bannerTitle: { fontSize: 12, fontWeight: "800" },
  bannerText: { fontSize: 10, marginTop: 2 },
  addButton: { height: 47, borderRadius: 14, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 7, marginBottom: 23 },
  addButtonText: { color: "#fff", fontSize: 12, fontWeight: "900" },
  installCard: { borderWidth: 1, borderRadius: 17, padding: 14, marginBottom: 10, overflow: "hidden" },
  installCardStripe: { position: "absolute", left: 0, top: 0, bottom: 0, width: 3 },
  installCardTop: { flexDirection: "row", alignItems: "center", gap: 10 },
  installTags: { flexDirection: "row", gap: 6, marginTop: 14, flexWrap: "wrap" },
  installCardBottom: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 13 },
  smallPlay: { borderRadius: 9, paddingVertical: 7, paddingHorizontal: 11, flexDirection: "row", alignItems: "center", gap: 3 },
  smallPlayText: { fontSize: 10, fontWeight: "900", color: "#11120d" },
  listCard: { borderWidth: 1, borderRadius: 17, paddingHorizontal: 13, marginBottom: 20 },
  rowButton: { minHeight: 57, flexDirection: "row", alignItems: "center", borderBottomWidth: 0.5, gap: 9 },
  rowIcon: { width: 32, height: 32, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  rowLabel: { fontSize: 12, fontWeight: "700", flex: 1 },
  rowValue: { fontSize: 10, maxWidth: 120, textAlign: "right" },
  selectedRuntimeCard: { borderWidth: 1, borderRadius: 17, padding: 15, flexDirection: "row", alignItems: "center", gap: 12, marginTop: 6, marginBottom: 12 },
  bigIconCircle: { width: 48, height: 48, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  selectedTitle: { fontSize: 14, fontWeight: "900" },
  actionChips: { flexDirection: "row", gap: 7, marginBottom: 23 },
  actionChip: { flex: 1, minHeight: 45, borderWidth: 1, borderRadius: 13, alignItems: "center", justifyContent: "center", gap: 4, paddingHorizontal: 4 },
  actionChipText: { fontSize: 9, fontWeight: "800" },
  runtimeCard: { borderWidth: 1, borderRadius: 16, padding: 13, flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 9 },
  runtimeIcon: { width: 39, height: 39, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  runtimeTitleRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 8 },
  runtimeStatus: { fontSize: 10, marginTop: 7, fontWeight: "700" },
  filterRow: { gap: 7, paddingVertical: 8, marginBottom: 7 },
  filterChip: { borderWidth: 1, borderRadius: 9, paddingHorizontal: 11, paddingVertical: 8 },
  filterText: { fontSize: 10, fontWeight: "800" },
  searchBox: { borderWidth: 1, borderRadius: 13, minHeight: 44, paddingHorizontal: 12, flexDirection: "row", alignItems: "center", gap: 8, marginBottom: 13 },
  searchInput: { flex: 1, fontSize: 12, paddingVertical: 0 },
  versionCard: { borderWidth: 1, borderRadius: 16, padding: 13, flexDirection: "row", gap: 10, marginBottom: 9 },
  versionIcon: { width: 39, height: 39, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  versionTitleRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: 8 },
  inlineTags: { flexDirection: "row", alignItems: "center", gap: 8, marginTop: 7 },
  versionBottom: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 10 },
  outlineSmallButton: { borderWidth: 1, borderRadius: 8, paddingHorizontal: 9, paddingVertical: 6 },
  outlineSmallText: { fontSize: 9, fontWeight: "800" },
  accountHero: { borderRadius: 17, padding: 15, flexDirection: "row", alignItems: "center", gap: 11, marginTop: 6, marginBottom: 23 },
  accountHeroIcon: { width: 46, height: 46, borderRadius: 15, alignItems: "center", justifyContent: "center" },
  onlineBadge: { width: 9, height: 9, borderRadius: 9 },
  accountCard: { borderWidth: 1, borderRadius: 17, padding: 14, flexDirection: "row", gap: 11, marginBottom: 10 },
  providerIcon: { width: 42, height: 42, borderRadius: 14, alignItems: "center", justifyContent: "center" },
  accountUsername: { fontSize: 12, fontWeight: "800", marginTop: 7 },
  accountCardActions: { flexDirection: "row", alignItems: "center", gap: 9, marginTop: 10 },
  accountActionDivider: { fontSize: 12 },
  warningCard: { borderWidth: 1, borderRadius: 14, padding: 12, flexDirection: "row", gap: 9, alignItems: "center" },
  warningText: { flex: 1, fontSize: 10, lineHeight: 15 },
  modToolbar: { flexDirection: "row", gap: 8, marginTop: 6, alignItems: "center" },
  squareButton: { width: 45, height: 45, borderRadius: 13, alignItems: "center", justifyContent: "center" },
  modSummary: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 12 },
  modCard: { borderWidth: 1, borderRadius: 16, padding: 12, flexDirection: "row", gap: 10, alignItems: "center", marginBottom: 9 },
  modIcon: { width: 40, height: 40, borderRadius: 13, alignItems: "center", justifyContent: "center" },
  modBottom: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 8 },
  packCard: { borderWidth: 1, borderRadius: 17, padding: 11, flexDirection: "row", gap: 11, marginBottom: 10 },
  packCover: { width: 78, height: 84, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  packCoverText: { fontSize: 9, fontWeight: "900", marginTop: 4, color: "#15131d", letterSpacing: 1 },
  packInfo: { flex: 1, justifyContent: "center" },
  packMeta: { flexDirection: "row", alignItems: "center", gap: 8, marginTop: 11 },
  pathBar: { borderWidth: 1, borderRadius: 13, paddingHorizontal: 12, minHeight: 45, flexDirection: "row", alignItems: "center", gap: 8, marginTop: 6, marginBottom: 12 },
  pathText: { fontSize: 10, fontWeight: "700", flex: 1 },
  fileActions: { flexDirection: "row", gap: 7, marginBottom: 16 },
  fileGrid: { borderWidth: 1, borderRadius: 17, padding: 8, flexDirection: "row", flexWrap: "wrap", marginBottom: 20 },
  fileTile: { width: "50%", minHeight: 102, padding: 9, borderBottomWidth: 0.5, alignItems: "flex-start" },
  fileIcon: { width: 36, height: 36, borderRadius: 11, alignItems: "center", justifyContent: "center", marginBottom: 7 },
  fileName: { fontSize: 12, fontWeight: "800" },
  fileCount: { fontSize: 9, marginTop: 3 },
  downloadSummary: { borderWidth: 1, borderRadius: 17, padding: 14, flexDirection: "row", alignItems: "center", gap: 13, marginTop: 6, marginBottom: 18 },
  downloadRing: { width: 68, height: 68, borderRadius: 68, borderWidth: 4, alignItems: "center", justifyContent: "center" },
  downloadRingText: { fontSize: 20, fontWeight: "900" },
  downloadRingLabel: { fontSize: 9, marginTop: -2 },
  progressTrack: { height: 5, borderRadius: 5, overflow: "hidden", marginTop: 10 },
  progressFill: { height: "100%", borderRadius: 5 },
  progressMeta: { fontSize: 9, marginTop: 6 },
  downloadCard: { borderWidth: 1, borderRadius: 16, padding: 12, flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 9 },
  downloadIcon: { width: 39, height: 39, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  logToolbar: { flexDirection: "row", alignItems: "center", gap: 7, marginTop: 7, marginBottom: 10 },
  iconButton: { width: 34, height: 34, borderRadius: 10, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  console: { minHeight: 238, borderWidth: 1, borderRadius: 16, padding: 14, marginBottom: 22 },
  consoleText: { color: "#c4c7d4", fontFamily: "monospace", fontSize: 10, lineHeight: 20 },
  consoleCursor: { color: "#c7f35b", fontFamily: "monospace", fontSize: 14, marginTop: 5 },
  crashCard: { borderWidth: 1, borderRadius: 16, padding: 14, flexDirection: "row", alignItems: "center", gap: 11 },
  crashIcon: { width: 40, height: 40, borderRadius: 13, alignItems: "center", justifyContent: "center" },
  playerHero: { borderWidth: 1, borderRadius: 19, padding: 20, alignItems: "center", marginTop: 6, marginBottom: 16 },
  playerAvatar: { width: 82, height: 82, borderRadius: 29, borderWidth: 2, alignItems: "center", justifyContent: "center", marginBottom: 10 },
  playerAvatarText: { fontSize: 38, fontWeight: "900" },
  playerName: { fontSize: 20, fontWeight: "900" },
  playerTags: { flexDirection: "row", gap: 7, marginTop: 12 },
  settingRow: { minHeight: 56, borderBottomWidth: 0.5, flexDirection: "row", alignItems: "center", gap: 9 },
  themeToggle: { borderRadius: 8, paddingVertical: 6, paddingHorizontal: 9, flexDirection: "row", gap: 5, alignItems: "center" },
  themeToggleText: { fontSize: 10, fontWeight: "800" },
  colorDots: { flexDirection: "row", gap: 7 },
  colorDot: { width: 18, height: 18, borderRadius: 18 },
  colorDotActive: { borderWidth: 2, borderColor: "#fff", transform: [{ scale: 1.15 }] },
  bottomNav: { minHeight: 69, borderTopWidth: 1, flexDirection: "row", alignItems: "center", justifyContent: "space-around", paddingHorizontal: 3, paddingBottom: 3 },
  navItem: { alignItems: "center", justifyContent: "center", minWidth: 48, gap: 2 },
  navIconWrap: { width: 38, height: 28, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  navLabel: { fontSize: 8, fontWeight: "800" },
  toast: { position: "absolute", bottom: 78, left: 18, right: 18, minHeight: 48, borderWidth: 1, borderRadius: 14, paddingHorizontal: 13, flexDirection: "row", gap: 8, alignItems: "center", shadowColor: "#000", shadowOpacity: 0.3, shadowRadius: 10, elevation: 5 },
  toastText: { fontSize: 11, fontWeight: "700", flex: 1 },
  pressed: { opacity: 0.72 },
  buttonPressed: { transform: [{ scale: 0.97 }], opacity: 0.9 },
  modalBackdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: "#00000099" },
  sheet: { borderTopLeftRadius: 27, borderTopRightRadius: 27, borderWidth: 1, paddingHorizontal: 18, paddingTop: 10, paddingBottom: 23, maxHeight: "92%" },
  sheetHandle: { width: 39, height: 4, borderRadius: 4, backgroundColor: "#ffffff33", alignSelf: "center", marginBottom: 15 },
  sheetHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 16 },
  sheetTitle: { fontSize: 19, fontWeight: "900" },
  sheetClose: { width: 33, height: 33, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  sheetOption: { minHeight: 65, borderWidth: 1, borderRadius: 15, padding: 11, flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 8 },
  sheetOptionIcon: { width: 38, height: 38, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  sheetCta: { minHeight: 48, borderRadius: 14, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 7, marginTop: 9 },
  sheetCtaText: { color: "#fff", fontSize: 12, fontWeight: "900" },
  sheetFootnote: { fontSize: 10, lineHeight: 15, textAlign: "center", marginTop: 12 },
  installNameInput: { borderWidth: 1, borderRadius: 13, padding: 11, marginBottom: 7 },
  installInputLabel: { fontSize: 9, fontWeight: "900", letterSpacing: 0.8 },
  installInputValue: { fontSize: 13, fontWeight: "800", marginTop: 5 },
  memoryHero: { borderRadius: 16, padding: 18, alignItems: "center", marginBottom: 18 },
  memoryNumber: { fontSize: 33, fontWeight: "900" },
  memoryUnit: { fontSize: 14, fontWeight: "700" },
  memoryGrid: { flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 17 },
  memoryOption: { width: "31.5%", minHeight: 42, borderWidth: 1, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  memoryOptionText: { fontSize: 10, fontWeight: "800" },
  memoryWarning: { borderWidth: 1, borderRadius: 12, padding: 10, flexDirection: "row", gap: 8, alignItems: "center" },
  versionOption: { minHeight: 57, borderWidth: 1, borderRadius: 14, padding: 10, flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 8 },
  versionDot: { width: 34, height: 34, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  menuOption: { minHeight: 61, borderRadius: 14, padding: 9, flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 3 },
  menuIcon: { width: 36, height: 36, borderRadius: 11, alignItems: "center", justifyContent: "center" },
});
