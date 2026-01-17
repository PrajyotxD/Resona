package music.resona.playback;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

/**
 * Network utility for checking connectivity status.
 */
public class NetworkUtil {
    
    /**
     * Check if device is connected to network.
     */
    public static boolean isConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }
    
    /**
     * Check if device is connected to WiFi.
     */
    public static boolean isWiFiConnected(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && 
                   activeNetwork.getType() == ConnectivityManager.TYPE_WIFI && 
                   activeNetwork.isConnected();
        }
        return false;
    }
    
    /**
     * Check if device is on metered connection.
     */
    public static boolean isMeteredConnection(@NonNull Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            return cm.isActiveNetworkMetered();
        }
        return true; // Assume metered if unknown
    }
}