export const MEMORY_PRESETS = [512, 768, 1024, 1536, 2048, 4096] as const;

export type MemoryStatus = {
  label: "Muito baixa" | "Baixa" | "Balanceada" | "Alta";
  detail: string;
  tone: "muted" | "success" | "warning";
};

export function getMemoryStatus(memory: number): MemoryStatus {
  if (memory <= 512) {
    return {
      label: "Muito baixa",
      detail: "Aumente a memória para melhorar a estabilidade.",
      tone: "muted",
    };
  }

  if (memory <= 768) {
    return {
      label: "Baixa",
      detail: "Boa para instalações leves e sessões curtas.",
      tone: "muted",
    };
  }

  if (memory <= 2048) {
    return {
      label: "Balanceada",
      detail: "Faixa recomendada para o perfil Survival.",
      tone: "success",
    };
  }

  return {
    label: "Alta",
    detail: "Deixe pelo menos 2 GB livres para o Android.",
    tone: "warning",
  };
}
