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
    const trackArtist = document.querySelector('.track-artist');
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
        const songChanged = !(trackTitle && trackTitle.textContent === newTitle && 
            trackArtist && trackArtist.textContent === newArtist);
        
        if (songChanged) {
            console.log('updateSongInfo:', title, artist);
            if (trackTitle) trackTitle.textContent = newTitle;
            if (trackArtist) trackArtist.textContent = newArtist;
            if (albumArt && thumbnailUrl) {
                albumArt.src = thumbnailUrl;
            }

            // Update lyrics header too
            const lyricsTitle = document.querySelector('.lyrics-title');
            const lyricsArtist = document.querySelector('.lyrics-artist');
            if (lyricsTitle) lyricsTitle.textContent = newTitle;
            if (lyricsArtist) lyricsArtist.textContent = newArtist;
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

    // Toggle Lyrics View
    if (lyricsToggle) {
        lyricsToggle.addEventListener('click', () => {
            if (mainView && lyricsView) {
                mainView.classList.remove('active');
                lyricsView.classList.add('active');
                setTimeout(scrollToActiveLyric, 300);
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

    // ========== LYRICS FUNCTIONS ==========

    function updateLyricsHighlight() {
        const lineIndex = Math.floor((currentTime / duration) * lyricLines.length);
        let changed = false;
        
        lyricLines.forEach((line, index) => {
            if (index === lineIndex) {
                if (!line.classList.contains('active')) {
                    line.classList.add('active');
                    changed = true;
                }
            } else {
                line.classList.remove('active');
            }
        });

        if (changed && lyricsView.classList.contains('active')) {
            scrollToActiveLyric();
        }
    }

    function scrollToActiveLyric() {
        const activeLine = document.querySelector('.lyric-line.active');
        if (activeLine && lyricsView.classList.contains('active')) {
            const containerHeight = lyricsContent.clientHeight;
            const lineOffset = activeLine.offsetTop;
            const lineHeight = activeLine.clientHeight;
            
            const targetScroll = lineOffset - (containerHeight / 2) + (lineHeight / 2);
            
            lyricsContent.scrollTo({
                top: targetScroll,
                behavior: 'smooth'
            });
        }
    }

    // Allow clicking lyrics to seek
    lyricLines.forEach((line, index) => {
        line.addEventListener('click', () => {
            const percentage = index / lyricLines.length;
            
            if (hasNativePlayer) {
                try {
                    PlayerHelper.seekToPercentage(percentage);
                    if (!isPlaying) {
                        PlayerHelper.play();
                    }
                } catch (e) {
                    console.error('Error seeking from lyrics:', e);
                }
            } else {
                currentTime = Math.floor(percentage * duration);
                updateProgress();
                updateLyricsHighlight();
                if (!isPlaying) {
                    isPlaying = true;
                    updatePlayState();
                }
            }
        });
    });

    // ========== INITIALIZATION ==========

    // Initial display
    updateTimeDisplay();
    updateLyricsHighlight();

    console.log('Player UI initialized');
});
