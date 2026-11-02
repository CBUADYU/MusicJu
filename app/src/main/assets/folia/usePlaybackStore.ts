import { create } from 'zustand';
import type React from 'react';
import { PlayerState, type LyricData, type PlaybackContext, type ReplayGainMode, type SongResult } from '../types';
import { createCoverUrlResolver } from '../components/app/playback/createCoverUrlResolver';

/** The now-playing picture, frozen for as long as a transition is running. */
export interface TransitionDisplay {
    song: SongResult | null;
    lyrics: LyricData | null;
    /** Held with the song: the cover cache is repointed at the arriving track during the blend. */
    coverUrl: string | null;
    /** Held for the same reason, or the progress bar reads the old position against a new length. */
    duration: number;
}

// src/stores/usePlaybackStore.ts
// What is playing, and what the listener is being shown while it plays.
//
// Two layers, and the distinction is load-bearing:
//
//   raw     — what the machine is doing right now.
//   display — what the listener perceives. During an automix blend the outgoing deck is still
//             audible while the raw state has already moved on to the arriving track.
//
// The display layer is exported as *selectors*, never as stored fields. Keeping a second copy is
// how it drifts, and the drift is not subtle: the command palette once read the raw transport, so
// for the length of every blend its Play command paused and its Pause command did nothing.
//
// Nothing here is a per-frame value. Playback position, the lyric clock and the analyser bands are
// MotionValues in ./motionSignals and must stay there. `currentLineIndex` is the fastest field in
// this store and it only moves when the lyric line changes, guarded by a ref at the call site.

type SetState<T> = React.Dispatch<React.SetStateAction<T>>;

type PlaybackStoreState = {
    // ---- raw ----
    currentSong: SongResult | null;
    audioSrc: string | null;
    lyrics: LyricData | null;
    cachedCoverUrl: string | null;
    duration: number;
    playerState: PlayerState;
    currentLineIndex: number;
    playQueue: SongResult[];
    activePlaybackContext: PlaybackContext;
    isFmMode: boolean;
    replayGainMode: ReplayGainMode;
    lyricTimelineOffsetMs: number;
    /**
     * The picture frozen on the outgoing track for the length of a blend.
     *
     * Written synchronously from the session's own block, not from an effect: the session calls
     * `advanceTrack` a few lines later, and an effect-scheduled snapshot would race that advance and
     * capture whichever of the two React had committed. A store write has the ref's timing.
     */
    transitionDisplay: TransitionDisplay | null;

    setCurrentSong: SetState<SongResult | null>;
    setAudioSrc: SetState<string | null>;
    setLyricsState: SetState<LyricData | null>;
    setCachedCoverUrl: SetState<string | null>;
    setDuration: SetState<number>;
    setPlayerState: SetState<PlayerState>;
    setCurrentLineIndex: SetState<number>;
    setPlayQueue: SetState<SongResult[]>;
    setActivePlaybackContext: SetState<PlaybackContext>;
    setIsFmMode: SetState<boolean>;
    setReplayGainMode: SetState<ReplayGainMode>;
    setLyricTimelineOffsetMs: SetState<number>;
    setTransitionDisplay: SetState<TransitionDisplay | null>;
};

const resolve = <T,>(next: React.SetStateAction<T>, previous: T): T => (
    typeof next === 'function' ? (next as (prev: T) => T)(previous) : next
);

const readStoredReplayGainMode = (): ReplayGainMode => {
    if (typeof window === 'undefined') return 'off';
    const saved = localStorage.getItem('local_replaygain_mode');
    return saved === 'track' || saved === 'album' ? saved : 'off';
};

export const usePlaybackStore = create<PlaybackStoreState>((set, get) => ({
    currentSong: null,
    audioSrc: null,
    lyrics: null,
    cachedCoverUrl: null,
    duration: 0,
    playerState: PlayerState.IDLE,
    currentLineIndex: -1,
    playQueue: [],
    activePlaybackContext: 'main',
    isFmMode: false,
    replayGainMode: readStoredReplayGainMode(),
    lyricTimelineOffsetMs: 0,
    transitionDisplay: null,

    setCurrentSong: (next) => set({ currentSong: resolve(next, get().currentSong) }),
    setAudioSrc: (next) => set({ audioSrc: resolve(next, get().audioSrc) }),
    setLyricsState: (next) => set({ lyrics: resolve(next, get().lyrics) }),
    setCachedCoverUrl: (next) => set({ cachedCoverUrl: resolve(next, get().cachedCoverUrl) }),
    setDuration: (next) => set({ duration: resolve(next, get().duration) }),
    setPlayerState: (next) => set({ playerState: resolve(next, get().playerState) }),
    setCurrentLineIndex: (next) => set({ currentLineIndex: resolve(next, get().currentLineIndex) }),
    setPlayQueue: (next) => set({ playQueue: resolve(next, get().playQueue) }),
    setActivePlaybackContext: (next) => set({ activePlaybackContext: resolve(next, get().activePlaybackContext) }),
    setIsFmMode: (next) => set({ isFmMode: resolve(next, get().isFmMode) }),
    setReplayGainMode: (next) => set({ replayGainMode: resolve(next, get().replayGainMode) }),
    setLyricTimelineOffsetMs: (next) => set({ lyricTimelineOffsetMs: resolve(next, get().lyricTimelineOffsetMs) }),
    setTransitionDisplay: (next) => set({ transitionDisplay: resolve(next, get().transitionDisplay) }),
}));

// ---- module-level setters ----
//
// The controller hooks used to be handed these as parameters (59 of them across nine hooks). Their
// identity is stable, so importing one costs nothing and it never has to enter a dependency array.
// Writes are synchronous, which the automix freeze depends on.

export const setCurrentSong: SetState<SongResult | null> = (next) => usePlaybackStore.getState().setCurrentSong(next);
export const setAudioSrc: SetState<string | null> = (next) => usePlaybackStore.getState().setAudioSrc(next);
export const setLyricsState: SetState<LyricData | null> = (next) => usePlaybackStore.getState().setLyricsState(next);
export const setCachedCoverUrl: SetState<string | null> = (next) => usePlaybackStore.getState().setCachedCoverUrl(next);
export const setDuration: SetState<number> = (next) => usePlaybackStore.getState().setDuration(next);
export const setPlayerState: SetState<PlayerState> = (next) => usePlaybackStore.getState().setPlayerState(next);
export const setCurrentLineIndex: SetState<number> = (next) => usePlaybackStore.getState().setCurrentLineIndex(next);
export const setPlayQueue: SetState<SongResult[]> = (next) => usePlaybackStore.getState().setPlayQueue(next);
export const setActivePlaybackContext: SetState<PlaybackContext> = (next) => usePlaybackStore.getState().setActivePlaybackContext(next);
export const setIsFmMode: SetState<boolean> = (next) => usePlaybackStore.getState().setIsFmMode(next);
export const setReplayGainMode: SetState<ReplayGainMode> = (next) => usePlaybackStore.getState().setReplayGainMode(next);
export const setLyricTimelineOffsetMs: SetState<number> = (next) => usePlaybackStore.getState().setLyricTimelineOffsetMs(next);
export const setTransitionDisplay: SetState<TransitionDisplay | null> = (next) => usePlaybackStore.getState().setTransitionDisplay(next);

// ---- derived: the cover, and the display layer ----

export const selectCoverUrl = (state: PlaybackStoreState) => (
    createCoverUrlResolver(state.cachedCoverUrl, state.currentSong)()
);

/** True while the outgoing deck still owns the picture. */
export const selectIsShowingTail = (state: PlaybackStoreState) => state.transitionDisplay !== null;

export const selectDisplaySong = (state: PlaybackStoreState) => (
    state.transitionDisplay?.song ?? state.currentSong
);

export const selectDisplayLyrics = (state: PlaybackStoreState) => (
    state.transitionDisplay ? state.transitionDisplay.lyrics : state.lyrics
);

export const selectDisplayCoverUrl = (state: PlaybackStoreState) => (
    state.transitionDisplay ? state.transitionDisplay.coverUrl : selectCoverUrl(state)
);

export const selectDisplayDuration = (state: PlaybackStoreState) => (
    state.transitionDisplay ? state.transitionDisplay.duration : state.duration
);

/**
 * The transport the picture belongs to, which is not idle just because the next track is.
 *
 * `playSong` resets the raw state to IDLE for the track it is loading. That is correct for the
 * arriving track and wrong for what is still being heard, so anything user-facing — the controls,
 * the remote, the taskbar, the command palette — must read this rather than `playerState`.
 */
export const selectDisplayPlayerState = (state: PlaybackStoreState) => (
    selectIsShowingTail(state) && state.playerState === PlayerState.IDLE
        ? PlayerState.PLAYING
        : state.playerState
);

// =====================================================================
//  MusicApp External Control API
//  注入到 window.__foliaExternal，让 Android WebView 可以外部控制 Folia
// =====================================================================

type FoliaExternalApi = {
    setSong: (json: any) => void;
    setLyrics: (lrcText: string) => void;
    setPositionMs: (ms: number) => void;
    setPlaying: (playing: boolean) => void;
    clear: () => void;
    getState: () => any;
};

// LRC → Folia LyricData 格式
function parseLrcToFolia(lrcText: string): any {
    const lines: any[] = [];
    const tagRe = /\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]/g;
    const rawLines = lrcText.split(/\r?\n/);
    for (const raw of rawLines) {
        tagRe.lastIndex = 0;
        const m = tagRe.exec(raw);
        if (!m) continue;
        const min = parseInt(m[1], 10);
        const sec = parseInt(m[2], 10);
        const msStr = m[3] || '0';
        const startTime = min * 60000 + sec * 1000 + parseInt((msStr + '00').slice(0, 3), 10);
        const content = raw.slice(m.index + m[0].length).trim();
        if (!content) continue;
        lines.push({ startTime, endTime: startTime + 3000, fullText: content, translation: null, words: [] });
    }
    for (let i = 0; i < lines.length - 1; i++) {
        lines[i].endTime = Math.min(lines[i].endTime, lines[i + 1].startTime);
    }
    return { lines };
}

function buildFoliaSongFromExternal(json: any): SongResult {
    return {
        id: { platform: 'netease', id: 'ma-' + String(json.id ?? json.name ?? Date.now()) },
        name: String(json.name ?? json.title ?? 'Unknown'),
        artists: [{ id: 'ma-artist', name: String(json.artist ?? 'MusicApp') }],
        album: {
            id: 'ma-album',
            name: String(json.album ?? ''),
            picUrl: json.coverUrl ?? json.cover ?? null,
            artist: json.artist ?? '',
            description: null,
            type: 'album'
        },
        durationMs: Number(json.durationMs ?? json.duration ?? 180000),
        sourceType: 'netease',
        sourceRef: null,
        playbackSourceRevision: 'musicapp-' + Date.now()
    } as SongResult;
}

// 暴露到 globalThis
if (typeof globalThis !== 'undefined') {
    const api: FoliaExternalApi = {
        setSong(json: any) {
            try {
                const song = typeof json === 'string' ? JSON.parse(json) : json;
                const foliaSong = buildFoliaSongFromExternal(song);
                setCurrentSong(foliaSong);
                setDuration(foliaSong.durationMs);
                if (song.coverUrl) setCachedCoverUrl(song.coverUrl);
            } catch (e) {
                console.error('[FoliaExt] setSong error', e);
            }
        },

        setLyrics(lrcText: string) {
            try {
                if (!lrcText) {
                    setLyricsState(null);
                    return;
                }
                const lyricData = parseLrcToFolia(lrcText);
                setLyricsState(lyricData as any);
            } catch (e) {
                console.error('[FoliaExt] setLyrics error', e);
            }
        },

        setPositionMs(ms: number) {
            try {
                setCurrentTimeSec(ms / 1000);
            } catch (e) {}
        },

        setPlaying(playing: boolean) {
            try {
                setPlayerState(playing ? PlayerState.PLAYING : PlayerState.PAUSED);
            } catch (e) {}
        },

        clear() {
            try {
                setCurrentSong(null);
                setLyricsState(null);
                setPlayerState(PlayerState.IDLE);
                setCurrentTimeSec(0);
            } catch (e) {}
        },

        getState() {
            try {
                const s = usePlaybackStore.getState();
                return {
                    currentSong: s.currentSong,
                    playerState: s.playerState,
                    duration: s.duration,
                    hasLyrics: !!s.lyrics
                };
            } catch { return null; }
        }
    };

    (globalThis as any).__foliaExternal = api;
    (globalThis as any).foliaExternal = api;
}

// 定义缺失的 setCurrentTimeSec（用 motionSignals 全局引用，避免循环导入）
// 注意：这个函数需要在 api 对象定义前存在，所以我们把它移到上面更好
// 但由于 JavaScript 函数声明提升，我们在 api 闭包里调用它也会因为闭包而捕获到
// 为了保险，让我们也直接在下面重新绑定
const _api = (globalThis as any).__foliaExternal;
if (_api) {
    const origSetPos = _api.setPositionMs;
    _api.setPositionMs = function(ms: number) {
        try {
            const motion = (globalThis as any).__FOLIA_MOTION__;
            if (motion) {
                const sec = ms / 1000;
                motion.currentTime.set(sec);
                if (motion.lyricCurrentTime) motion.lyricCurrentTime.set(sec);
            }
        } catch (e) {}
    };
    const origClear = _api.clear;
    _api.clear = function() {
        try {
            origClear();
            const motion = (globalThis as any).__FOLIA_MOTION__;
            if (motion) {
                motion.currentTime.set(0);
                if (motion.lyricCurrentTime) motion.lyricCurrentTime.set(0);
            }
        } catch (e) {}
    };
}
