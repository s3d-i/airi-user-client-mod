import type { EpisodeOutput } from "@airi-client-mod/hub-runtime";

export interface AiriWsBridge {
  publishEpisode(output: EpisodeOutput): Promise<void>;
}

export function createAiriWsBridge(): AiriWsBridge {
  return {
    async publishEpisode(_output) {
      return undefined;
    }
  };
}
