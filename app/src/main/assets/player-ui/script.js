document.addEventListener('DOMContentLoaded', () => {
    // Elements - static elements that don't get replaced
    const mainView = document.getElementById('main-view');
    const lyricsView = document.getElementById('lyrics-view');
    const lyricsToggle = document.querySelector('.lyrics-toggle');
    const backBtn = document.querySelector('.back-btn');
    const playPauseBtn = document.querySelector('.play-pause-btn');
    const progressBar = document.querySelector('.progress-bar');
    const currentTimeEl = document.querySelector('.current-time');
    const totalTimeEl = document.querySelector('.total-time');
    const heartBtn = document.querySelector('.heart-btn');
    const lyricLines = document.querySelectorAll('.lyric-line');
    const lyricsContent = document.querySelector('.lyrics-content');
    const trackTitle = document.querySelector('.track-title');
    const trackArtistContainer = document.querySelector('.track-artist-container');
    const albumArt = document.querySelector('.album-art');
    const prevBtn = document.querySelector('.prev-btn');
    const nextBtn = document.querySelector('.next-btn');
    const shuffleBtn = document.querySelector('.shuffle-btn');
    const repeatBtn = document.querySelector('.repeat-btn');
    const shareBtn = document.querySelector('.share-btn');
    const volumeBtn = document.querySelector('.volume-btn');
    const sleepTimerBtn = document.querySelector('.sleep-timer-btn');
    const queueBtn = document.querySelector('.queue-btn');
    
    // Helper function to get play button elements (re-queries because Lucide replaces icons)
    function getPlayIcon() {
        return playPauseBtn ? playPauseBtn.querySelector('svg, i') : null;
    }
    function getPlayText() {
        return playPauseBtn ? playPauseBtn.querySelector('span') : null;
    }
    
    console.log('DOM Elements loaded:', {
        playPauseBtn: !!playPauseBtn,
        playIcon: !!getPlayIcon(),
        playText: !!getPlayText(),
        trackTitle: !!trackTitle,
        heartBtn: !!heartBtn
    });

    // State
    let isPlaying = false;
    let isLiked = false;
    let currentTime = 0;
    let duration = 179; // Default, will be updated
    let isDraggingProgress = false;
    
    // Lyrics state (declared early so global functions can access)
    let lyricsData = null;        // Current lyrics data from provider
    let syncedLyrics = [];        // Array of {time: ms, text: string}
    let currentLyricIndex = -1;   // Currently highlighted line
    let isLoadingLyrics = false;

    // Check if PlayerHelper is available
    const hasNativePlayer = typeof PlayerHelper !== 'undefined';
    console.log('PlayerHelper available:', hasNativePlayer);

    // ========== GLOBAL UPDATE FUNCTIONS (called from native) ==========
    // Define these first so they're available for initializeFromNativePlayer

    window.updateSongInfo = function(title, artist, thumbnailUrl, likedStatus) {
        // Only log when actually updating
        const newTitle = title || 'Unknown Track';
        const newArtist = artist || 'Unknown Artist';
        
        // Check if song changed
        const currentTitle = trackTitle ? trackTitle.textContent : '';
        const songChanged = currentTitle !== newTitle;
        
        if (songChanged) {
            console.log('updateSongInfo:', title, artist);
            if (trackTitle) trackTitle.textContent = newTitle;
            
            // Render clickable artist names
            renderArtistNames(newArtist);
            
            if (albumArt && thumbnailUrl) {
                albumArt.src = thumbnailUrl;
            }

            // Update lyrics header too
            const lyricsTitle = document.querySelector('.lyrics-title');
            const lyricsArtist = document.querySelector('.lyrics-artist');
            if (lyricsTitle) lyricsTitle.textContent = newTitle;
            if (lyricsArtist) lyricsArtist.textContent = newArtist;
            
            // Reset lyrics for new song - CRITICAL for sync reliability
            lyricsData = null;
            syncedLyrics = [];
            currentLyricIndex = -1;
            isLoadingLyrics = false;
            
            // Clear any existing lyrics display and remove all styling
            if (lyricsContent) {
                const oldLines = lyricsContent.querySelectorAll('.lyric-line');
                oldLines.forEach(line => {
                    line.classList.remove('active', 'past');
                });
                lyricsContent.innerHTML = '';
            }
            
            // If lyrics view is open, show loading and fetch new lyrics IMMEDIATELY
            if (lyricsView && lyricsView.classList.contains('active') && hasNativePlayer) {
                showLyricsLoading();
                setTimeout(() => fetchLyrics(), 10);
            } else if (lyricsContent) {
                // Pre-show loading state so it's ready when user opens lyrics
                showLyricsLoading();
            }
            
            // Prefetch lyrics in background
            if (hasNativePlayer) {
                try {
                    PlayerHelper.prefetchLyrics();
                } catch (e) {
                    console.error('Error prefetching lyrics:', e);
                }
            }
        }
        
        // Always update heart/like state (could have changed even for same song)
        if (heartBtn) {
            if (typeof likedStatus === 'boolean') {
                // Use provided status
                updateHeartState(likedStatus);
            } else if (hasNativePlayer) {
                // Fetch from native
                try {
                    const songLiked = PlayerHelper.isCurrentSongLiked();
                    updateHeartState(songLiked);
                } catch (e) {
                    console.error('Error checking like state:', e);
                }
            }
        }
    };
    
    // Helper function to update heart button UI
    function updateHeartState(liked) {
        if (!heartBtn) return;
        isLiked = liked;
        const icon = heartBtn.querySelector('svg, i');
        
        if (icon) {
            icon.remove();
        }
        const newIcon = document.createElement('i');
        newIcon.setAttribute('data-lucide', 'heart');
        heartBtn.insertBefore(newIcon, heartBtn.firstChild);
        
        if (liked) {
            heartBtn.style.color = '#ff3b30';
            heartBtn.style.background = 'rgba(255, 59, 48, 0.15)';
            heartBtn.classList.add('liked');
        } else {
            heartBtn.style.color = 'white';
            heartBtn.style.background = 'rgba(255, 255, 255, 0.08)';
            heartBtn.classList.remove('liked');
        }
        lucide.createIcons();
    }
    
    // Global function to refresh like state (can be called from native)
    window.updateLikeState = function(liked) {
        updateHeartState(liked);
    };
    
    // Also refresh like state when called without args (fetches from native)
    window.refreshLikeState = function() {
        if (hasNativePlayer && heartBtn) {
            try {
                const songLiked = PlayerHelper.isCurrentSongLiked();
                updateHeartState(songLiked);
            } catch (e) {
                console.error('Error refreshing like state:', e);
            }
        }
    };

    window.updatePlaybackState = function(playing) {
        isPlaying = playing;
        updatePlayState();
    };

    window.updateProgress = function(currentMs, durationMs, percentage) {
        if (!isDraggingProgress) {
            currentTime = Math.floor(currentMs / 1000);
            duration = Math.floor(durationMs / 1000);
            if (progressBar) progressBar.value = percentage;
            updateTimeDisplay();
            updateLyricsHighlight();
        }
    };

    // ========== NATIVE PLAYER INTEGRATION ==========

    function initializeFromNativePlayer() {
        try {
            // Get initial state
            const state = JSON.parse(PlayerHelper.getPlayerState());
            console.log('Initial player state:', state);

            // Update UI with current state
            if (state.currentSong && state.currentSong.title) {
                window.updateSongInfo(
                    state.currentSong.title,
                    state.currentSong.artist,
                    state.currentSong.thumbnailUrl
                );
            }

            isPlaying = state.isPlaying || false;
            duration = state.duration > 0 ? Math.floor(state.duration / 1000) : 179;
            currentTime = state.currentPosition > 0 ? Math.floor(state.currentPosition / 1000) : 0;

            updatePlayState();
            updateProgress();

            // Update shuffle state
            if (shuffleBtn) {
                const shuffleEnabled = PlayerHelper.isShuffleEnabled();
                shuffleBtn.style.color = shuffleEnabled ? '#1DB954' : 'white';
                shuffleBtn.style.opacity = shuffleEnabled ? '1' : '0.6';
            }

            // Update repeat state
            if (repeatBtn) {
                const repeatMode = PlayerHelper.getRepeatMode();
                const isActive = repeatMode !== 'OFF';
                repeatBtn.style.color = isActive ? '#1DB954' : 'white';
                repeatBtn.style.opacity = isActive ? '1' : '0.6';
            }
            
            // Update like/heart state
            if (heartBtn) {
                const songLiked = PlayerHelper.isCurrentSongLiked();
                updateHeartState(songLiked);
            }

        } catch (e) {
            console.error('Error initializing from native player:', e);
        }
    }

    // Initialize UI with current player state
    if (hasNativePlayer) {
        initializeFromNativePlayer();
    }

    // ========== CALLBACK FUNCTIONS (called from native on events) ==========

    window.onSongChanged = function(songJson) {
        console.log('Song changed:', songJson);
        try {
            const song = JSON.parse(songJson);
            if (song && song.title) {
                window.updateSongInfo(song.title, song.artist, song.thumbnailUrl);
            }
        } catch (e) {
            console.error('Error parsing song JSON:', e);
        }
    };

    window.onPlaybackStateChanged = function(playing) {
        console.log('Playback state changed:', playing);
        isPlaying = playing;
        updatePlayState();
    };

    window.onShuffleChanged = function(enabled) {
        console.log('Shuffle changed:', enabled);
        if (shuffleBtn) {
            shuffleBtn.style.color = enabled ? '#1DB954' : 'white';
            shuffleBtn.style.opacity = enabled ? '1' : '0.6';
        }
    };

    window.onRepeatModeChanged = function(mode) {
        console.log('Repeat mode changed:', mode);
        if (repeatBtn) {
            const isActive = mode !== 'OFF';
            repeatBtn.style.color = isActive ? '#1DB954' : 'white';
            repeatBtn.style.opacity = isActive ? '1' : '0.6';
        }
    };

    window.onError = function(message) {
        console.error('Playback error:', message);
    };

    window.onLoadingStateChanged = function(loading) {
        console.log('Loading state:', loading);
        if (playPauseBtn) {
            playPauseBtn.style.opacity = loading ? '0.5' : '1';
            playPauseBtn.disabled = loading;
        }
    };

    window.onQueueChanged = function(queueJson) {
        console.log('Queue changed');
        // TODO: Update queue UI if needed
    };

    // ========== UI EVENT HANDLERS ==========

    // Toggle Lyrics View - Open instantly with loading state
    if (lyricsToggle) {
        lyricsToggle.addEventListener('click', () => {
            if (mainView && lyricsView) {
                // 1. Show loading state FIRST (if needed)
                if (!lyricsData && hasNativePlayer && lyricsContent) {
                    showLyricsLoading();
                }
                
                // 2. Switch views IMMEDIATELY - don't wait for anything
                mainView.classList.remove('active');
                lyricsView.classList.add('active');
                
                // 3. After view is open, handle lyrics
                if (hasNativePlayer) {
                    if (!lyricsData) {
                        // No lyrics loaded - fetch asynchronously (don't block UI)
                        setTimeout(() => fetchLyrics(), 10);
                    } else {
                        // Already have lyrics, but verify they're for current song
                        // and scroll to current line
                        if (syncedLyrics && syncedLyrics.length > 0) {
                            setTimeout(scrollToActiveLyric, 100);
                        }
                    }
                }
            }
        });
    }

    if (backBtn) {
        backBtn.addEventListener('click', () => {
            if (mainView && lyricsView) {
                lyricsView.classList.remove('active');
                mainView.classList.add('active');
            }
        });
    }

    // Play/Pause Toggle
    if (playPauseBtn) {
        playPauseBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.togglePlayPause();
                    // State will be updated via callback
                } catch (e) {
                    console.error('Error toggling playback:', e);
                }
            } else {
                // Fallback to local state
                isPlaying = !isPlaying;
                updatePlayState();
            }
        });
    }

    // Previous/Next buttons
    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.previous();
                } catch (e) {
                    console.error('Error going to previous:', e);
                }
            }
        });
    }

    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.next();
                } catch (e) {
                    console.error('Error going to next:', e);
                }
            }
        });
    }

    // Shuffle button
    if (shuffleBtn) {
        shuffleBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.toggleShuffle();
                } catch (e) {
                    console.error('Error toggling shuffle:', e);
                }
            }
        });
    }

    // Repeat button
    if (repeatBtn) {
        repeatBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.toggleRepeat();
                } catch (e) {
                    console.error('Error toggling repeat:', e);
                }
            }
        });
    }

    // Heart Toggle
    if (heartBtn) {
        heartBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    // Toggle via native bridge (handles both local DB and YouTube sync)
                    PlayerHelper.toggleLike();
                    
                    // Update UI immediately (optimistic update)
                    isLiked = !isLiked;
                    updateHeartState(isLiked);
                    console.log(isLiked ? 'Like song called' : 'Unlike song called');
                } catch (e) {
                    console.error('Error toggling like:', e);
                }
            } else {
                // Fallback for non-native (web demo mode)
                isLiked = !isLiked;
                updateHeartState(isLiked);
            }
        });
    }
    // Share button
    if (shareBtn) {
        shareBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.shareSong();
                    // Visual feedback
                    shareBtn.style.color = '#1DB954';
                    shareBtn.style.background = 'rgba(29, 185, 84, 0.15)';
                    setTimeout(() => {
                        shareBtn.style.color = 'white';
                        shareBtn.style.background = 'rgba(255, 255, 255, 0.08)';
                    }, 500);
                } catch (e) {
                    console.error('Error sharing song:', e);
                }
            }
        });
    }

    // Volume button - show volume control
    if (volumeBtn) {
        volumeBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    const currentVolume = PlayerHelper.getVolumePercentage();
                    const newVolume = prompt('Set volume (0-100):', currentVolume);
                    if (newVolume !== null && !isNaN(newVolume)) {
                        const volume = Math.max(0, Math.min(100, parseInt(newVolume)));
                        PlayerHelper.setVolumePercentage(volume);
                    }
                } catch (e) {
                    console.error('Error setting volume:', e);
                }
            }
        });
    }

    // Sleep timer button
    if (sleepTimerBtn) {
        sleepTimerBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    const minutes = prompt('Set sleep timer (minutes, 0 to cancel):');
                    if (minutes !== null && !isNaN(minutes)) {
                        const mins = parseInt(minutes);
                        if (mins <= 0) {
                            PlayerHelper.cancelSleepTimer();
                            alert('Sleep timer cancelled');
                        } else {
                            PlayerHelper.setSleepTimerMinutes(mins);
                            alert(`Sleep timer set for ${mins} minutes`);
                        }
                    }
                } catch (e) {
                    console.error('Error setting sleep timer:', e);
                }
            }
        });
    }

    // Queue button - show native queue bottom sheet
    if (queueBtn) {
        queueBtn.addEventListener('click', () => {
            if (hasNativePlayer) {
                try {
                    PlayerHelper.showQueueBottomSheet();
                } catch (e) {
                    console.error('Error showing queue:', e);
                    // Fallback to simple alert if native queue fails
                    try {
                        const queueJson = PlayerHelper.getQueue();
                        const queue = JSON.parse(queueJson);
                        const currentIndex = PlayerHelper.getCurrentQueueIndex();
                        
                        let message = `Queue (${queue.length} songs):\n\n`;
                        queue.forEach((song, index) => {
                            const marker = index === currentIndex ? '▶ ' : '  ';
                            message += `${marker}${index + 1}. ${song.title} - ${song.artist}\n`;
                        });
                        
                        alert(message);
                    } catch (fallbackError) {
                        console.error('Fallback queue display also failed:', fallbackError);
                    }
                }
            }
        });
    }

    // Progress Bar Interaction
    if (progressBar) {
        progressBar.addEventListener('mousedown', () => {
            isDraggingProgress = true;
        });

        progressBar.addEventListener('touchstart', () => {
            isDraggingProgress = true;
        });

        progressBar.addEventListener('input', (e) => {
            if (isDraggingProgress) {
                const percentage = e.target.value / 100;
                currentTime = Math.floor(percentage * duration);
                updateTimeDisplay();
                updateLyricsHighlight();
            }
        });

        progressBar.addEventListener('mouseup', (e) => {
            isDraggingProgress = false;
            seekToPercentage(e.target.value / 100);
        });

        progressBar.addEventListener('touchend', (e) => {
            isDraggingProgress = false;
            const percentage = progressBar.value / 100;
            seekToPercentage(percentage);
        });
    }

    function seekToPercentage(percentage) {
        if (hasNativePlayer) {
            try {
                PlayerHelper.seekToPercentage(percentage);
            } catch (e) {
                console.error('Error seeking:', e);
            }
        } else {
            currentTime = Math.floor(percentage * duration);
            updateProgress();
        }
    }

    // ========== UI UPDATE FUNCTIONS ==========

    function updatePlayState() {
        const playIcon = getPlayIcon();
        const playText = getPlayText();
        
        if (!playPauseBtn) {
            console.error('Play button not found');
            return;
        }
        
        if (isPlaying) {
            // Update the icon - Lucide may have replaced the <i> with <svg>
            if (playIcon) {
                if (playIcon.tagName === 'svg') {
                    // Lucide already processed - replace with new icon
                    playIcon.remove();
                    const newIcon = document.createElement('i');
                    newIcon.setAttribute('data-lucide', 'pause');
                    playPauseBtn.insertBefore(newIcon, playPauseBtn.firstChild);
                } else {
                    playIcon.setAttribute('data-lucide', 'pause');
                }
            }
            if (playText) playText.textContent = 'Pause';
            if (!hasNativePlayer) {
                startTimer();
            }
        } else {
            if (playIcon) {
                if (playIcon.tagName === 'svg') {
                    playIcon.remove();
                    const newIcon = document.createElement('i');
                    newIcon.setAttribute('data-lucide', 'play');
                    playPauseBtn.insertBefore(newIcon, playPauseBtn.firstChild);
                } else {
                    playIcon.setAttribute('data-lucide', 'play');
                }
            }
            if (playText) playText.textContent = 'Play';
            if (!hasNativePlayer) {
                stopTimer();
            }
        }
        lucide.createIcons();
    }

    // Timer Logic (only for fallback mode)
    let timer;
    function startTimer() {
        stopTimer();
        timer = setInterval(() => {
            if (currentTime < duration) {
                currentTime++;
                updateProgress();
                updateLyricsHighlight();
            } else {
                isPlaying = false;
                updatePlayState();
            }
        }, 1000);
    }

    function stopTimer() {
        clearInterval(timer);
    }

    function updateProgress() {
        const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;
        progressBar.value = progressPercent;
        updateTimeDisplay();
    }

    function updateTimeDisplay() {
        currentTimeEl.textContent = formatTime(currentTime);
        totalTimeEl.textContent = formatTime(duration);
    }

    function formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }

    // ========== ARTIST NAVIGATION ==========
    
    /**
     * Render artist names as clickable elements (Spotify-style)
     * Fetches artist browse IDs from native and creates clickable links
     */
    function renderArtistNames(artistString) {
        if (!trackArtistContainer) return;
        
        trackArtistContainer.innerHTML = '';
        
        if (!hasNativePlayer) {
            // Fallback: show plain text
            const p = document.createElement('p');
            p.className = 'track-artist';
            p.textContent = artistString;
            trackArtistContainer.appendChild(p);
            return;
        }
        
        try {
            // Get artist info from native
            const artistInfoJson = PlayerHelper.getArtistInfo();
            const artistInfo = JSON.parse(artistInfoJson);
            
            if (artistInfo && artistInfo.length > 0) {
                // Multiple artists - create clickable spans with commas
                const container = document.createElement('p');
                container.className = 'track-artist';
                
                artistInfo.forEach((artist, index) => {
                    // Create clickable artist name
                    const artistSpan = document.createElement('span');
                    artistSpan.className = 'artist-name clickable';
                    artistSpan.textContent = artist.name;
                    artistSpan.addEventListener('click', () => {
                        openArtistPage(artist.browseId, artist.name);
                    });
                    container.appendChild(artistSpan);
                    
                    // Add comma separator (not clickable)
                    if (index < artistInfo.length - 1) {
                        const comma = document.createElement('span');
                        comma.textContent = ', ';
                        container.appendChild(comma);
                    }
                });
                
                trackArtistContainer.appendChild(container);
            } else {
                // No artist info available, show plain text
                const p = document.createElement('p');
                p.className = 'track-artist';
                p.textContent = artistString;
                trackArtistContainer.appendChild(p);
            }
        } catch (e) {
            console.error('Error rendering artist names:', e);
            // Fallback
            const p = document.createElement('p');
            p.className = 'track-artist';
            p.textContent = artistString;
            trackArtistContainer.appendChild(p);
        }
    }
    
    /**
     * Open artist page in the app
     */
    function openArtistPage(browseId, artistName) {
        if (!hasNativePlayer) return;
        
        try {
            console.log('Opening artist page:', artistName, browseId);
            PlayerHelper.openArtistPage(browseId, artistName);
        } catch (e) {
            console.error('Error opening artist page:', e);
        }
    }

    // ========== LYRICS SYSTEM ==========
    
    // Lyrics state is declared at the top of the script
    
    /**
     * Fetch lyrics for current song
     */
    function fetchLyrics() {
        if (!hasNativePlayer || isLoadingLyrics) return;
        
        isLoadingLyrics = true;
        showLyricsLoading();
        
        try {
            // Synchronous fetch (runs in background on native side)
            const lyricsJson = PlayerHelper.getLyrics();
            console.log('Lyrics response:', lyricsJson);
            
            if (lyricsJson && lyricsJson !== '{}') {
                const data = JSON.parse(lyricsJson);
                handleLyricsLoaded(data);
            } else {
                showNoLyrics();
            }
        } catch (e) {
            console.error('Error fetching lyrics:', e);
            showNoLyrics();
        } finally {
            isLoadingLyrics = false;
        }
    }
    
    /**
     * Handle loaded lyrics data
     */
    function handleLyricsLoaded(data) {
        lyricsData = data;
        
        if (data.isSynced && data.syncedLines && data.syncedLines.length > 0) {
            // Time-synced lyrics
            syncedLyrics = data.syncedLines;
            currentLyricIndex = -1;
            renderSyncedLyrics();
            console.log('Loaded ' + syncedLyrics.length + ' synced lyrics lines from ' + data.provider);
        } else if (data.plainLyrics) {
            // Plain text lyrics - clear sync state completely
            syncedLyrics = [];
            currentLyricIndex = -1;
            renderPlainLyrics(data.plainLyrics);
            console.log('Loaded plain lyrics from ' + data.provider);
        } else {
            // No lyrics available
            syncedLyrics = [];
            currentLyricIndex = -1;
            showNoLyrics();
        }
    }
    
    /**
     * Render time-synced lyrics (Spotify-style)
     */
    function renderSyncedLyrics() {
        if (!lyricsContent) return;
        
        lyricsContent.innerHTML = '';
        currentLyricIndex = -1;
        
        syncedLyrics.forEach((line, index) => {
            const p = document.createElement('p');
            p.className = 'lyric-line';
            p.textContent = line.text || '';
            p.dataset.time = line.time;
            p.dataset.index = index;
            
            // Click to seek
            p.addEventListener('click', () => {
                if (hasNativePlayer && line.time !== undefined) {
                    try {
                        const percentage = line.time / (duration * 1000);
                        PlayerHelper.seekToPercentage(percentage);
                        if (!isPlaying) {
                            PlayerHelper.play();
                        }
                    } catch (e) {
                        console.error('Error seeking from lyrics:', e);
                    }
                }
            });
            
            lyricsContent.appendChild(p);
        });
        
        // Update highlight immediately and scroll to current position
        updateLyricsHighlight();
        
        // Scroll to active lyric after a brief delay to let DOM settle
        setTimeout(() => {
            if (lyricsView && lyricsView.classList.contains('active')) {
                scrollToActiveLyric();
            }
        }, 100);
    }
    
    /**
     * Render plain text lyrics (no timestamps)
     */
    function renderPlainLyrics(text) {
        if (!lyricsContent) return;
        
        lyricsContent.innerHTML = '';
        syncedLyrics = [];
        currentLyricIndex = -1;
        
        const lines = text.split('\n');
        lines.forEach((line, index) => {
            if (line.trim()) {
                const p = document.createElement('p');
                p.className = 'lyric-line';
                p.textContent = line;
                lyricsContent.appendChild(p);
            }
        });
    }
    
    /**
     * Show loading state with spinner
     */
    function showLyricsLoading() {
        if (!lyricsContent) return;
        lyricsContent.innerHTML = `
            <div class="lyrics-loading-container">
                <div class="lyrics-loading-spinner"></div>
                <span class="lyrics-loading-text">Finding lyrics...</span>
            </div>
        `;
    }
    
    /**
     * Show no lyrics available
     */
    function showNoLyrics() {
        if (!lyricsContent) return;
        lyricsContent.innerHTML = `
            <div class="no-lyrics-container">
                <div class="no-lyrics-icon">🎵</div>
                <span class="no-lyrics-text">No lyrics available</span>
                <span class="no-lyrics-subtext">Lyrics not found for this track</span>
            </div>
        `;
        syncedLyrics = [];
        lyricsData = null;
    }
    
    /**
     * Update lyrics highlight based on current playback position
     * Marks current line as active and past lines as past (Spotify-style)
     */
    function updateLyricsHighlight() {
        if (!syncedLyrics || syncedLyrics.length === 0 || !lyricsContent) return;
        
        // Find the current line based on playback time (currentTime is in seconds)
        const currentTimeMs = Math.max(0, currentTime * 1000);
        let newIndex = -1;
        
        // Find the active lyric line - last line with time <= currentTime
        for (let i = 0; i < syncedLyrics.length; i++) {
            const lineTime = syncedLyrics[i].time;
            if (lineTime !== undefined && lineTime <= currentTimeMs) {
                newIndex = i;
            } else {
                break;
            }
        }
        
        // Only update if changed
        if (newIndex !== currentLyricIndex) {
            // Update all lines - mark past, active, and future
            const allLines = lyricsContent.querySelectorAll('.lyric-line');
            if (allLines.length > 0) {
                allLines.forEach((line, index) => {
                    line.classList.remove('active', 'past');
                    if (index < newIndex) {
                        line.classList.add('past');
                    } else if (index === newIndex) {
                        line.classList.add('active');
                    }
                });
            }
            
            currentLyricIndex = newIndex;
            
            // Auto-scroll if lyrics view is active
            if (currentLyricIndex >= 0 && lyricsView && lyricsView.classList.contains('active')) {
                scrollToActiveLyric();
            }
        }
    }

    /**
     * Scroll to center the active lyric line (Spotify-style)
     */
    function scrollToActiveLyric() {
        const activeLine = lyricsContent.querySelector('.lyric-line.active');
        if (activeLine && lyricsContent) {
            const containerHeight = lyricsContent.clientHeight;
            const lineOffset = activeLine.offsetTop;
            const lineHeight = activeLine.clientHeight;
            
            // Position active line at ~35% from top for better visibility
            const targetScroll = lineOffset - (containerHeight * 0.35) + (lineHeight / 2);
            
            lyricsContent.scrollTo({
                top: Math.max(0, targetScroll),
                behavior: 'smooth'
            });
        }
    }
    
    // Global function to trigger lyrics fetch (can be called from native)
    window.fetchLyrics = fetchLyrics;
    
    // Global function to receive lyrics data (for async callback)
    window.onLyricsLoaded = function(data) {
        if (typeof data === 'string') {
            try {
                data = JSON.parse(data);
            } catch (e) {
                console.error('Error parsing lyrics data:', e);
                return;
            }
        }
        handleLyricsLoaded(data);
    };

    // ========== INITIALIZATION ==========

    // Initial display
    updateTimeDisplay();

    console.log('Player UI initialized');
});
