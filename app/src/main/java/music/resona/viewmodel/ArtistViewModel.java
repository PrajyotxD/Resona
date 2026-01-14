package music.resona.viewmodel;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import music.resona.online.bridge.InnertubeBridge;
import music.resona.online.bridge.callbacks.ArtistCallback;
import music.resona.online.bridge.exceptions.BridgeException;
import music.resona.online.bridge.models.ArtistPageResult;
import music.resona.online.bridge.models.YTItemResult;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for managing artist page data and state.
 * Loads artist information including songs, albums, singles, and videos
 * using the InnertubeBridge API.
 */
public class ArtistViewModel extends ViewModel {
    
    private static final String TAG = "ArtistViewModel";
    
    // LiveData for artist information
    private final MutableLiveData<YTItemResult> artist = new MutableLiveData<>();
    private final MutableLiveData<String> description = new MutableLiveData<>();
    private final MutableLiveData<List<String>> thumbnails = new MutableLiveData<>(new ArrayList<>());
    
    // LiveData for content sections
    private final MutableLiveData<List<YTItemResult>> songs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<YTItemResult>> albums = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<YTItemResult>> singles = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<YTItemResult>> videos = new MutableLiveData<>(new ArrayList<>());
    
    // LiveData for UI state
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> currentArtistId = new MutableLiveData<>();
    
    // Endpoints for actions
    private String shuffleEndpoint;
    private String radioEndpoint;
    
    /**
     * Loads artist data from InnertubeBridge API.
     *
     * @param browseId The artist browse ID (e.g., "UCxxxxxx")
     */
    public void loadArtist(@NonNull String browseId) {
        if (browseId.equals(currentArtistId.getValue())) {
            Log.d(TAG, "Artist already loaded: " + browseId);
            return;
        }
        
        currentArtistId.setValue(browseId);
        isLoading.setValue(true);
        errorMessage.setValue(null);
        
        // Clear previous data
        clearArtistData();
        
        InnertubeBridge.getArtistAsync(browseId, new ArtistCallback() {
            @Override
            public void onSuccess(@NonNull ArtistPageResult result) {
                Log.d(TAG, "Artist data loaded successfully");
                
                artist.setValue(result.getArtist());
                description.setValue(result.getDescription());
                thumbnails.setValue(result.getThumbnails());
                
                songs.setValue(result.getSongs());
                albums.setValue(result.getAlbums());
                singles.setValue(result.getSingles());
                videos.setValue(result.getVideos());
                
                shuffleEndpoint = result.getShuffleEndpoint();
                radioEndpoint = result.getRadioEndpoint();
                
                isLoading.setValue(false);
            }
            
            @Override
            public void onError(@NonNull BridgeException exception) {
                Log.e(TAG, "Failed to load artist data", exception);
                
                String message = "Failed to load artist information";
                if (exception.getMessage() != null) {
                    message = exception.getMessage();
                }
                
                errorMessage.setValue(message);
                isLoading.setValue(false);
            }
        });
    }
    
    /**
     * Retries loading the current artist after an error.
     */
    public void retry() {
        String artistId = currentArtistId.getValue();
        if (artistId != null) {
            currentArtistId.setValue(null); // Reset to force reload
            loadArtist(artistId);
        }
    }
    
    /**
     * Clears all artist data.
     */
    private void clearArtistData() {
        artist.setValue(null);
        description.setValue(null);
        thumbnails.setValue(new ArrayList<>());
        songs.setValue(new ArrayList<>());
        albums.setValue(new ArrayList<>());
        singles.setValue(new ArrayList<>());
        videos.setValue(new ArrayList<>());
        shuffleEndpoint = null;
        radioEndpoint = null;
    }
    
    // Getters for LiveData
    
    @NonNull
    public LiveData<YTItemResult> getArtist() {
        return artist;
    }
    
    @NonNull
    public LiveData<String> getDescription() {
        return description;
    }
    
    @NonNull
    public LiveData<List<String>> getThumbnails() {
        return thumbnails;
    }
    
    @NonNull
    public LiveData<List<YTItemResult>> getSongs() {
        return songs;
    }
    
    @NonNull
    public LiveData<List<YTItemResult>> getAlbums() {
        return albums;
    }
    
    @NonNull
    public LiveData<List<YTItemResult>> getSingles() {
        return singles;
    }
    
    @NonNull
    public LiveData<List<YTItemResult>> getVideos() {
        return videos;
    }
    
    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    @Nullable
    public String getShuffleEndpoint() {
        return shuffleEndpoint;
    }
    
    @Nullable
    public String getRadioEndpoint() {
        return radioEndpoint;
    }
}
